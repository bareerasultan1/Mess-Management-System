package com.giki.mess.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.exception.BillNotFoundException;
import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.MealType;
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

/** Zero-meal bills, input validation and the itemized invoice (FR-002 / BL-01). */
@DisplayName("BillingService: invoice, zero meals, validation")
class BillingServiceInvoiceAndValidationTest {

    static final String STUDENT = "2023294";
    static final LocalDate END_OF_NOV = LocalDate.of(2026, 11, 30);

    InMemoryLedgerRepository ledgers;
    InMemoryBillRepository bills;
    BillingServiceTest.FakeAttendance attendance;
    BillingService service;

    @BeforeEach
    void setUp() {
        ledgers = new InMemoryLedgerRepository();
        bills = new InMemoryBillRepository();
        attendance = new BillingServiceTest.FakeAttendance();
        ledgers.save(new StudentLedger(STUDENT, StudentCategory.REGULAR));
        TariffConfiguration tariff = TariffConfiguration.defaults();
        service = new BillingServiceImpl(ledgers, bills, attendance, tariff,
            BillingStrategies.defaults(tariff));
    }

    @Test
    @DisplayName("a month with no meals produces a minimal administrative charge of 100 PKR")
    void zeroMeals_minimalAdministrativeCharge() {
        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(100));
        assertThat(bill.getItems()).isEmpty();
    }

    @Test
    @DisplayName("recalculating the same month replaces the bill (no duplicate bill)")
    void recalculating_doesNotCreateDuplicates() {
        attendance.addFullDays(1, 2);
        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);
        service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bills.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("null or blank student id is rejected")
    void blankStudentId_rejected() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.calculateMonthlyBill(null, 11, 2026, END_OF_NOV));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.calculateMonthlyBill("  ", 11, 2026, END_OF_NOV));
    }

    @Test
    @DisplayName("month outside 1-12 is rejected [BVA 0 and 13]")
    void invalidMonth_rejected() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.calculateMonthlyBill(STUDENT, 0, 2026, END_OF_NOV));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.calculateMonthlyBill(STUDENT, 13, 2026, END_OF_NOV));
    }

    @Test
    @DisplayName("unknown student raises LedgerNotFoundException")
    void unknownStudent_ledgerNotFound() {
        assertThatExceptionOfType(LedgerNotFoundException.class)
            .isThrownBy(() -> service.calculateMonthlyBill("2023999", 11, 2026, END_OF_NOV));
    }

    @Test
    @DisplayName("itemized invoice lists days attended, meals by type, and every amount")
    void itemizedInvoice_hasFullBreakdown() {
        attendance.addFullDays(1, 20);                       // 8,000
        attendance.absences.add(LocalDate.of(2026, 11, 21));
        attendance.absences.add(LocalDate.of(2026, 11, 22));
        attendance.absences.add(LocalDate.of(2026, 11, 23)); // rebate 960
        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        ItemizedInvoice invoice = service.generateItemizedInvoice(bill.getBillId());

        assertThat(invoice.daysAttended()).isEqualTo(20);
        assertThat(invoice.mealsByType().get(MealType.LUNCH)).isEqualTo(20);
        assertThat(invoice.mealsByType().get(MealType.BREAKFAST)).isEqualTo(20);
        assertThat(invoice.baseAmount()).isEqualTo(Money.pkr(8_000));
        assertThat(invoice.rebateAmount()).isEqualTo(Money.pkr(960));
        assertThat(invoice.netPayable()).isEqualTo(Money.pkr(7_040));
        assertThat(invoice.lines()).hasSize(60);
    }

    @Test
    @DisplayName("invoice for an unknown bill raises BillNotFoundException")
    void itemizedInvoice_unknownBill() {
        assertThatExceptionOfType(BillNotFoundException.class)
            .isThrownBy(() -> service.generateItemizedInvoice("NOPE"));
    }
}
