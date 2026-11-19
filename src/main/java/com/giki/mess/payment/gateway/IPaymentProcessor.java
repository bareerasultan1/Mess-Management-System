package com.giki.mess.payment.gateway;

import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentReceipt;
import com.giki.mess.payment.model.PaymentRequest;
import com.giki.mess.payment.model.PaymentResponse;

/**
 * Common contract of every payment gateway (Open/Closed: a new gateway is a new class).
 * The implementations in this project are SIMULATED sandboxes, not live bank connections.
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname") // I-prefix convention approved in the M1 design
public interface IPaymentProcessor {

    PaymentResponse initiatePayment(PaymentRequest request);

    /** True only if the callback is authentic (signature) and fresh (not expired). */
    boolean verifyCallback(PaymentCallback callback);

    /** Verifies the callback and returns a receipt; throws InvalidCallbackException otherwise. */
    PaymentReceipt reconcile(PaymentCallback callback);

    PaymentChannel getChannelType();
}
