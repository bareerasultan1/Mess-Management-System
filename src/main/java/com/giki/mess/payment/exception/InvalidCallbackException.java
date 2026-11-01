package com.giki.mess.payment.exception;

/**
 * Thrown when a payment gateway callback fails signature verification.
 *
 * WHY THIS MATTERS (QA-05 in SQAP):
 *   Any server on the internet can POST a fake "payment success" message
 *   to your system if you don't verify that the request actually came from
 *   JazzCash or EasyPaisa.  These gateways sign their callbacks with an
 *   HMAC key that only they and we know.
 *
 *   If verification fails, this exception is thrown and the callback
 *   is REJECTED before any ledger update happens.
 */
public class InvalidCallbackException extends PaymentException {

    public InvalidCallbackException(String message) {
        super("CALLBACK_INVALID", message);
    }
}
