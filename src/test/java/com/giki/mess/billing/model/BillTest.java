package com.giki.mess.billing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * UNIT TESTS: Bill Domain Entity
 *
 * Day 1 tests — verifying that the Bill class enforces its business rules
 * at the domain level, before BillingService even exists.
 *
 * Test naming convention: methodUnderTest_Scenario_ExpectedBehavior()
 *
 * These tests are the SE423 TDD baseline and also serve as:
 *   - Traceability evidence for RTM row FR-002
 *   - JaCoCo coverage contribution for the Bill entity
 */
@DisplayName("Bill Domain Entity Tests")
class BillTest {

    // ── Happy path (valid inputs) ──────────────────────────────────────────────

    @Test
    @DisplayName("new Bill with valid inputs should start as UNPAID")
    void newBill_withValidInputs_shouldStartAsUnpaid() {
        Bill bill = createSampleBill(3000.0, 0.0, 0.0, 0.0);

        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(bill.getNetPayable()).isEqualTo(3000.0);
    }

    @Test
    @DisplayName("net payable should equal: base - rebate + fine - discount")
    void netPayable_shouldFollowFormula() {
        // base=3000, rebate=400, fine=150, discount=0  →  net = 2750
        Bill bill = createSampleBill(3000.0, 400.0, 150.0, 0.0);

        assertThat(bill.getNetPayable()).isEqualTo(2750.0);
    }

    @Test
    @DisplayName("net payable should never go below zero (rebate cannot exceed base)")
    void netPayable_shouldNeverBeNegative_whenRebateExceedsBase() {
        // Scenario: student registered absence for the whole month — rebate > base
        // The system must floor net payable at 0.0, not produce a negative bill
        Bill bill = createSampleBill(1000.0, 2000.0, 0.0, 0.0);

        assertThat(bill.getNetPayable()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("markAsPaid should set status PAID and zero the net payable")
    void markAsPaid_shouldSetStatusPaidAndZeroBalance() {
        Bill bill = createSampleBill(3000.0, 0.0, 0.0, 0.0);
        bill.markAsPaid();

        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bill.getNetPayable()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("markAsOverdue should transition UNPAID to OVERDUE")
    void markAsOverdue_fromUnpaid_shouldBecomeOverdue() {
        Bill bill = createSampleBill(2000.0, 0.0, 0.0, 0.0);
        bill.markAsOverdue();

        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.OVERDUE);
    }

    @Test
    @DisplayName("markAsOverdue on a PAID bill should have no effect")
    void markAsOverdue_onPaidBill_shouldNotChangeStatus() {
        Bill bill = createSampleBill(2000.0, 0.0, 0.0, 0.0);
        bill.markAsPaid();
        bill.markAsOverdue(); // Should be ignored for already-paid bills

        // Still PAID — overdue does not retroactively reverse a payment
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("applyPartialPayment reduces net payable correctly")
    void applyPartialPayment_shouldReduceNetPayable() {
        Bill bill = createSampleBill(3000.0, 0.0, 0.0, 0.0);
        bill.applyPartialPayment(1000.0);

        assertThat(bill.getNetPayable()).isEqualTo(2000.0);
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("applyPartialPayment equal to full amount should mark bill as PAID")
    void applyPartialPayment_equalToFullAmount_shouldMarkPaid() {
        Bill bill = createSampleBill(3000.0, 0.0, 0.0, 0.0);
        bill.applyPartialPayment(3000.0);

        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bill.getNetPayable()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getItems should return an unmodifiable list (defensive copy)")
    void getItems_shouldReturnUnmodifiableList() {
        Bill bill = createSampleBill(3000.0, 0.0, 0.0, 0.0);

        // The returned list must be unmodifiable — callers cannot tamper with bill items
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> bill.getItems().add(null));
    }

    // ── Defensive validation (bad inputs must throw exceptions) ───────────────

    @Test
    @DisplayName("Bill with null billId should throw IllegalArgumentException")
    void constructor_withNullBillId_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Bill(null, "2023294", 11, 2026,
                LocalDate.now(), 3000.0, 0.0, 0.0, 0.0, Collections.emptyList()))
            .withMessageContaining("Bill ID cannot be null or blank");
    }

    @Test
    @DisplayName("Bill with invalid month (0) should throw IllegalArgumentException")
    void constructor_withInvalidMonth_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Bill("B001", "2023294", 0, 2026,
                LocalDate.now(), 3000.0, 0.0, 0.0, 0.0, Collections.emptyList()))
            .withMessageContaining("Month must be between 1 and 12");
    }

    @Test
    @DisplayName("Bill with negative base amount should throw IllegalArgumentException")
    void constructor_withNegativeBaseAmount_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Bill("B001", "2023294", 11, 2026,
                LocalDate.now(), -500.0, 0.0, 0.0, 0.0, Collections.emptyList()))
            .withMessageContaining("Base amount cannot be negative");
    }

    @Test
    @DisplayName("applyPartialPayment with zero amount should throw IllegalArgumentException")
    void applyPartialPayment_withZeroAmount_shouldThrowException() {
        Bill bill = createSampleBill(3000.0, 0.0, 0.0, 0.0);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> bill.applyPartialPayment(0.0))
            .withMessageContaining("Payment amount must be positive");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Creates a Bill with predictable test values and an empty item list */
    private Bill createSampleBill(double base, double rebate, double fine, double discount) {
        return new Bill(
            "BILL-TEST-001",           // billId
            "2023294",                 // studentId (Mahvash)
            11,                        // November
            2026,                      // year
            LocalDate.of(2026, 12, 10), // due date = 10th December
            base, rebate, fine, discount,
            Collections.emptyList()
        );
    }
}
