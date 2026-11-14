package com.giki.mess.payment.model;

import com.giki.mess.common.Money;

/** What the service asks a gateway to do: collect the amount for one bill. */
public record PaymentRequest(
        String transactionId,
        String studentId,
        String billId,
        Money amount,
        PaymentChannel channel,
        String idempotencyKey) {

    public PaymentRequest {
        if (transactionId == null || transactionId.isBlank()
                || studentId == null || studentId.isBlank()
                || billId == null || billId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("PaymentRequest ids and idempotencyKey cannot be blank");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("PaymentRequest amount must be positive");
        }
        if (channel == null) {
            throw new IllegalArgumentException("PaymentRequest channel cannot be null");
        }
    }
}
