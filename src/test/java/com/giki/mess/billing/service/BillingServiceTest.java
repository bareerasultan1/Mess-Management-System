package com.giki.mess.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.strategy.BillingCalculationStrategy;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.common.Money;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the billing engine (FR-002, test group BL-01). Built test-first. */
@DisplayName("BillingService (FR-002 / BL-01)")
class BillingServiceTest {

    static final String STUDENT = "2023294";
    static final LocalDate END_OF_NOV = LocalDate.of(2026, 11, 30);

    InMemoryLedgerRepository ledgers;
    InMemoryBillRepository bills;
    FakeAttendance attendance;
    BillingService service;

    /** Hand-written fake of the Attendance module. */
    static class FakeAttendance implements AttendanceDataSource {
        final List<MealRecord> meals = new ArrayList<>();
        final Set<LocalDate> absences = new HashSet<>();

        @Override
        public List<MealRecord> mealsFor(String studentId, int month, int year) {
            return meals;
        }

        @Override
        public Set<LocalDate> registeredAbsences(String studentId, int month, int year) {
            return absences;
        }

        /** Adds breakfast, lunch and dinner for each day number in [from, to]. */
        void addFullDays(int from, int to) {
            for (int d = from; d <= to; d++) {
                for (MealType m : MealType.values()) {
                    meals.add(new MealRecord("E-" + d + "-" + m,
                        LocalDateTime.of(2026, 11, d, 12, 0), m));
                }
            }
        }
    }

    @BeforeEach
    void setUp() {
        ledgers = new InMemoryLedgerRepository();
        bills = new InMemoryBillRepository();
        attendance = new FakeAttendance();
        ledgers.save(new StudentLedger(STUDENT, StudentCategory.REGULAR));
        service = new BillingServiceImpl(ledgers, bills, attendance, TariffConfiguration.defaults(),
            BillingStrategies.defaults(TariffConfiguration.defaults()));
    }

    @Test
    @DisplayName("regular student: base bill = sum of meal rates for 25 full days")
    void calculateBaseBill_regularStudent() {
        attendance.addFullDays(1, 25); // 25 days x (100 + 150 + 150) = 10,000 PKR

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(10_000));
        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(10_000));
        assertThat(bill.getItems()).hasSize(75);
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(bill.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    // ── Cycle 2: absence rebate ───────────────────────────────────────────────

    private void absentDays(int... days) {
        for (int d : days) {
            attendance.absences.add(LocalDate.of(2026, 11, d));
        }
    }

    @Test
    @DisplayName("3 consecutive registered absence days earn 80% of the daily rate per day")
    void absenceRebate_appliedWhenAbsenceReachesThreshold() {
        attendance.addFullDays(1, 20);   // 20 x 400 = 8,000 PKR
        absentDays(21, 22, 23);          // daily rate 400 -> 80% = 320 x 3 = 960

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getRebateAmount()).isEqualTo(Money.pkr(960));
        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(8_000 - 960));
    }

    @Test
    @DisplayName("only 2 consecutive absence days earn no rebate (below threshold)")
    void absenceRebate_notAppliedBelowThreshold() {
        attendance.addFullDays(1, 20);
        absentDays(21, 22);

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getRebateAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("separate short absences are not added together")
    void absenceRebate_separateRunsAreNotCombined() {
        attendance.addFullDays(1, 20);
        absentDays(5, 6, 10, 11); // two runs of 2, none qualifies

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getRebateAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("rebate is capped at 20 days")
    void absenceRebate_cappedAtTwentyDays() {
        attendance.addFullDays(1, 5);
        for (int d = 6; d <= 30; d++) {
            absentDays(d); // 25 consecutive absent days
        }

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getRebateAmount()).isEqualTo(Money.pkr(320).times(20));
    }

    @Test
    @DisplayName("net payable never goes negative when rebate exceeds base")
    void netPayable_neverNegative() {
        attendance.addFullDays(1, 1);        // base 400 PKR
        absentDays(10, 11, 12, 13, 14);      // rebate 1,600 PKR

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getNetPayable()).isEqualTo(Money.ZERO);
    }

    // ── Cycle 3: late fine and tariff strategies ──────────────────────────────

    @Test
    @DisplayName("late fine: 5% of the amount due when evaluated after the due date")
    void lateFine_appliedAfterDueDate() {
        attendance.addFullDays(1, 25); // 10,000 PKR

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 12, 11));

        assertThat(bill.getFineAmount()).isEqualTo(Money.pkr(500));
        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(10_500));
    }

    @Test
    @DisplayName("no fine on the due date itself (boundary 10th) or before it")
    void lateFine_notAppliedOnOrBeforeDueDate() {
        attendance.addFullDays(1, 25);

        Bill onDue = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 12, 10));
        Bill before = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 12, 9));

        assertThat(onDue.getFineAmount()).isEqualTo(Money.ZERO);
        assertThat(before.getFineAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("fine is calculated on the amount after rebate")
    void lateFine_calculatedAfterRebate() {
        attendance.addFullDays(1, 20);  // 8,000
        absentDays(21, 22, 23);         // rebate 960 -> 7,040 -> fine 5% = 352

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 12, 20));

        assertThat(bill.getFineAmount()).isEqualTo(Money.pkr(352));
    }

    @Test
    @DisplayName("subsidized student pays reduced meal rates (60%)")
    void subsidizedStrategy_reducedRates() {
        ledgers.save(new StudentLedger("2023111", StudentCategory.SUBSIDIZED));
        attendance.addFullDays(1, 10); // regular price 4,000 -> subsidized 2,400

        Bill bill = service.calculateMonthlyBill("2023111", 11, 2026, END_OF_NOV);

        assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(2_400));
    }

    @Test
    @DisplayName("scholarship student gets 25% off the net meal cost")
    void scholarStrategy_discount() {
        ledgers.save(new StudentLedger("2023222", StudentCategory.SCHOLAR));
        attendance.addFullDays(1, 20); // 8,000

        Bill bill = service.calculateMonthlyBill("2023222", 11, 2026, END_OF_NOV);

        assertThat(bill.getDiscountAmount()).isEqualTo(Money.pkr(2_000));
        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(6_000));
    }

    @Test
    @DisplayName("a custom strategy can be injected without touching the service (OCP)")
    void customStrategy_isUsed() {
        BillingCalculationStrategy flat = new BillingCalculationStrategy() {
            @Override
            public Money rateFor(MealType mealType) {
                return Money.pkr(10);
            }

            @Override
            public Money discountOn(Money netMealCost) {
                return Money.ZERO;
            }
        };
        BillingService custom = new BillingServiceImpl(ledgers, bills, attendance,
            TariffConfiguration.defaults(), java.util.Map.of(StudentCategory.REGULAR, flat));
        attendance.addFullDays(1, 1);

        Bill bill = custom.calculateMonthlyBill(STUDENT, 11, 2026, END_OF_NOV);

        assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(30));
    }
}
