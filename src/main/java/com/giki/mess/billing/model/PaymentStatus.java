package com.giki.mess.billing.model;

/**
 * ENUM: PaymentStatus
 *
 * Represents the lifecycle state of a student's Bill.
 * Using an enum (instead of a String like "unpaid", "paid") means:
 *   1. The compiler catches typos at build time — "PIAD" would be a compile error
 *   2. You can switch() on it safely
 *   3. It self-documents the valid states in the system
 *
 * Lifecycle:
 *   UNPAID  →  PARTIALLY_PAID  →  PAID
 *       └──────────────────────→  OVERDUE (if payment not received by due date)
 */
public enum PaymentStatus {

    /** Bill has been generated but no payment has been made yet. */
    UNPAID,

    /** Student made a partial payment — some amount is still outstanding. */
    PARTIALLY_PAID,

    /** Full amount has been received and reconciled. */
    PAID,

    /** Payment due date has passed and bill is still not fully paid. */
    OVERDUE
}
