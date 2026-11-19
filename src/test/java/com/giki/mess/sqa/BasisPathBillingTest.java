package com.giki.mess.sqa;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.service.AttendanceDataSource;
import com.giki.mess.billing.service.BillingService;
import com.giki.mess.billing.service.BillingServiceImpl;
import com.giki.mess.billing.service.MealRecord;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.common.Money;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SE431 white-box tests: one test per basis path of BillingServiceImpl.calculateMonthlyBill.
 * V(G) = 6 (predicates: existing present, existing paid, more meals, no items, late). The control
 * flow graph and the path derivation are in docs/sqa/WHITE_BOX_ANALYSIS.md.
 */
@DisplayName("White-box: basis paths of calculateMonthlyBill (V(G) = 6)")
class BasisPathBillingTest {

    static final String STUDENT = "2023294";
    static final LocalDate IN_TIME = LocalDate.of(2026, 11, 30);
    static final LocalDate LATE = LocalDate.of(2026, 12, 15);

    InMemoryLedgerRepository ledgers;
    InMemoryBillRepository bills;
    List<MealRecord> meals;
    BillingService service;

    @BeforeEach
    void setUp() {
        ledgers = new InMemoryLedgerRepository();
        ledgers.save(new StudentLedger(STUDENT, StudentCategory.REGULAR));
        bills = new InMemoryBillRepository();
        meals = new ArrayList<>();
        TariffConfiguration tariff = TariffConfiguration.defaults();
        service = new BillingServiceImpl(ledgers, bills, new AttendanceDataSource() {
            @Override
            public List<MealRecord> mealsFor(String studentId, int month, int year) {
                return meals;
            }

            @Override
            public Set<LocalDate> registeredAbsences(String studentId, int month, int year) {
                return Set.of();
            }
        }, tariff, BillingStrategies.defaults(tariff));
    }

    private void meal(int count) {
        MealType[] types = MealType.values();
        for (int i = 0; i < count; i++) {
            meals.add(new MealRecord("E" + i, LocalDateTime.of(2026, 11, 1 + i, 12, 0), types[i % 3]));
        }
    }

    @Test
    @DisplayName("Path 1: a bill that already received a payment is returned unchanged")
    void path1_existingPaidBill() {
        meal(1);
        Bill first = service.calculateMonthlyBill(STUDENT, 11, 2026, IN_TIME);
        first.markAsPaid();

        Bill again = service.calculateMonthlyBill(STUDENT, 11, 2026, LATE);

        assertThat(again).isSameAs(first);
        assertThat(again.getFineAmount()).isEqualTo(Money.ZERO);       // not recalculated
        assertThat(again.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("Path 2: no existing bill, no meals: minimal administrative bill, on time")
    void path2_noMeals_administrativeCharge() {
        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, IN_TIME);

        assertThat(bill.getItems()).isEmpty();
        assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(100));
        assertThat(bill.getFineAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("Path 3: no existing bill, exactly one meal, on time")
    void path3_oneMeal_onTime() {
        meal(1);

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, IN_TIME);

        assertThat(bill.getItems()).hasSize(1);
        assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(100));     // breakfast
        assertThat(bill.getFineAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("Path 4: meals present and the evaluation date is after the due date: late fine")
    void path4_lateFine() {
        meal(1);

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, LATE);

        assertThat(bill.getFineAmount()).isEqualTo(Money.pkr(5));       // 5% of 100
    }

    @Test
    @DisplayName("Path 5: an existing UNPAID bill is recalculated and replaced")
    void path5_existingUnpaidBillRecalculated() {
        meal(1);
        Bill first = service.calculateMonthlyBill(STUDENT, 11, 2026, IN_TIME);
        meal(2);                                                          // two more meals arrive

        Bill second = service.calculateMonthlyBill(STUDENT, 11, 2026, IN_TIME);

        assertThat(second).isNotSameAs(first);
        assertThat(second.getItems()).hasSize(3);
        assertThat(bills.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Path 6: the meal loop runs more than once")
    void path6_severalMeals() {
        meal(3);                                                          // breakfast, lunch, dinner

        Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, IN_TIME);

        assertThat(bill.getItems()).hasSize(3);
        assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(400));
    }
}
