package com.giki.mess.observer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.service.BillingService;
import com.giki.mess.billing.service.BillingServiceImpl;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.common.Money;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Attendance scans reach the ledger through the Observer pattern (FR-002, QA-CONC-01). */
@DisplayName("BillingObserver / recordMealSwipe")
class BillingObserverTest {

    static final String STUDENT = "2023294";
    static final LocalDateTime AT = LocalDateTime.of(2026, 11, 20, 13, 0);

    InMemoryLedgerRepository ledgers;
    StudentLedger ledger;
    BillingService billing;
    ExecutorService pool;
    AttendanceSubject subject;

    @BeforeEach
    void setUp() {
        ledgers = new InMemoryLedgerRepository();
        ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledgers.save(ledger);
        TariffConfiguration tariff = TariffConfiguration.defaults();
        billing = new BillingServiceImpl(ledgers, new InMemoryBillRepository(),
            new com.giki.mess.billing.service.AttendanceDataSource() {
                @Override
                public java.util.List<com.giki.mess.billing.service.MealRecord> mealsFor(
                        String s, int m, int y) {
                    return Collections.emptyList();
                }

                @Override
                public Set<java.time.LocalDate> registeredAbsences(String s, int m, int y) {
                    return Set.of();
                }
            }, tariff, BillingStrategies.defaults(tariff));
        pool = Executors.newFixedThreadPool(4);
        subject = new AttendanceSubject(pool);
        subject.register(new BillingObserver(billing));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private void publish(String eventId, MealType type) throws Exception {
        subject.publish(new ScanEvent(eventId, STUDENT, AT, type, "T1")).get(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("a published scan debits the ledger by the meal rate and counts the meal")
    void scan_debitsLedger() throws Exception {
        publish("E1", MealType.LUNCH);

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(150));
        assertThat(ledger.getUnbilledMealCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("different meals add up exactly")
    void scans_accumulate() throws Exception {
        publish("E1", MealType.BREAKFAST);
        publish("E2", MealType.LUNCH);
        publish("E3", MealType.DINNER);

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(400));
        assertThat(ledger.getUnbilledMealCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("a replayed scan event (same eventId) is billed only once")
    void replayedEvent_billedOnce() throws Exception {
        publish("E1", MealType.LUNCH);
        publish("E1", MealType.LUNCH);
        publish("E1", MealType.LUNCH);

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(150));
        assertThat(ledger.getUnbilledMealCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("subsidized students are debited at their own rate")
    void subsidized_usesStrategyRate() throws Exception {
        StudentLedger sub = new StudentLedger("2023111", StudentCategory.SUBSIDIZED);
        ledgers.save(sub);

        subject.publish(new ScanEvent("E9", "2023111", AT, MealType.LUNCH, "T1"))
            .get(2, TimeUnit.SECONDS);

        assertThat(sub.getBalance()).isEqualTo(Money.pkr(90)); // 60% of 150
    }

    @Test
    @DisplayName("a scan for an unknown student does not break the publisher")
    void unknownStudent_isContained() throws Exception {
        subject.publish(new ScanEvent("E5", "2023999", AT, MealType.LUNCH, "T1"))
            .get(2, TimeUnit.SECONDS);   // must complete without throwing

        assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("recordMealSwipe reports an unknown student and blank input directly")
    void recordMealSwipe_directErrors() {
        assertThatExceptionOfType(LedgerNotFoundException.class).isThrownBy(
            () -> billing.recordMealSwipe("2023999", MealType.LUNCH, AT, "E7"));
        assertThatIllegalArgumentException().isThrownBy(
            () -> billing.recordMealSwipe(STUDENT, MealType.LUNCH, AT, " "));
        assertThatIllegalArgumentException().isThrownBy(
            () -> billing.recordMealSwipe(STUDENT, null, AT, "E8"));
    }

    @Test
    @DisplayName("a failed recording is not remembered, so the event can be retried")
    void failedRecording_canBeRetried() {
        assertThatExceptionOfType(LedgerNotFoundException.class).isThrownBy(
            () -> billing.recordMealSwipe("2023777", MealType.LUNCH, AT, "E-RETRY"));
        ledgers.save(new StudentLedger("2023777", StudentCategory.REGULAR));

        billing.recordMealSwipe("2023777", MealType.LUNCH, AT, "E-RETRY");

        assertThat(ledgers.findByStudentId("2023777").orElseThrow().getBalance())
            .isEqualTo(Money.pkr(150));
    }
}
