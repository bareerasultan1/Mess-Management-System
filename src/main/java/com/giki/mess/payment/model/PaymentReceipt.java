package com.giki.mess.payment.model;

import com.giki.mess.common.Money;
import java.time.Instant;

/** Proof that a callback was verified and accepted. */
public record PaymentReceipt(String transactionId, Money amount, String gatewayReference,
                             Instant verifiedAt) {
}
