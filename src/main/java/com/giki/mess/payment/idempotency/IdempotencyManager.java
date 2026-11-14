package com.giki.mess.payment.idempotency;

import java.util.Optional;

/** Skeleton: behaviour is added test-first. */
public class IdempotencyManager {

    public boolean tryAcquire(String idempotencyKey, TransactionRecord record) {
        return false;
    }

    public Optional<TransactionRecord> find(String idempotencyKey) {
        return Optional.empty();
    }

    public void release(String idempotencyKey, TransactionRecord record) {
    }
}
