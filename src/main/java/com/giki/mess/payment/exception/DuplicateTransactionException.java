package com.giki.mess.payment.exception;

/**
 * Thrown when the same payment transaction is submitted more than once.
 *
 * <p>WHY THIS MATTERS (Risk R-02 in SQAP):
 *   External payment gateways like JazzCash sometimes resend payment
 *   confirmation webhooks multiple times when the network is unstable.
 *   Without this exception, the system could credit the student's account
 *   twice for a single payment — a serious financial bug.
 *
 * <p>The IdempotencyManager catches this and throws this exception
 *   so the duplicate request is safely rejected.
 */
public class DuplicateTransactionException extends PaymentException {

    private final String idempotencyKey;

    /**
     * Creates the error for a payment key that was already used.
     */
    public DuplicateTransactionException(String idempotencyKey) {
        super("DUPLICATE_TRANSACTION",
              "Payment already processed for key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
