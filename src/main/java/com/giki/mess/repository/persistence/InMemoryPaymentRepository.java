package com.giki.mess.repository.persistence;

import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.repository.IPaymentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory payment transaction storage for tests (thread-safe map keyed by transactionId). */
public class InMemoryPaymentRepository implements IPaymentRepository {

    private final ConcurrentMap<String, PaymentTransaction> store = new ConcurrentHashMap<>();

    @Override
    public synchronized PaymentTransaction save(PaymentTransaction transaction) {
        boolean keyUsedByOther = store.values().stream().anyMatch(
            t -> t.getIdempotencyKey().equals(transaction.getIdempotencyKey())
                && !t.getTransactionId().equals(transaction.getTransactionId()));
        if (keyUsedByOther) {   // same rule as the UNIQUE column in the SQLite schema
            throw new com.giki.mess.payment.exception.DuplicateTransactionException(
                transaction.getIdempotencyKey());
        }
        store.put(transaction.getTransactionId(), transaction);
        return transaction;
    }

    @Override
    public Optional<PaymentTransaction> findById(String transactionId) {
        return Optional.ofNullable(store.get(transactionId));
    }

    @Override
    public List<PaymentTransaction> findAll() {
        return List.copyOf(new ArrayList<>(store.values()));
    }

    @Override
    public void deleteById(String transactionId) {
        store.remove(transactionId);
    }

    @Override
    public Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey) {
        return store.values().stream()
            .filter(t -> t.getIdempotencyKey().equals(idempotencyKey))
            .findFirst();
    }

    @Override
    public List<PaymentTransaction> findByBillId(String billId) {
        return store.values().stream().filter(t -> t.getBillId().equals(billId)).toList();
    }
}
