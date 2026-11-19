package com.giki.mess.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.common.Money;
import com.giki.mess.repository.PersistenceException;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regression tests for defects found in the pre-inspection review (see docs/sqa/FAGAN_INSPECTION.md). */
@DisplayName("Inspection defects: billing")
class InspectionDefectsBillingTest {

    static final String STUDENT = "2023294";
    static final LocalDateTime AT = LocalDateTime.of(2026, 11, 20, 13, 0);

    /** Storage whose save() can be made to fail, to simulate a database outage. */
    static class FlakyLedgerRepository extends InMemoryLedgerRepository {
        boolean failSaves;

        @Override
        public StudentLedger save(StudentLedger ledger) {
            if (failSaves) {
                throw new PersistenceException("database down", null);
            }
            return super.save(ledger);
        }
    }

    FlakyLedgerRepository ledgers;
    StudentLedger ledger;
    BillingServiceTest.FakeAttendance attendance;
    BillingService service;

    @BeforeEach
    void setUp() {
        ledgers = new FlakyLedgerRepository();
        ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledgers.save(ledger);
        attendance = new BillingServiceTest.FakeAttendance();
        TariffConfiguration tariff = TariffConfiguration.defaults();
        service = new BillingServiceImpl(ledgers, new InMemoryBillRepository(), attendance, tariff,
            BillingStrategies.defaults(tariff));
    }

    @Test
    @DisplayName("DEF-01: a scan whose debit succeeded but whose save failed is not billed again on retry")
    void def01_retryAfterSaveFailure_doesNotDoubleBill() {
        ledgers.failSaves = true;
        assertThatExceptionOfType(PersistenceException.class).isThrownBy(
            () -> service.recordMealSwipe(STUDENT, MealType.LUNCH, AT, "E-1"));
        ledgers.failSaves = false;

        service.recordMealSwipe(STUDENT, MealType.LUNCH, AT, "E-1");   // the retry

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(150));      // billed once, not 300
        assertThat(ledger.getUnbilledMealCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DEF-04: generating a bill suspends a student who owes more than 10,000 PKR")
    void def04_overdueStudentIsSuspended() {
        attendance.addFullDays(1, 30);                 // 30 days x 400 = 12,000 PKR
        ledger.debitAmount(Money.pkr(12_000));

        service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30));

        assertThat(ledger.getStatus()).isEqualTo(StudentLedger.AccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("DEF-04: a student below the threshold stays ACTIVE")
    void def04_normalStudentStaysActive() {
        attendance.addFullDays(1, 20);
        ledger.debitAmount(Money.pkr(8_000));

        service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30));

        assertThat(ledger.isActive()).isTrue();
    }
}
