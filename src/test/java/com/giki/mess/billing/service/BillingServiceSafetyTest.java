package com.giki.mess.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.common.Money;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regression tests for weaknesses found in review of the first billing engine. */
@DisplayName("BillingService safety rules")
class BillingServiceSafetyTest {

    static final String STUDENT = "2023294";
    static final LocalDate END_OF_NOV = LocalDate.of(2026, 11, 30);

    InMemoryBillRepository bills;
    BillingServiceTest.FakeAttendance attendance;
    BillingService service;

    @BeforeEach
    void setUp() {
        InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
        bills = new InMemoryBillRepository();
        attendance = new BillingServiceTest.FakeAttendance();
        ledgers.save(new StudentLedger(STUDENT, StudentCategory.REGULAR));
        TariffConfiguration tariff = TariffConfiguration.defaults();
        service = new BillingServiceImpl(ledgers, bills, attendance, tariff,
            BillingStrategies.defaults(tariff));
    }

    @Test
    @DisplayName("recalculating a PAID bill returns it unchanged (payment status is not lost)")
    void recalculating_paidBill_keepsPaidStatus() {
        attendance.addFullDays(1, 5);
        Bill first = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);
        first.markAsPaid();

        Bill again = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(again.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bills.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("recalculating a PARTIALLY_PAID bill keeps the remaining balance")
    void recalculating_partiallyPaidBill_keepsBalance() {
        attendance.addFullDays(1, 5);                      // 2,000 PKR
        Bill first = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);
        first.applyPartialPayment(Money.pkr(500));

        Bill again = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(again.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(again.getNetPayable()).isEqualTo(Money.pkr(1_500));
    }

    @Test
    @DisplayName("an UNPAID bill is still recalculated (e.g. late fine added later)")
    void recalculating_unpaidBill_updatesFine() {
        attendance.addFullDays(1, 5);
        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        Bill later = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 12, 15));

        assertThat(later.getFineAmount()).isEqualTo(Money.pkr(100)); // 5% of 2,000
    }

    @Test
    @DisplayName("a day with meal scans is not counted as an absence day")
    void absenceDayWithScans_isIgnored() {
        attendance.addFullDays(11, 11);                       // ate on the 11th
        attendance.absences.add(LocalDate.of(2026, 11, 10));
        attendance.absences.add(LocalDate.of(2026, 11, 11));  // contradicts the scans
        attendance.absences.add(LocalDate.of(2026, 11, 12));

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getRebateAmount()).isEqualTo(Money.ZERO); // 10th and 12th alone: runs of 1
    }

    @Test
    @DisplayName("null evaluation date is rejected instead of silently skipping the fine")
    void nullAsOfDate_rejected() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.calculateMonthlyBill(STUDENT, 11, 2026, null))
            .withMessageContaining("asOfDate");
    }
}
