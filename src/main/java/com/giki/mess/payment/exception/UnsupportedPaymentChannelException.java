package com.giki.mess.payment.exception;

/** Thrown when no processor is registered for the requested payment channel. */
public class UnsupportedPaymentChannelException extends PaymentException {

    public UnsupportedPaymentChannelException(String message) {
        super("CHANNEL_UNSUPPORTED", message);
    }
}
