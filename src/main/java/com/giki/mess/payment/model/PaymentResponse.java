package com.giki.mess.payment.model;

/** Gateway acknowledgement that a payment was started; the result arrives later as a callback. */
public record PaymentResponse(String transactionId, PaymentChannel channel, String gatewayReference) {
}
