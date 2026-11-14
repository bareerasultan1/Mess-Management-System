package com.giki.mess.payment.model;

import com.giki.mess.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * UNIT TESTS: PaymentTransaction Domain Entity
 *
 * Day 1 tests — verifying the transaction entity's lifecycle state
 * transitions and defensive boundary validation.
 */
@DisplayName("PaymentTransaction Domain Entity Tests")
class PaymentTransactionTest {

    @Test
    @DisplayName("new transaction should start with INITIATED status")
    void newTransaction_shouldStartAsInitiated() {
        PaymentTransaction tx = createSampleTransaction(Money.pkr(3500));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.INITIATED);
    }

    @Test
    @DisplayName("markSuccess should transition status to SUCCESS")
    void markSuccess_shouldSetStatusToSuccess() {
        PaymentTransaction tx = createSampleTransaction(Money.pkr(3500));
        tx.markSuccess();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    @DisplayName("markFailed should transition status to FAILED")
    void markFailed_shouldSetStatusToFailed() {
        PaymentTransaction tx = createSampleTransaction(Money.pkr(3500));
        tx.markFailed();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("markReconciled should transition status to RECONCILED")
    void markReconciled_shouldSetStatusToReconciled() {
        PaymentTransaction tx = createSampleTransaction(Money.pkr(3500));
        tx.markSuccess();
        tx.markReconciled();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
    }

    @Test
    @DisplayName("markDuplicate should transition status to DUPLICATE")
    void markDuplicate_shouldSetStatusToDuplicate() {
        PaymentTransaction tx = createSampleTransaction(Money.pkr(3500));
        tx.markDuplicate();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
    }

    @Test
    @DisplayName("createdAt should be set automatically and not be null")
    void createdAt_shouldBeSetAutomatically() {
        PaymentTransaction tx = createSampleTransaction(Money.pkr(3500));

        assertThat(tx.getCreatedAt()).isNotNull();
    }

    // ── Defensive validation (BVA — Boundary Value Analysis for Day 6) ───────

    @Test
    @DisplayName("zero amount should throw IllegalArgumentException [BVA boundary]")
    void constructor_withZeroAmount_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new PaymentTransaction(
                "TXN-001", "2023294", "BILL-001", Money.ZERO, PaymentChannel.JAZZCASH, "KEY-001"))
            .withMessageContaining("Payment amount must be positive");
    }

    @Test
    @DisplayName("negative amount should throw IllegalArgumentException [BVA boundary]")
    void constructor_withNegativeAmount_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new PaymentTransaction(
                "TXN-001", "2023294", "BILL-001", Money.pkr(-50), PaymentChannel.EASYPAISA, "KEY-001"))
            .withMessageContaining("Payment amount must be positive");
    }

    @Test
    @DisplayName("amount above 50000.00 (50000.01) should throw IllegalArgumentException [BVA upper boundary]")
    void constructor_withAmountAboveLimit_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new PaymentTransaction(
                "TXN-001", "2023294", "BILL-001", Money.ofPaisa(5_000_001), PaymentChannel.BANK_TRANSFER, "KEY-001"))
            .withMessageContaining("exceeds maximum limit");
    }

    @Test
    @DisplayName("null channel should throw IllegalArgumentException")
    void constructor_withNullChannel_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new PaymentTransaction(
                "TXN-001", "2023294", "BILL-001", Money.pkr(3500), null, "KEY-001"))
            .withMessageContaining("PaymentChannel cannot be null");
    }

    @Test
    @DisplayName("blank idempotency key should throw IllegalArgumentException")
    void constructor_withBlankIdempotencyKey_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new PaymentTransaction(
                "TXN-001", "2023294", "BILL-001", Money.pkr(3500), PaymentChannel.JAZZCASH, "  "))
            .withMessageContaining("idempotencyKey cannot be null or blank");
    }

    @Test
    @DisplayName("amount exactly 50000.00 is accepted [BVA on-boundary]")
    void constructor_withAmountAtLimit_isAccepted() {
        PaymentTransaction tx = createSampleTransaction(Money.pkr(50_000));
        assertThat(tx.getAmount()).isEqualTo(Money.pkr(50_000));
    }

    @Test
    @DisplayName("smallest valid amount 0.01 is accepted [BVA lower boundary]")
    void constructor_withOnePaisa_isAccepted() {
        PaymentTransaction tx = createSampleTransaction(Money.ofPaisa(1));
        assertThat(tx.getAmount()).isEqualTo(Money.ofPaisa(1));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private PaymentTransaction createSampleTransaction(Money amount) {
        return new PaymentTransaction(
            "TXN-TEST-001",
            "2023294",
            "BILL-TEST-001",
            amount,
            PaymentChannel.JAZZCASH,
            "IDEMPOTENCY-KEY-001"
        );
    }
}
