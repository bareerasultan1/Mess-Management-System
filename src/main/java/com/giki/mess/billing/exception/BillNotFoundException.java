package com.giki.mess.billing.exception;

/** Thrown when an invoice or payment refers to a bill id that does not exist. */
public class BillNotFoundException extends BillingException {

    public BillNotFoundException(String billId) {
        super("BILL_NOT_FOUND", "No bill found with id: " + billId);
    }
}
