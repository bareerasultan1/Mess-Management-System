package com.giki.mess.billing.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.giki.mess.common.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bill.applyPayment (atomic settle step)")
class BillApplyPaymentTest {

    private Bill bill() {
        return new Bill("B1", "2023294", 11, 2026, LocalDate.of(2026, 12, 10),
            Money.pkr(1_000), Money.ZERO, Money.ZERO, Money.ZERO, List.of());
    }

    @Test
    @DisplayName("a payment covering the balance marks the bill PAID")
    void fullPayment_marksPaid() {
        Bill b = bill();
        b.applyPayment(Money.pkr(1_000));
        assertThat(b.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(b.getNetPayable()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("a smaller payment leaves PARTIALLY_PAID with the remainder")
    void partialPayment_keepsRemainder() {
        Bill b = bill();
        b.applyPayment(Money.pkr(300));
        assertThat(b.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(b.getNetPayable()).isEqualTo(Money.pkr(700));
    }

    @Test
    @DisplayName("zero, negative or null payments are rejected")
    void invalidPayment_rejected() {
        Bill b = bill();
        assertThatIllegalArgumentException().isThrownBy(() -> b.applyPayment(Money.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> b.applyPayment(Money.pkr(-1)));
        assertThatIllegalArgumentException().isThrownBy(() -> b.applyPayment(null));
    }
}
