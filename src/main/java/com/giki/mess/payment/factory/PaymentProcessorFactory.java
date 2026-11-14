package com.giki.mess.payment.factory;

import com.giki.mess.payment.exception.UnsupportedPaymentChannelException;
import com.giki.mess.payment.gateway.IPaymentProcessor;
import com.giki.mess.payment.model.PaymentChannel;
import java.util.Map;
import java.util.Objects;

/**
 * FACTORY METHOD pattern: getProcessor() decides which concrete gateway handles a channel.
 *
 * The payment service asks for a processor by channel and only ever sees the
 * IPaymentProcessor interface. Adding a gateway (Raast, SadaPay...) means writing one new
 * class and registering it here, so no existing billing or payment code is modified (OCP).
 * The rejected alternative was a switch statement inside PaymentService.
 */
public class PaymentProcessorFactory {

    private final Map<PaymentChannel, IPaymentProcessor> processors;

    public PaymentProcessorFactory(Map<PaymentChannel, IPaymentProcessor> processors) {
        this.processors = Map.copyOf(Objects.requireNonNull(processors, "processors"));
    }

    public IPaymentProcessor getProcessor(PaymentChannel channel) {
        if (channel == null) {
            throw new UnsupportedPaymentChannelException("Payment channel must not be null");
        }
        IPaymentProcessor processor = processors.get(channel);
        if (processor == null) {
            throw new UnsupportedPaymentChannelException("Channel not supported: " + channel);
        }
        return processor;
    }
}
