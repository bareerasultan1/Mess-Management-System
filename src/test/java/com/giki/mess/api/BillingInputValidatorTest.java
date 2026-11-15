package com.giki.mess.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.giki.mess.common.InvalidInputException;
import com.giki.mess.common.Money;
import com.giki.mess.payment.model.PaymentChannel;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Boundary validation (SE423 defensive programming, equivalence classes and boundaries). */
@DisplayName("BillingInputValidator")
class BillingInputValidatorTest {

    BillingInputValidator v = new BillingInputValidator();

    // ── student id: 7 digits starting with 20 ─────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"2023294", "2024001", "2020000"})
    @DisplayName("valid student ids are accepted")
    void studentId_valid(String id) {
        assertThat(v.studentId(id)).isEqualTo(id);
    }

    @Test
    @DisplayName("surrounding spaces are trimmed")
    void studentId_trimmed() {
        assertThat(v.studentId("  2023294 ")).isEqualTo("2023294");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "202329", "20232945", "1023294", "20A3294", "2023-294", "' OR 1=1 --"})
    @DisplayName("malformed student ids are rejected")
    void studentId_invalid(String id) {
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.studentId(id))
            .satisfies(e -> assertThat(e.getField()).isEqualTo("studentId"));
    }

    // ── payment amount: 0.01 .. 50,000.00, at most 2 decimals ─────────────────

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.02", "2500", "49999.99", "50000", "50000.00"})
    @DisplayName("amounts from 0.01 to 50,000.00 are accepted (BVA)")
    void amount_valid(String value) {
        Money m = v.paymentAmount(new BigDecimal(value));
        assertThat(m.paisa()).isEqualTo(new BigDecimal(value).movePointRight(2).longValueExact());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-50", "-0.01", "0", "0.00", "50000.01", "55000", "100000"})
    @DisplayName("zero, negative and above-limit amounts are rejected (BVA)")
    void amount_outOfRange(String value) {
        assertThatExceptionOfType(InvalidInputException.class)
            .isThrownBy(() -> v.paymentAmount(new BigDecimal(value)));
    }

    @Test
    @DisplayName("null amount and amounts with fractions of a paisa are rejected")
    void amount_nullAndTooPrecise() {
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.paymentAmount(null));
        assertThatExceptionOfType(InvalidInputException.class)
            .isThrownBy(() -> v.paymentAmount(new BigDecimal("10.005")));
    }

    // ── month and year ────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 12})
    void month_valid(int m) {
        assertThat(v.month(m)).isEqualTo(m);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 13, 100})
    void month_invalid(int m) {
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.month(m));
    }

    @ParameterizedTest
    @ValueSource(ints = {2020, 2026, 2100})
    void year_valid(int y) {
        assertThat(v.year(y)).isEqualTo(y);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2019, 2101})
    void year_invalid(int y) {
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.year(y));
    }

    // ── bill id, idempotency key, channel, date ───────────────────────────────

    @Test
    void billId_formats() {
        assertThat(v.billId("BILL-2023294-2026-11")).isEqualTo("BILL-2023294-2026-11");
        assertThat(v.billId("BILL-2023294-2026-1")).isEqualTo("BILL-2023294-2026-1");
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.billId("B1"));
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.billId(null));
    }

    @Test
    void idempotencyKey_rules() {
        assertThat(v.idempotencyKey("pay-2026.11_A1")).isEqualTo("pay-2026.11_A1");
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.idempotencyKey(null));
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.idempotencyKey(" "));
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.idempotencyKey("has space"));
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.idempotencyKey("x".repeat(65)));
    }

    @Test
    void channel_parsing() {
        assertThat(v.channel("jazzcash")).isEqualTo(PaymentChannel.JAZZCASH);
        assertThat(v.channel(" EASYPAISA ")).isEqualTo(PaymentChannel.EASYPAISA);
        assertThat(v.channel("bank_transfer")).isEqualTo(PaymentChannel.BANK_TRANSFER);
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.channel("paypal"))
            .withMessageContaining("JAZZCASH");
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.channel(null));
    }

    @Test
    void asOfDate_required() {
        LocalDate d = LocalDate.of(2026, 11, 30);
        assertThat(v.asOfDate(d)).isEqualTo(d);
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> v.asOfDate(null));
    }
}
