package com.giki.mess.payment.idempotency;

import java.time.Instant;

/** What the idempotency gate remembers about the request that won the key. */
public record TransactionRecord(String transactionId, Instant acquiredAt) {
}
