package com.giki.mess.payment.gateway;

import com.giki.mess.common.Money;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentReceipt;
import com.giki.mess.payment.model.PaymentRequest;
import com.giki.mess.payment.model.PaymentResponse;
import java.time.Clock;
import java.time.Instant;

/** Shared behaviour of the simulated gateways (skeleton: behaviour added test-first). */
public abstract class AbstractSignedProcessor implements IPaymentProcessor {

    private final PaymentChannel channel;

    protected AbstractSignedProcessor(PaymentChannel channel, String secret, Clock clock) {
        this.channel = channel;
    }

    public String sign(String transactionId, String studentId, String billId, Money amount,
                       CallbackStatus status, String gatewayReference, Instant issuedAt) {
        return "";
    }

    public void setSimulatedOutage(boolean outage) {
    }

    @Override
    public PaymentResponse initiatePayment(PaymentRequest request) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public boolean verifyCallback(PaymentCallback callback) {
        return false;
    }

    @Override
    public PaymentReceipt reconcile(PaymentCallback callback) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public PaymentChannel getChannelType() {
        return channel;
    }
}
