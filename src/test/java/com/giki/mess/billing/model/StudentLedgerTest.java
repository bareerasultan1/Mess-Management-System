package com.giki.mess.billing.model;

import com.giki.mess.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * UNIT TESTS: StudentLedger Domain Entity
 *
 * Day 1 tests — verifying ledger's balance arithmetic and
 * defensive validations before any concurrency tests are added.
 *
 * Day 4 will ADD concurrent stress tests to this entity.
 * These are the sequential (single-threaded) correctness tests.
 */
@DisplayName("StudentLedger Domain Entity Tests")
class StudentLedgerTest {

    @Test
    @DisplayName("new ledger should start with zero balance and ACTIVE status")
    void newLedger_shouldStartWithZeroBalanceAndActiveStatus() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);

        assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
        assertThat(ledger.getUnbilledMealCount()).isEqualTo(0);
        assertThat(ledger.isActive()).isTrue();
        assertThat(ledger.getStatus()).isEqualTo(StudentLedger.AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("debitAmount should increase balance and unbilled meal count")
    void debitAmount_shouldIncreaseBalanceAndMealCount() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);

        ledger.debitAmount(Money.pkr(120)); // Breakfast
        ledger.debitAmount(Money.pkr(150)); // Lunch

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(270));
        assertThat(ledger.getUnbilledMealCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("creditAmount should reduce balance and not go below zero")
    void creditAmount_shouldReduceBalanceButNeverBelowZero() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(500));

        // Pay more than owed — balance should floor at 0.0, not go negative
        ledger.creditAmount(Money.pkr(1000));

        assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("creditAmount equal to balance should clear the debt")
    void creditAmount_exactBalance_shouldClearDebt() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(3500));
        ledger.creditAmount(Money.pkr(3500));

        assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("clearUnbilledMeals should reset meal count to zero")
    void clearUnbilledMeals_shouldResetMealCount() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(120));
        ledger.debitAmount(Money.pkr(150));
        ledger.debitAmount(Money.pkr(200));

        assertThat(ledger.getUnbilledMealCount()).isEqualTo(3);

        ledger.clearUnbilledMeals();

        assertThat(ledger.getUnbilledMealCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("suspendIfOverdue should suspend account when balance exceeds 10000 PKR")
    void suspendIfOverdue_balanceOver10000_shouldSuspendAccount() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(11_000)); // Exceeds 10,000 PKR threshold

        ledger.suspendIfOverdue();

        assertThat(ledger.isActive()).isFalse();
        assertThat(ledger.getStatus()).isEqualTo(StudentLedger.AccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("suspendIfOverdue should NOT suspend when balance is below threshold")
    void suspendIfOverdue_belowThreshold_shouldNotSuspend() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(5_000)); // Below 10,000 PKR threshold

        ledger.suspendIfOverdue();

        assertThat(ledger.isActive()).isTrue();
    }

    @Test
    @DisplayName("creditAmount on suspended account should reactivate it when balance hits zero")
    void creditAmount_onSuspendedAccount_shouldReactivateWhenBalanceClears() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(11_000));
        ledger.suspendIfOverdue();

        assertThat(ledger.isActive()).isFalse(); // Confirm suspended

        ledger.creditAmount(Money.pkr(11_000)); // Full payment

        assertThat(ledger.isActive()).isTrue(); // Should reactivate
    }

    // ── Defensive validation ────────────────────────────────────────────────

    @Test
    @DisplayName("debitAmount with zero should throw IllegalArgumentException")
    void debitAmount_withZero_shouldThrowException() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> ledger.debitAmount(Money.pkr(0)))
            .withMessageContaining("Debit amount must be positive");
    }

    @Test
    @DisplayName("creditAmount with negative value should throw IllegalArgumentException")
    void creditAmount_withNegative_shouldThrowException() {
        StudentLedger ledger = new StudentLedger("2023294", StudentCategory.REGULAR);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> ledger.creditAmount(Money.pkr(-100)))
            .withMessageContaining("Credit amount must be positive");
    }

    @Test
    @DisplayName("constructor with null studentId should throw IllegalArgumentException")
    void constructor_withNullStudentId_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new StudentLedger(null, StudentCategory.REGULAR))
            .withMessageContaining("studentId cannot be null or blank");
    }

    @Test
    @DisplayName("constructor with null category should throw IllegalArgumentException")
    void constructor_withNullCategory_shouldThrowException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new StudentLedger("2023294", null))
            .withMessageContaining("category cannot be null");
    }
}
