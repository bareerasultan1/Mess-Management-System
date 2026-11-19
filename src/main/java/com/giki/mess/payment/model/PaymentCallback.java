package com.giki.mess.payment.model;

import com.giki.mess.common.Money;
import java.time.Instant;

/** The gateway webhook message reporting the outcome of one payment. */
public record PaymentCallback(
        String transactionId,
        String studentId,
        String billId,
        Money amount,
        CallbackStatus status,
        String gatewayReference,
        Instant issuedAt,
        String signature) {

    /**
     * Rejects a callback with missing fields.
     */
    public PaymentCallback {
        if (transactionId == null || studentId == null || billId == null || amount == null
                || status == null || gatewayReference == null || issuedAt == null
                || signature == null) {
            throw new IllegalArgumentException("PaymentCallback fields cannot be null");
        }
    }
}
