package com.giki.mess.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.GatewayTimeoutException;
import com.giki.mess.payment.exception.InvalidCallbackException;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentReceipt;
import com.giki.mess.payment.model.PaymentRequest;
import com.giki.mess.payment.model.PaymentResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Callback security and behaviour of the three simulated gateways (PY-01, QA-05).
 * Equivalence classes: valid signature / forged signature / expired callback.
 */
@DisplayName("Payment processors (JazzCash, EasyPaisa, BankTransfer)")
class PaymentProcessorsTest {

    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    List<AbstractSignedProcessor> all() {
        return List.of(
            new JazzCashProcessor("jazz-secret", CLOCK),
            new EasyPaisaProcessor("easy-secret", CLOCK),
            new BankTransferProcessor("bank-secret", CLOCK));
    }

    PaymentCallback signed(AbstractSignedProcessor p, Money amount, CallbackStatus status,
                           Instant issuedAt, String reference) {
        String sig = p.sign("TX-1", "2023294", "BILL-1", amount, status, reference, issuedAt);
        return new PaymentCallback("TX-1", "2023294", "BILL-1", amount, status, reference,
            issuedAt, sig);
    }

    @Test
    @DisplayName("each processor reports its own channel")
    void channels() {
        List<AbstractSignedProcessor> ps = all();
        assertThat(ps.get(0).getChannelType()).isEqualTo(PaymentChannel.JAZZCASH);
        assertThat(ps.get(1).getChannelType()).isEqualTo(PaymentChannel.EASYPAISA);
        assertThat(ps.get(2).getChannelType()).isEqualTo(PaymentChannel.BANK_TRANSFER);
    }

    @Test
    @DisplayName("initiatePayment acknowledges with a gateway reference for the same transaction")
    void initiate_acknowledges() {
        for (AbstractSignedProcessor p : all()) {
            PaymentRequest req = new PaymentRequest("TX-1", "2023294", "BILL-1",
                Money.pkr(2_500), p.getChannelType(), "KEY-1");

            PaymentResponse res = p.initiatePayment(req);

            assertThat(res.transactionId()).isEqualTo("TX-1");
            assertThat(res.channel()).isEqualTo(p.getChannelType());
            assertThat(res.gatewayReference()).isNotBlank();
        }
    }

    @Test
    @DisplayName("a correctly signed, fresh callback is accepted")
    void validCallback_accepted() {
        for (AbstractSignedProcessor p : all()) {
            PaymentCallback cb = signed(p, Money.pkr(2_500), CallbackStatus.SUCCESS, NOW, "REF123456");

            assertThat(p.verifyCallback(cb)).isTrue();
            PaymentReceipt receipt = p.reconcile(cb);
            assertThat(receipt.transactionId()).isEqualTo("TX-1");
            assertThat(receipt.amount()).isEqualTo(Money.pkr(2_500));
        }
    }

    @Test
    @DisplayName("a forged signature is rejected")
    void forgedSignature_rejected() {
        for (AbstractSignedProcessor p : all()) {
            PaymentCallback good = signed(p, Money.pkr(2_500), CallbackStatus.SUCCESS, NOW, "REF123456");
            PaymentCallback forged = new PaymentCallback(good.transactionId(), good.studentId(),
                good.billId(), good.amount(), good.status(), good.gatewayReference(),
                good.issuedAt(), "deadbeef");

            assertThat(p.verifyCallback(forged)).isFalse();
            assertThatExceptionOfType(InvalidCallbackException.class)
                .isThrownBy(() -> p.reconcile(forged));
        }
    }

    @Test
    @DisplayName("a callback signed with another gateway's secret is rejected")
    void wrongSecret_rejected() {
        JazzCashProcessor jazz = new JazzCashProcessor("jazz-secret", CLOCK);
        JazzCashProcessor attacker = new JazzCashProcessor("guessed-secret", CLOCK);
        PaymentCallback cb = signed(attacker, Money.pkr(2_500), CallbackStatus.SUCCESS, NOW, "REF123456");

        assertThat(jazz.verifyCallback(cb)).isFalse();
    }

    @Test
    @DisplayName("an amount altered after signing is rejected (tamper detection)")
    void alteredAmount_rejected() {
        for (AbstractSignedProcessor p : all()) {
            PaymentCallback good = signed(p, Money.pkr(2_500), CallbackStatus.SUCCESS, NOW, "REF123456");
            PaymentCallback tampered = new PaymentCallback(good.transactionId(), good.studentId(),
                good.billId(), Money.pkr(25_000), good.status(), good.gatewayReference(),
                good.issuedAt(), good.signature());

            assertThat(p.verifyCallback(tampered)).isFalse();
        }
    }

    @Test
    @DisplayName("callback exactly 15 minutes old is accepted, 15 min 1 s old is expired [BVA]")
    void expiry_boundary() {
        for (AbstractSignedProcessor p : all()) {
            Instant atLimit = NOW.minus(Duration.ofMinutes(15));
            Instant tooOld = atLimit.minusSeconds(1);

            assertThat(p.verifyCallback(signed(p, Money.pkr(10), CallbackStatus.SUCCESS, atLimit, "REF123456")))
                .isTrue();
            assertThat(p.verifyCallback(signed(p, Money.pkr(10), CallbackStatus.SUCCESS, tooOld, "REF123456")))
                .isFalse();
        }
    }

    @Test
    @DisplayName("a callback dated far in the future is rejected")
    void futureCallback_rejected() {
        AbstractSignedProcessor p = all().get(0);
        PaymentCallback cb = signed(p, Money.pkr(10), CallbackStatus.SUCCESS,
            NOW.plus(Duration.ofHours(1)), "REF123456");

        assertThat(p.verifyCallback(cb)).isFalse();
    }

    @Test
    @DisplayName("bank transfer additionally requires a bank reference number")
    void bankTransfer_requiresReference() {
        BankTransferProcessor bank = new BankTransferProcessor("bank-secret", CLOCK);
        PaymentCallback noRef = signed(bank, Money.pkr(500), CallbackStatus.SUCCESS, NOW, "");

        assertThat(bank.verifyCallback(noRef)).isFalse();
    }

    @Test
    @DisplayName("a simulated gateway outage raises GatewayTimeoutException")
    void outage_raisesTimeout() {
        AbstractSignedProcessor p = all().get(0);
        p.setSimulatedOutage(true);
        PaymentRequest req = new PaymentRequest("TX-1", "2023294", "BILL-1",
            Money.pkr(100), PaymentChannel.JAZZCASH, "KEY-1");

        assertThatExceptionOfType(GatewayTimeoutException.class)
            .isThrownBy(() -> p.initiatePayment(req));
    }
}
