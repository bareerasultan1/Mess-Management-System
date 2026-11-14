package com.giki.mess.payment.factory;

import com.giki.mess.payment.gateway.IPaymentProcessor;
import com.giki.mess.payment.model.PaymentChannel;
import java.util.Map;

public class PaymentProcessorFactory {

    private final Map<PaymentChannel, IPaymentProcessor> processors;

    public PaymentProcessorFactory(Map<PaymentChannel, IPaymentProcessor> processors) {
        this.processors = processors;
    }

    public IPaymentProcessor getProcessor(PaymentChannel channel) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
