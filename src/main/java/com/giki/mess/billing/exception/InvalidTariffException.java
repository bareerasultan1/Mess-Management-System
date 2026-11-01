package com.giki.mess.billing.exception;

/**
 * Thrown when the tariff rate provided for bill calculation is invalid.
 *
 * Examples:
 *   - Daily meal rate is zero or negative
 *   - An unknown student category is passed
 *   - A tariff month/year combination does not exist
 */
public class InvalidTariffException extends BillingException {

    public InvalidTariffException(String message) {
        super("TARIFF_INVALID", message);
    }
}
