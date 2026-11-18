package com.giki.mess.payment.idempotency;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ATOMIC GATE against duplicate payment settlement (risk R-02, requirement QA-CONC-01).
 *
 * Gateways resend callbacks when the network is unreliable, and several copies can arrive at
 * the same instant on different threads. A "check the status, then update it" sequence is not
 * safe because two threads can both pass the check. putIfAbsent on a ConcurrentHashMap is a
 * single atomic operation: exactly one thread gets null back (the winner); every other thread
 * sees the winner's record and is turned away before it can touch the ledger.
 *
 * This is process-local. The PostgreSQL schema adds a UNIQUE constraint on idempotency_key as the
 * durable second line of defence across restarts.
 */
public class IdempotencyManager {

    private final ConcurrentMap<String, TransactionRecord> processedTokens = new ConcurrentHashMap<>();

    /** @return true only for the single caller that claimed this key first */
    public boolean tryAcquire(String idempotencyKey, TransactionRecord record) {
        requireKey(idempotencyKey);
        if (record == null) {
            throw new IllegalArgumentException("record cannot be null");
        }
        return processedTokens.putIfAbsent(idempotencyKey, record) == null;
    }

    public Optional<TransactionRecord> find(String idempotencyKey) {
        requireKey(idempotencyKey);
        return Optional.ofNullable(processedTokens.get(idempotencyKey));
    }

    /**
     * Gives the key back after an attempt failed BEFORE any money moved, so a retry is possible.
     * Only the record that won the key can release it (remove(key, value) is atomic as well).
     */
    public void release(String idempotencyKey, TransactionRecord record) {
        requireKey(idempotencyKey);
        processedTokens.remove(idempotencyKey, record);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be null or blank");
        }
    }
}
