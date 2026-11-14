package com.giki.mess.payment.service;

import com.giki.mess.common.Money;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.PaymentTransaction;

/** Public contract of the Online Payment module (FR-003). */
public interface PaymentService {

    /**
     * Starts a payment for a bill through the chosen gateway.
     *
     * @throws com.giki.mess.billing.exception.BillNotFoundException unknown bill
     * @throws com.giki.mess.payment.exception.DuplicateTransactionException key already used
     * @throws com.giki.mess.payment.exception.GatewayTimeoutException gateway down (ledger untouched)
     */
    PaymentResponse initiatePayment(String studentId, String billId, Money amount,
                                    PaymentChannel channel, String idempotencyKey);

    /**
     * Handles the gateway callback: verifies it, then credits the ledger and updates the bill
     * exactly once.
     *
     * @throws com.giki.mess.payment.exception.InvalidCallbackException forged, expired or mismatched
     * @throws com.giki.mess.payment.exception.DuplicateTransactionException already settled
     */
    PaymentTransaction settlePayment(PaymentCallback callback);
}
