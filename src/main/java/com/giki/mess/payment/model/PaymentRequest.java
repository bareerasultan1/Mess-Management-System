package com.giki.mess.payment.model;

import com.giki.mess.common.Guard;
import com.giki.mess.common.Money;

/** What the service asks a gateway to do: collect the amount for one bill. */
public record PaymentRequest(
        String transactionId,
        String studentId,
        String billId,
        Money amount,
        PaymentChannel channel,
        String idempotencyKey) {

    /**
     * Rejects a request with blank ids, a non-positive amount or no channel.
     */
    public PaymentRequest {
        String blankMessage = "PaymentRequest ids and idempotencyKey cannot be blank";
        Guard.notBlank(transactionId, blankMessage);
        Guard.notBlank(studentId, blankMessage);
        Guard.notBlank(billId, blankMessage);
        Guard.notBlank(idempotencyKey, blankMessage);
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("PaymentRequest amount must be positive");
        }
        Guard.notNull(channel, "PaymentRequest channel cannot be null");
    }
}
