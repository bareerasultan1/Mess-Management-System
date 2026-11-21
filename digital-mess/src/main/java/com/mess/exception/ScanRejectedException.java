package com.mess.exception;

/** A scan was understood but refused by a business rule. */
public class ScanRejectedException extends MessException {

    public enum Reason {
        UNKNOWN_CREDENTIAL,
        NOT_A_STUDENT,
        ACCOUNT_INACTIVE,
        DUPLICATE_SCAN,
        OUTSIDE_MEAL_WINDOW,
        ALREADY_ENTERED_FOR_MEAL
    }

    private final Reason reason;

    public ScanRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
