package com.giki.mess.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.Bill;
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

/**
 * The ledger is debited per meal scan, but a bill also carries rebates, discounts and fines.
 * Generating a bill must bring the ledger in line with the bill, exactly once
 * (otherwise a fully paid bill would leave money on the ledger).
 */
@DisplayName("Bill generation reconciles the student ledger")
class BillingLedgerReconciliationTest {

    static final String STUDENT = "2023294";
    static final LocalDate END_OF_NOV = LocalDate.of(2026, 11, 30);

    InMemoryLedgerRepository ledgers;
    InMemoryBillRepository bills;
    BillingServiceTest.FakeAttendance attendance;
    BillingService service;
    StudentLedger ledger;

    @BeforeEach
    void setUp() {
        ledgers = new InMemoryLedgerRepository();
        bills = new InMemoryBillRepository();
        attendance = new BillingServiceTest.FakeAttendance();
        ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledgers.save(ledger);
        TariffConfiguration tariff = TariffConfiguration.defaults();
        service = new BillingServiceImpl(ledgers, bills, attendance, tariff,
            BillingStrategies.defaults(tariff));
    }

    /** Simulates the observer having debited every scanned meal (20 full days = 8,000 PKR). */
    private void twentyDaysScannedAndDebited() {
        attendance.addFullDays(1, 20);
        ledger.debitAmount(Money.pkr(8_000));
    }

    @Test
    @DisplayName("a bill without adjustments leaves the ledger unchanged")
    void noAdjustments_ledgerUnchanged() {
        twentyDaysScannedAndDebited();

        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(8_000));
    }

    @Test
    @DisplayName("an absence rebate is credited to the ledger")
    void rebate_creditedToLedger() {
        twentyDaysScannedAndDebited();
        attendance.absences.add(LocalDate.of(2026, 11, 21));
        attendance.absences.add(LocalDate.of(2026, 11, 22));
        attendance.absences.add(LocalDate.of(2026, 11, 23));   // rebate 960

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(7_040));
        assertThat(ledger.getBalance()).isEqualTo(bill.getNetPayable());
    }

    @Test
    @DisplayName("a scholarship discount is credited to the ledger")
    void discount_creditedToLedger() {
        StudentLedger scholar = new StudentLedger("2023222", StudentCategory.SCHOLAR);
        ledgers.save(scholar);
        attendance.addFullDays(1, 20);
        scholar.debitAmount(Money.pkr(8_000));

        Bill bill = service.calculateMonthlyBill("2023222", 11, 2026, END_OF_NOV);

        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(6_000));
        assertThat(scholar.getBalance()).isEqualTo(Money.pkr(6_000));
    }

    @Test
    @DisplayName("a month with no meals charges the administrative fee to the ledger")
    void adminCharge_debitedToLedger() {
        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(100));
    }

    @Test
    @DisplayName("recalculating the same bill does not apply the adjustment twice")
    void recalculation_isIdempotent() {
        twentyDaysScannedAndDebited();
        attendance.absences.add(LocalDate.of(2026, 11, 21));
        attendance.absences.add(LocalDate.of(2026, 11, 22));
        attendance.absences.add(LocalDate.of(2026, 11, 23));

        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);
        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);
        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(7_040));
    }

    @Test
    @DisplayName("a late fine added by a later recalculation raises the ledger by the fine only")
    void lateFine_addsOnlyTheDifference() {
        twentyDaysScannedAndDebited();
        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);       // no fine yet

        Bill late = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 12, 15));

        assertThat(late.getFineAmount()).isEqualTo(Money.pkr(400));         // 5% of 8,000
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(8_400));
    }

    @Test
    @DisplayName("a PAID bill is not recalculated and the ledger is left alone")
    void paidBill_ledgerUntouched() {
        twentyDaysScannedAndDebited();
        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);
        bill.markAsPaid();

        service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 12, 15));

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(8_000));
    }
}
