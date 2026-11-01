package com.giki.mess.repository;

import com.giki.mess.payment.model.PaymentTransaction;
import java.util.Optional;

/**
 * PAYMENT REPOSITORY INTERFACE
 *
 * Manages persistence of payment transaction records.
 *
 * The most important method here is findByIdempotencyKey() — this is what
 * the IdempotencyManager uses on Day 4 to detect duplicate payment callbacks.
 */
public interface IPaymentRepository extends IRepository<PaymentTransaction, String> {

    /**
     * Looks up a transaction by its idempotency key.
     *
     * WHY THIS IS CRITICAL:
     * When JazzCash sends a duplicate callback (same payment notified twice),
     * we call this method.  If it returns a non-empty Optional, we know we
     * already processed this payment and we return a "duplicate" response
     * WITHOUT touching the student's ledger or bill again.
     *
     * @param idempotencyKey The unique key that identifies a specific payment attempt
     * @return Optional<PaymentTransaction> — present if already processed
     */
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Returns all transactions for a specific bill.
     * Used to show a student their payment attempts for a given bill
     * (including failed attempts, so they know what happened).
     *
     * @param billId The bill ID
     * @return List of all payment attempts for that bill
     */
    java.util.List<PaymentTransaction> findByBillId(String billId);
}
