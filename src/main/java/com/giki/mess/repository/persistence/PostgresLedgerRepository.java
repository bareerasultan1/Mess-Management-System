package com.giki.mess.repository.persistence;

import com.giki.mess.billing.model.LedgerSnapshot;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.repository.ILedgerRepository;
import com.giki.mess.repository.PersistenceException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * PostgreSQL storage of student ledgers.
 *
 * IDENTITY MAP: the repository hands out ONE StudentLedger object per student inside the
 * process. That keeps the object's ReentrantLock meaningful: two threads changing the same
 * student share the same lock instead of working on two private copies (which would lose
 * updates). save() writes the current state through to the database.
 *
 * WRITE ORDER: the snapshot is taken INSIDE a per-student lock together with the SQL write, so
 * a slower thread can never overwrite a newer balance with an older one.
 */
public class PostgresLedgerRepository implements ILedgerRepository {

    private static final String UPSERT = """
        INSERT INTO student_ledgers (student_id, category, balance_paisa, unbilled_meals, status, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (student_id) DO UPDATE SET
            balance_paisa = EXCLUDED.balance_paisa,
            unbilled_meals = EXCLUDED.unbilled_meals,
            status = EXCLUDED.status,
            updated_at = EXCLUDED.updated_at
        """;

    private final PostgresDatabase db;
    private final ConcurrentMap<String, StudentLedger> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> writeLocks = new ConcurrentHashMap<>();

    public PostgresLedgerRepository(PostgresDatabase db) {
        this.db = db;
    }

    @Override
    public StudentLedger save(StudentLedger ledger) {
        Object lock = writeLocks.computeIfAbsent(ledger.getStudentId(), k -> new Object());
        synchronized (lock) {
            LedgerSnapshot s = ledger.snapshot();
            try {
                db.inTransaction(c -> {
                    try (PreparedStatement ps = c.prepareStatement(UPSERT)) {
                        ps.setString(1, ledger.getStudentId());
                        ps.setString(2, ledger.getCategory().name());
                        ps.setLong(3, s.balance().paisa());
                        ps.setInt(4, s.unbilledMealCount());
                        ps.setString(5, s.status().name());
                        ps.setObject(6, LocalDateTime.now());
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                throw new PersistenceException("Could not save ledger", e);
            }
            cache.put(ledger.getStudentId(), ledger);
        }
        return ledger;
    }

    @Override
    public Optional<StudentLedger> findById(String studentId) {
        StudentLedger cached = cache.get(studentId);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            return db.read(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT category, balance_paisa, unbilled_meals, status FROM student_ledgers WHERE student_id = ?")) {
                    ps.setString(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.<StudentLedger>empty();
                        }
                        StudentLedger loaded = StudentLedger.restore(studentId,
                            StudentCategory.valueOf(rs.getString(1)), Money.ofPaisa(rs.getLong(2)),
                            rs.getInt(3), StudentLedger.AccountStatus.valueOf(rs.getString(4)));
                        StudentLedger existing = cache.putIfAbsent(studentId, loaded);
                        return Optional.of(existing != null ? existing : loaded);
                    }
                }
            });
        } catch (SQLException e) {
            throw new PersistenceException("Could not load ledger", e);
        }
    }

    @Override
    public Optional<StudentLedger> findByStudentId(String studentId) {
        return findById(studentId);
    }

    @Override
    public List<StudentLedger> findAll() {
        try {
            List<String> ids = db.read(c -> {
                List<String> found = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT student_id FROM student_ledgers ORDER BY student_id");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        found.add(rs.getString(1));
                    }
                }
                return found;
            });
            List<StudentLedger> result = new ArrayList<>();
            for (String id : ids) {
                findById(id).ifPresent(result::add);
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new PersistenceException("Could not list ledgers", e);
        }
    }

    @Override
    public void deleteById(String studentId) {
        Object lock = writeLocks.computeIfAbsent(studentId, k -> new Object());
        synchronized (lock) {
            try {
                db.inTransaction(c -> {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM student_ledgers WHERE student_id = ?")) {
                        ps.setString(1, studentId);
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                throw new PersistenceException("Could not delete ledger", e);
            }
            cache.remove(studentId);
        }
    }
}
