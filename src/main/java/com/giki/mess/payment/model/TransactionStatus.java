package com.giki.mess.payment.model;

/**
 * ENUM: TransactionStatus
 *
 * Tracks the lifecycle of a single payment attempt.
 *
 * Lifecycle:
 *   INITIATED  →  SUCCESS   (payment confirmed by gateway callback)
 *   INITIATED  →  FAILED    (gateway rejected or student cancelled)
 *   SUCCESS    →  RECONCILED (ledger updated, bill marked as paid)
 *   Any state  →  DUPLICATE  (idempotency check: this transaction was already processed)
 */
public enum TransactionStatus {

    /** Payment has been sent to the gateway and is awaiting confirmation. */
    INITIATED,

    /** Gateway confirmed the payment was received. */
    SUCCESS,

    /** Gateway rejected the payment (insufficient funds, expired session, etc.) */
    FAILED,

    /** SUCCESS + ledger has been updated + bill has been marked PAID. */
    RECONCILED,

    /** This transaction was already processed — duplicate callback detected. */
    DUPLICATE
}
