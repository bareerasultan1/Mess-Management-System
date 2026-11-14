package com.giki.mess.payment.exception;

/** Thrown when the external gateway does not answer (risk R-03). The ledger stays untouched. */
public class GatewayTimeoutException extends PaymentException {

    public GatewayTimeoutException(String channel) {
        super("GATEWAY_TIMEOUT", "Payment gateway did not respond: " + channel);
    }
}
