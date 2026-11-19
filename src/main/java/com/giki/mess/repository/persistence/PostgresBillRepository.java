package com.giki.mess.repository.persistence;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.common.Money;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.PersistenceException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * PostgreSQL storage of bills and their itemised lines.
 * A bill and its items are written in ONE transaction: either both are stored or neither.
 * Like the ledger repository it keeps one Bill object per bill id (identity map) so the
 * Bill's own synchronization protects concurrent payments.
 */
public class PostgresBillRepository implements IBillRepository {

    private static final String UPSERT = """
        INSERT INTO bills (bill_id, student_id, month, year, due_date, base_paisa, rebate_paisa,
                           fine_paisa, discount_paisa, net_paisa, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (bill_id) DO UPDATE SET
            due_date = EXCLUDED.due_date,
            base_paisa = EXCLUDED.base_paisa,
            rebate_paisa = EXCLUDED.rebate_paisa,
            fine_paisa = EXCLUDED.fine_paisa,
            discount_paisa = EXCLUDED.discount_paisa,
            net_paisa = EXCLUDED.net_paisa,
            status = EXCLUDED.status
        """;

    private final PostgresDatabase db;
    private final ConcurrentMap<String, Bill> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> writeLocks = new ConcurrentHashMap<>();

    public PostgresBillRepository(PostgresDatabase db) {
        this.db = db;
    }

    @Override
    public Bill save(Bill bill) {
        Object lock = writeLocks.computeIfAbsent(bill.getBillId(), k -> new Object());
        synchronized (lock) {
            Money net;
            PaymentStatus status;
            synchronized (bill) {                 // read the pair together (Bill locks on itself)
                net = bill.getNetPayable();
                status = bill.getPaymentStatus();
            }
            try {
                db.inTransaction(c -> {
                    try (PreparedStatement ps = c.prepareStatement(UPSERT)) {
                        ps.setString(1, bill.getBillId());
                        ps.setString(2, bill.getStudentId());
                        ps.setInt(3, bill.getMonth());
                        ps.setInt(4, bill.getYear());
                        ps.setObject(5, bill.getDueDate());
                        ps.setLong(6, bill.getBaseAmount().paisa());
                        ps.setLong(7, bill.getRebateAmount().paisa());
                        ps.setLong(8, bill.getFineAmount().paisa());
                        ps.setLong(9, bill.getDiscountAmount().paisa());
                        ps.setLong(10, net.paisa());
                        ps.setString(11, status.name());
                        ps.executeUpdate();
                    }
                    replaceItems(c, bill);
                    return null;
                });
            } catch (SQLException e) {
                throw new PersistenceException("Could not save bill " + bill.getBillId(), e);
            }
            cache.put(bill.getBillId(), bill);
        }
        return bill;
    }

    private void replaceItems(Connection c, Bill bill) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM bill_items WHERE bill_id = ?")) {
            del.setString(1, bill.getBillId());
            del.executeUpdate();
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO bill_items (item_id, bill_id, student_id, scan_time, meal_type, rate_paisa) "
                    + "VALUES (?,?,?,?,?,?)")) {
            for (BillItem item : bill.getItems()) {
                ins.setString(1, item.getItemId());
                ins.setString(2, bill.getBillId());
                ins.setString(3, item.getStudentId());
                ins.setObject(4, item.getScanTime());
                ins.setString(5, item.getMealType().name());
                ins.setLong(6, item.getRateApplied().paisa());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    @Override
    public Optional<Bill> findById(String billId) {
        Bill cached = cache.get(billId);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            return db.read(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT * FROM bills WHERE bill_id = ?")) {
                    ps.setString(1, billId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.<Bill>empty();
                        }
                        Bill loaded = Bill.restore(billId, rs.getString("student_id"),
                            rs.getInt("month"), rs.getInt("year"),
                            rs.getObject("due_date", LocalDate.class),
                            Money.ofPaisa(rs.getLong("base_paisa")),
                            Money.ofPaisa(rs.getLong("rebate_paisa")),
                            Money.ofPaisa(rs.getLong("fine_paisa")),
                            Money.ofPaisa(rs.getLong("discount_paisa")),
                            Money.ofPaisa(rs.getLong("net_paisa")),
                            PaymentStatus.valueOf(rs.getString("status")),
                            loadItems(c, billId));
                        Bill existing = cache.putIfAbsent(billId, loaded);
                        return Optional.of(existing != null ? existing : loaded);
                    }
                }
            });
        } catch (SQLException e) {
            throw new PersistenceException("Could not load bill " + billId, e);
        }
    }

    private List<BillItem> loadItems(Connection c, String billId) throws SQLException {
        List<BillItem> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT item_id, student_id, scan_time, meal_type, rate_paisa FROM bill_items "
                    + "WHERE bill_id = ? ORDER BY scan_time, item_id")) {
            ps.setString(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new BillItem(rs.getString(1), billId, rs.getString(2),
                        rs.getObject(3, LocalDateTime.class), MealType.valueOf(rs.getString(4)),
                        Money.ofPaisa(rs.getLong(5))));
                }
            }
        }
        return items;
    }

    /** Runs a query that returns bill ids, then loads each bill (parameters are never concatenated). */
    private List<Bill> findByIds(String sql, Object... params) {
        try {
            List<String> ids = db.read(c -> {
                List<String> found = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (int i = 0; i < params.length; i++) {
                        ps.setObject(i + 1, params[i]);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            found.add(rs.getString(1));
                        }
                    }
                }
                return found;
            });
            List<Bill> result = new ArrayList<>();
            for (String id : ids) {
                findById(id).ifPresent(result::add);
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new PersistenceException("Could not query bills", e);
        }
    }

    @Override
    public List<Bill> findAll() {
        return findByIds("SELECT bill_id FROM bills ORDER BY year, month, student_id");
    }

    @Override
    public Optional<Bill> findByStudentAndMonth(String studentId, int month, int year) {
        return findByIds("SELECT bill_id FROM bills WHERE student_id = ? AND month = ? AND year = ?",
            studentId, month, year).stream().findFirst();
    }

    @Override
    public List<Bill> findOverdueBills() {
        return findByIds("SELECT bill_id FROM bills WHERE status <> 'PAID' AND due_date < ? ORDER BY due_date, bill_id",
            LocalDate.now());
    }

    @Override
    public List<Bill> findByStudentId(String studentId) {
        return findByIds("SELECT bill_id FROM bills WHERE student_id = ? ORDER BY year DESC, month DESC",
            studentId);
    }

    @Override
    public void deleteById(String billId) {
        Object lock = writeLocks.computeIfAbsent(billId, k -> new Object());
        synchronized (lock) {
            try {
                db.inTransaction(c -> {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM bills WHERE bill_id = ?")) {
                        ps.setString(1, billId);
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                throw new PersistenceException("Could not delete bill " + billId, e);
            }
            cache.remove(billId);
        }
    }
}
