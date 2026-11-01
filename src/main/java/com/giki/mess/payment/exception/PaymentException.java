package com.giki.mess.payment.exception;

/**
 * BASE EXCEPTION — Payment Module
 *
 * WHY SEPARATE FROM BillingException?
 * ─────────────────────────────────────────────
 * Payment failures are a different category of problem from billing
 * calculation errors.  When a JazzCash callback arrives and the signature
 * is invalid, that is a PAYMENT security issue — not a billing rule violation.
 *
 * By separating them, future developers (or your team members) can
 * catch ONLY payment failures without accidentally swallowing billing errors.
 *
 * HIERARCHY:
 *   PaymentException   (this class — base for all payment errors)
 *   ├── DuplicateTransactionException  (same payment submitted twice)
 *   ├── GatewayTimeoutException        (JazzCash/EasyPaisa timed out)
 *   └── InvalidCallbackException       (forged or tampered webhook)
 */
public class PaymentException extends RuntimeException {

    private final String errorCode;

    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaymentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "PaymentException[" + errorCode + "]: " + getMessage();
    }
}
