package com.giki.mess.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Money value object")
class MoneyTest {

    @Test
    @DisplayName("pkr() stores whole rupees as paisa")
    void pkr_storesPaisa() {
        assertThat(Money.pkr(120).paisa()).isEqualTo(12_000L);
    }

    @Test
    @DisplayName("plus and minus are exact (no floating-point drift)")
    void plusMinus_areExact() {
        Money tenth = Money.ofPaisa(10);
        Money fifth = Money.ofPaisa(20);
        assertThat(tenth.plus(fifth)).isEqualTo(Money.ofPaisa(30));
        assertThat(fifth.minus(tenth)).isEqualTo(Money.ofPaisa(10));
    }

    @Test
    @DisplayName("times multiplies by a whole factor")
    void times_multiplies() {
        assertThat(Money.pkr(150).times(25)).isEqualTo(Money.pkr(3750));
    }

    @Test
    @DisplayName("percent rounds HALF_UP to the nearest paisa")
    void percent_roundsHalfUp() {
        // 5% of 0.10 PKR (10 paisa) = 0.5 paisa -> rounds up to 1 paisa
        assertThat(Money.ofPaisa(10).percent(5)).isEqualTo(Money.ofPaisa(1));
        // 25% of 3333 paisa = 833.25 -> 833
        assertThat(Money.ofPaisa(3333).percent(25)).isEqualTo(Money.ofPaisa(833));
    }

    @Test
    @DisplayName("floorAtZero turns negative amounts into zero")
    void floorAtZero_clampsNegative() {
        assertThat(Money.pkr(-5).floorAtZero()).isEqualTo(Money.ZERO);
        assertThat(Money.pkr(5).floorAtZero()).isEqualTo(Money.pkr(5));
    }

    @Test
    @DisplayName("comparison helpers work")
    void comparisons() {
        assertThat(Money.pkr(5).isGreaterThan(Money.pkr(4))).isTrue();
        assertThat(Money.pkr(4).min(Money.pkr(5))).isEqualTo(Money.pkr(4));
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.pkr(1).isPositive()).isTrue();
        assertThat(Money.pkr(-1).isNegative()).isTrue();
    }

    @Test
    @DisplayName("overflow is detected instead of silently wrapping")
    void overflow_isDetected() {
        Money huge = Money.ofPaisa(Long.MAX_VALUE);
        assertThatExceptionOfType(ArithmeticException.class)
            .isThrownBy(() -> huge.plus(Money.ofPaisa(1)));
    }

    @Test
    @DisplayName("toString prints PKR with two decimals")
    void toString_formats() {
        assertThat(Money.ofPaisa(12_050).toString()).isEqualTo("120.50 PKR");
    }
}
