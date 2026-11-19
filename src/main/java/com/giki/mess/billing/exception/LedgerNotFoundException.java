package com.giki.mess.billing.exception;

/**
 * Thrown when a StudentLedger account cannot be found for the given student ID.
 *
 * <p>Example:
 *   - A billing calculation is requested for student "2023999" who does not
 *     have a ledger account registered in the system.
 */
public class LedgerNotFoundException extends BillingException {

    private final String studentId;

    /**
     * Creates the error for a student that has no ledger.
     */
    public LedgerNotFoundException(String studentId) {
        super("LEDGER_NOT_FOUND",
              "No ledger account found for student ID: " + studentId);
        this.studentId = studentId;
    }

    public String getStudentId() {
        return studentId;
    }
}
