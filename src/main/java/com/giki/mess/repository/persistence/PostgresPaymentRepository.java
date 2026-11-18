package com.giki.mess.repository.persistence;

import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.DuplicateTransactionException;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.payment.model.TransactionStatus;
import com.giki.mess.repository.IPaymentRepository;
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
import org.postgresql.util.PSQLException;

/**
 * PostgreSQL storage of payment transactions.
 *
 * DURABLE IDEMPOTENCY: the idempotency_key column has a UNIQUE constraint
 * (uq_payment_idempotency). Even if the in-memory IdempotencyManager is empty after a restart,
 * the database refuses a second transaction with the same key, which this class reports as a
 * DuplicateTransactionException.
 */
public class PostgresPaymentRepository implements IPaymentRepository {

    private static final String UPSERT = """
        INSERT INTO payment_transactions (transaction_id, student_id, bill_id, amount_paisa, channel,
                                          idempotency_key, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (transaction_id) DO UPDATE SET status = EXCLUDED.status
        """;

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String IDEMPOTENCY_CONSTRAINT = "uq_payment_idempotency";

    private final PostgresDatabase db;
    private final ConcurrentMap<String, PaymentTransaction> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> writeLocks = new ConcurrentHashMap<>();

    public PostgresPaymentRepository(PostgresDatabase db) {
        this.db = db;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction tx) {
        Object lock = writeLocks.computeIfAbsent(tx.getTransactionId(), k -> new Object());
        synchronized (lock) {
            TransactionStatus status = tx.getStatus();
            try {
                db.inTransaction(c -> {
                    try (PreparedStatement ps = c.prepareStatement(UPSERT)) {
                        ps.setString(1, tx.getTransactionId());
                        ps.setString(2, tx.getStudentId());
                        ps.setString(3, tx.getBillId());
                        ps.setLong(4, tx.getAmount().paisa());
                        ps.setString(5, tx.getChannel().name());
                        ps.setString(6, tx.getIdempotencyKey());
                        ps.setString(7, status.name());
                        ps.setObject(8, tx.getCreatedAt());
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                if (isIdempotencyViolation(e)) {
                    throw new DuplicateTransactionException(tx.getIdempotencyKey());
                }
                throw new PersistenceException("Could not save payment " + tx.getTransactionId(), e);
            }
            cache.put(tx.getTransactionId(), tx);
        }
        return tx;
    }

    private static boolean isIdempotencyViolation(SQLException e) {
        return e instanceof PSQLException p
            && UNIQUE_VIOLATION.equals(p.getSQLState())
            && p.getServerErrorMessage() != null
            && IDEMPOTENCY_CONSTRAINT.equals(p.getServerErrorMessage().getConstraint());
    }

    @Override
    public Optional<PaymentTransaction> findById(String transactionId) {
        PaymentTransaction cached = cache.get(transactionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return query("SELECT * FROM payment_transactions WHERE transaction_id = ?", transactionId)
            .stream().findFirst();
    }

    @Override
    public Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey) {
        return query("SELECT * FROM payment_transactions WHERE idempotency_key = ?", idempotencyKey)
            .stream().findFirst();
    }

    @Override
    public List<PaymentTransaction> findByBillId(String billId) {
        return query("SELECT * FROM payment_transactions WHERE bill_id = ? ORDER BY created_at, transaction_id", billId);
    }

    @Override
    public List<PaymentTransaction> findAll() {
        return query("SELECT * FROM payment_transactions ORDER BY created_at, transaction_id");
    }

    private List<PaymentTransaction> query(String sql, String... params) {
        try {
            return db.read(c -> {
                List<PaymentTransaction> result = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (int i = 0; i < params.length; i++) {
                        ps.setString(i + 1, params[i]);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String id = rs.getString("transaction_id");
                            PaymentTransaction loaded = PaymentTransaction.restore(id,
                                rs.getString("student_id"), rs.getString("bill_id"),
                                Money.ofPaisa(rs.getLong("amount_paisa")),
                                PaymentChannel.valueOf(rs.getString("channel")),
                                rs.getString("idempotency_key"),
                                rs.getObject("created_at", LocalDateTime.class),
                                TransactionStatus.valueOf(rs.getString("status")));
                            PaymentTransaction existing = cache.putIfAbsent(id, loaded);
                            result.add(existing != null ? existing : loaded);
                        }
                    }
                }
                return List.copyOf(result);
            });
        } catch (SQLException e) {
            throw new PersistenceException("Could not query payments", e);
        }
    }

    @Override
    public void deleteById(String transactionId) {
        Object lock = writeLocks.computeIfAbsent(transactionId, k -> new Object());
        synchronized (lock) {
            try {
                db.inTransaction(c -> {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM payment_transactions WHERE transaction_id = ?")) {
                        ps.setString(1, transactionId);
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (SQLException e) {
                throw new PersistenceException("Could not delete payment " + transactionId, e);
            }
            cache.remove(transactionId);
        }
    }
}
