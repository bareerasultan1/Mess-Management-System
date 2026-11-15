package com.giki.mess.repository.persistence;

import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.repository.IPaymentRepository;
import java.util.List;
import java.util.Optional;

/** Skeleton: behaviour is added test-first. */
public class SqlitePaymentRepository implements IPaymentRepository {

    public SqlitePaymentRepository(SqliteDatabase db) {
    }

    @Override
    public PaymentTransaction save(PaymentTransaction entity) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public Optional<PaymentTransaction> findById(String id) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public List<PaymentTransaction> findAll() {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public void deleteById(String id) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public List<PaymentTransaction> findByBillId(String billId) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
