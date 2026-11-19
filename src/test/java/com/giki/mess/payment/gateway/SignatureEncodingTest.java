package com.giki.mess.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.common.Money;
import com.giki.mess.payment.model.CallbackStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DEF-03 (pre-inspection review): moving text between neighbouring fields must change the signature. */
@DisplayName("DEF-03: callback signature uses an unambiguous encoding")
class SignatureEncodingTest {

    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    JazzCashProcessor jazz = new JazzCashProcessor("secret", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("field boundaries are part of the signed data")
    void shiftedFieldBoundaries_giveDifferentSignatures() {
        String a = jazz.sign("TX", "2023294|BILL-1", "", Money.pkr(10), CallbackStatus.SUCCESS, "R", NOW);
        String b = jazz.sign("TX", "2023294", "BILL-1|", Money.pkr(10), CallbackStatus.SUCCESS, "R", NOW);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("a delimiter inside the gateway reference cannot hide a changed bill id")
    void delimiterInReference_cannotShiftFields() {
        String a = jazz.sign("TX", "2023294", "BILL-1", Money.pkr(10), CallbackStatus.SUCCESS, "X|Y", NOW);
        String b = jazz.sign("TX", "2023294", "BILL-1|X", Money.pkr(10), CallbackStatus.SUCCESS, "Y", NOW);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("the same fields still always give the same signature")
    void sameInput_sameSignature() {
        String a = jazz.sign("TX", "2023294", "BILL-1", Money.pkr(10), CallbackStatus.SUCCESS, "R", NOW);
        String b = jazz.sign("TX", "2023294", "BILL-1", Money.pkr(10), CallbackStatus.SUCCESS, "R", NOW);

        assertThat(a).isEqualTo(b);
    }
}
