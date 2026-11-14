package com.giki.mess.payment.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import com.giki.mess.payment.exception.UnsupportedPaymentChannelException;
import com.giki.mess.payment.gateway.IPaymentProcessor;
import com.giki.mess.payment.model.PaymentChannel;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Factory Method pattern: PaymentProcessorFactory (FR-003 / PY-01). */
@DisplayName("PaymentProcessorFactory")
class PaymentProcessorFactoryTest {

    IPaymentProcessor jazz = mock(IPaymentProcessor.class);
    IPaymentProcessor easy = mock(IPaymentProcessor.class);
    IPaymentProcessor bank = mock(IPaymentProcessor.class);
    PaymentProcessorFactory factory;

    @BeforeEach
    void setUp() {
        Map<PaymentChannel, IPaymentProcessor> map = new EnumMap<>(PaymentChannel.class);
        map.put(PaymentChannel.JAZZCASH, jazz);
        map.put(PaymentChannel.EASYPAISA, easy);
        map.put(PaymentChannel.BANK_TRANSFER, bank);
        factory = new PaymentProcessorFactory(map);
    }

    @Test
    @DisplayName("returns the processor registered for each channel")
    void returnsCorrectProcessor() {
        assertThat(factory.getProcessor(PaymentChannel.JAZZCASH)).isSameAs(jazz);
        assertThat(factory.getProcessor(PaymentChannel.EASYPAISA)).isSameAs(easy);
        assertThat(factory.getProcessor(PaymentChannel.BANK_TRANSFER)).isSameAs(bank);
    }

    @Test
    @DisplayName("null channel is rejected")
    void nullChannel_rejected() {
        assertThatExceptionOfType(UnsupportedPaymentChannelException.class)
            .isThrownBy(() -> factory.getProcessor(null));
    }

    @Test
    @DisplayName("a channel with no registered processor is rejected")
    void unregisteredChannel_rejected() {
        PaymentProcessorFactory partial = new PaymentProcessorFactory(
            Map.of(PaymentChannel.JAZZCASH, jazz));

        assertThatExceptionOfType(UnsupportedPaymentChannelException.class)
            .isThrownBy(() -> partial.getProcessor(PaymentChannel.EASYPAISA))
            .withMessageContaining("EASYPAISA");
    }

    @Test
    @DisplayName("a new gateway can be added just by registering it (Open/Closed)")
    void newGateway_canBeRegistered() {
        IPaymentProcessor extra = mock(IPaymentProcessor.class);
        PaymentProcessorFactory custom = new PaymentProcessorFactory(
            Map.of(PaymentChannel.JAZZCASH, extra));

        assertThat(custom.getProcessor(PaymentChannel.JAZZCASH)).isSameAs(extra);
    }
}
