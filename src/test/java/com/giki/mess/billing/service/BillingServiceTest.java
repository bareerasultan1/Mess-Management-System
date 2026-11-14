package com.giki.mess.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
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
        service = new BillingServiceImpl(ledgers, bills, attendance);
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
}
