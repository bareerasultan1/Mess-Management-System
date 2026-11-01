package com.giki.mess.billing.exception;

/**
 * BASE EXCEPTION — Billing Module
 *
 * WHY DO WE HAVE A CUSTOM EXCEPTION HIERARCHY?
 * ─────────────────────────────────────────────
 * Java has many generic exceptions (IllegalArgumentException, RuntimeException…).
 * But if we use those in billing code, a test or caller cannot tell WHY the error
 * happened — was it bad input?  A database failure?  A business rule violation?
 *
 * With our own hierarchy, we can:
 *   1. Catch ONLY billing errors:  catch (BillingException e)
 *   2. Write clearer error messages for the student-facing UI
 *   3. Log structured error types for the SQA audit trail (SE431)
 *
 * HIERARCHY:
 *   BillingException  (this class — base for all billing errors)
 *   ├── InvalidTariffException   (bad rate or tariff rule)
 *   ├── InsufficientFundsException (student owes more than ledger can handle)
 *   └── LedgerNotFoundException  (no account found for a student ID)
 *
 * All are RuntimeException so callers are NOT forced to catch them everywhere
 * (unchecked), but they CAN catch them when they want to handle them.
 */
public class BillingException extends RuntimeException {

    /** A short machine-readable code for logging (e.g. "TARIFF_INVALID") */
    private final String errorCode;

    public BillingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BillingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "BillingException[" + errorCode + "]: " + getMessage();
    }
}
