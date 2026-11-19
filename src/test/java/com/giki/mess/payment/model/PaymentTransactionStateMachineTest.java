package com.giki.mess.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.giki.mess.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DEF-02 (pre-inspection review): a payment transaction may only move along legal transitions. */
@DisplayName("DEF-02: PaymentTransaction state machine")
class PaymentTransactionStateMachineTest {

    private PaymentTransaction tx() {
        return new PaymentTransaction("TX-1", "2023294", "BILL-1", Money.pkr(100),
            PaymentChannel.JAZZCASH, "KEY-1");
    }

    @Test
    @DisplayName("legal path INITIATED -> SUCCESS -> RECONCILED works")
    void legalSuccessPath() {
        PaymentTransaction t = tx();
        t.markSuccess();
        t.markReconciled();
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
    }

    @Test
    @DisplayName("a transaction cannot be RECONCILED before the gateway confirmed it")
    void cannotReconcileWithoutSuccess() {
        assertThatIllegalStateException().isThrownBy(() -> tx().markReconciled());
    }

    @Test
    @DisplayName("a FAILED transaction cannot later become SUCCESS or RECONCILED")
    void failedIsFinal() {
        PaymentTransaction t = tx();
        t.markFailed();
        assertThatIllegalStateException().isThrownBy(t::markSuccess);
        assertThatIllegalStateException().isThrownBy(t::markReconciled);
    }

    @Test
    @DisplayName("a RECONCILED transaction cannot be failed or marked successful again")
    void reconciledIsFinal() {
        PaymentTransaction t = tx();
        t.markSuccess();
        t.markReconciled();
        assertThatIllegalStateException().isThrownBy(t::markFailed);
        assertThatIllegalStateException().isThrownBy(t::markSuccess);
    }

    @Test
    @DisplayName("a SUCCESS transaction cannot be turned into FAILED")
    void successCannotFail() {
        PaymentTransaction t = tx();
        t.markSuccess();
        assertThatIllegalStateException().isThrownBy(t::markFailed);
    }
}
