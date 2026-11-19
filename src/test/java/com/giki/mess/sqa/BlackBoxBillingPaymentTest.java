package com.giki.mess.sqa;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.api.BillingInputValidator;
import com.giki.mess.api.BillingPaymentApi;
import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.service.AttendanceDataSource;
import com.giki.mess.billing.service.BillingService;
import com.giki.mess.billing.service.BillingServiceImpl;
import com.giki.mess.billing.service.MealRecord;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.common.Money;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.service.PaymentServiceImpl;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import com.giki.mess.repository.persistence.InMemoryPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * SE431 black-box test design: Equivalence Partitioning (EP) and Boundary Value Analysis (BVA)
 * applied to the real inputs of the Billing (FR-002, BL-01) and Payment (FR-003, PY-01) modules.
 *
 * <p>Every case has an id (BB-xxx-nn) that appears in docs/sqa/BLACK_BOX_TEST_DESIGN.md and in the
 * Requirements Traceability Matrix (docs/sqa/RTM_v2.md).
 */
@DisplayName("SE431 black-box tests (EP + BVA)")
class BlackBoxBillingPaymentTest {

    static final String STUDENT = "2023294";
    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    // ═══════════════════════════════════════════════════════════════════════════
    // BB-AMT: payment amount (PY-01). Classes: <=0 invalid | 0.01..50,000.00 valid |
    // >50,000.00 invalid | more than 2 decimals invalid.
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BB-AMT payment amount [PY-01]")
    class PaymentAmount {

        BillingPaymentApi api;

        PaymentAmount() {
            InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
            InMemoryBillRepository bills = new InMemoryBillRepository();
            StudentLedger ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
            ledger.debitAmount(Money.pkr(60_000));
            ledgers.save(ledger);
            bills.save(new Bill("BILL-2023294-2026-11", STUDENT, 11, 2026, LocalDate.of(2026, 12, 10),
                Money.pkr(60_000), Money.ZERO, Money.ZERO, Money.ZERO, List.of()));
            JazzCashProcessor jazz = new JazzCashProcessor("secret", CLOCK);
            var payments = new PaymentServiceImpl(
                new PaymentProcessorFactory(Map.of(PaymentChannel.JAZZCASH, jazz)),
                new InMemoryPaymentRepository(), bills, ledgers);
            api = new BillingPaymentApi(null, payments, new BillingInputValidator());
        }

        @ParameterizedTest(name = "{0}: amount {1} -> {2}")
        @CsvSource({
            "BB-AMT-01, -50.00,    INPUT_INVALID",   // EP invalid: negative
            "BB-AMT-02, -0.01,     INPUT_INVALID",   // BVA: just below zero
            "BB-AMT-03, 0.00,      INPUT_INVALID",   // BVA: zero
            "BB-AMT-04, 0.01,      ACCEPTED",        // BVA: smallest valid
            "BB-AMT-05, 0.02,      ACCEPTED",        // BVA: just above the lower bound
            "BB-AMT-06, 2500.00,   ACCEPTED",        // EP valid: typical
            "BB-AMT-07, 49999.99,  ACCEPTED",        // BVA: just below the upper bound
            "BB-AMT-08, 50000.00,  ACCEPTED",        // BVA: upper bound
            "BB-AMT-09, 50000.01,  INPUT_INVALID",   // BVA: just above the upper bound
            "BB-AMT-10, 55000.00,  INPUT_INVALID",   // EP invalid: too large
            "BB-AMT-11, 10.005,    INPUT_INVALID"    // EP invalid: fraction of a paisa
        })
        @DisplayName("EP/BVA on the payment amount")
        void amount(String id, String amount, String expected) {
            String key = "KEY-" + id;
            var result = api.startPayment(STUDENT, "BILL-2023294-2026-11", new BigDecimal(amount),
                "JAZZCASH", key);

            if ("ACCEPTED".equals(expected)) {
                assertThat(result.success()).as(id).isTrue();
            } else {
                assertThat(result.error().code()).as(id).isEqualTo(expected);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BB-ABS: consecutive registered absence days D (BL-01). Classes: 0-2 no rebate |
    // 3-20 rebate D x 320 PKR | >20 capped at 20 days (6,400 PKR).
    // Daily rate 400 PKR x 80% = 320 PKR per qualifying day.
    // (A negative or >31 day count cannot be expressed: absences are a set of calendar dates
    // inside one month, so those partitions of the roadmap are not reachable at this layer.)
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BB-ABS absence rebate [BL-01]")
    class AbsenceRebate {

        @ParameterizedTest(name = "{0}: {1} consecutive absent days -> rebate {2} PKR")
        @CsvSource({
            "BB-ABS-01,  0,    0",       // EP no rebate
            "BB-ABS-02,  1,    0",
            "BB-ABS-03,  2,    0",       // BVA: just below the threshold
            "BB-ABS-04,  3,  960",       // BVA: threshold
            "BB-ABS-05,  4, 1280",       // BVA: just above the threshold
            "BB-ABS-06, 10, 3200",       // EP typical standard rebate
            "BB-ABS-07, 19, 6080",       // BVA: just below the cap
            "BB-ABS-08, 20, 6400",       // BVA: cap reached
            "BB-ABS-09, 21, 6400",       // BVA: just above the cap, still 20 days
            "BB-ABS-10, 24, 6400"        // EP capped (scans occupy days 25-30)
        })
        @DisplayName("EP/BVA on the number of consecutive absence days")
        void rebate(String id, int absentDays, long expectedRebatePkr) {
            InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
            ledgers.save(new StudentLedger(STUDENT, StudentCategory.REGULAR));
            Set<LocalDate> absences = new HashSet<>();
            for (int d = 1; d <= absentDays; d++) {
                absences.add(LocalDate.of(2026, 11, d));
            }
            BillingService service = billingWith(ledgers, mealsOnDays(25, 30), absences);

            Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30));

            assertThat(bill.getRebateAmount()).as(id).isEqualTo(Money.pkr(expectedRebatePkr));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BB-FIN: late fine by evaluation date (BL-01). Due date 10 December 2026.
    // Classes: on or before the due date no fine | after the due date 5% fine.
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BB-FIN late fine [BL-01]")
    class LateFine {

        @ParameterizedTest(name = "{0}: evaluated on {1} -> fine {2} PKR")
        @CsvSource({
            "BB-FIN-01, 2026-11-30,   0",    // EP before the due date
            "BB-FIN-02, 2026-12-09,   0",    // BVA: day before the due date
            "BB-FIN-03, 2026-12-10,   0",    // BVA: the due date itself
            "BB-FIN-04, 2026-12-11, 100",    // BVA: day after (5% of 2,000)
            "BB-FIN-05, 2027-02-01, 100"     // EP long overdue: fine is flat, not compounding
        })
        @DisplayName("BVA on the date relative to the due date")
        void fine(String id, LocalDate evaluatedOn, long expectedFinePkr) {
            InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
            ledgers.save(new StudentLedger(STUDENT, StudentCategory.REGULAR));
            BillingService service = billingWith(ledgers, mealsOnDays(1, 5), Set.of());   // 2,000 PKR

            Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, evaluatedOn);

            assertThat(bill.getFineAmount()).as(id).isEqualTo(Money.pkr(expectedFinePkr));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BB-CAT: student category tariff (BL-01). One full day of meals (400 PKR standard).
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BB-CAT category tariff [BL-01]")
    class CategoryTariff {

        @ParameterizedTest(name = "{0}: {1} -> net payable {2} PKR")
        @CsvSource({
            "BB-CAT-01, REGULAR,       400",    // standard rates
            "BB-CAT-02, SUBSIDIZED,    240",    // 60% rates
            "BB-CAT-03, SCHOLAR,       300",    // standard rates minus 25% discount
            "BB-CAT-04, FACULTY_GUEST, 480"     // 120% premium rates
        })
        @DisplayName("EP on the student category")
        void category(String id, StudentCategory category, long expectedNetPkr) {
            InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
            ledgers.save(new StudentLedger(STUDENT, category));
            BillingService service = billingWith(ledgers, mealsOnDays(1, 1), Set.of());

            Bill bill = service.calculateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30));

            assertThat(bill.getNetPayable()).as(id).isEqualTo(Money.pkr(expectedNetPkr));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BB-CBK: gateway callback authenticity and age (PY-01, QA-05).
    // Classes: valid signature fresh | forged | expired. Age limit 15 minutes.
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BB-CBK callback verification [PY-01, QA-05]")
    class CallbackVerification {

        final JazzCashProcessor jazz = new JazzCashProcessor("secret", CLOCK);

        private PaymentCallback callbackIssuedAt(Instant issuedAt, String signatureOverride) {
            String signature = signatureOverride != null ? signatureOverride
                : jazz.sign("TX-1", STUDENT, "BILL-1", Money.pkr(100), CallbackStatus.SUCCESS, "REF-1", issuedAt);
            return new PaymentCallback("TX-1", STUDENT, "BILL-1", Money.pkr(100), CallbackStatus.SUCCESS,
                "REF-1", issuedAt, signature);
        }

        @ParameterizedTest(name = "{0}: callback issued {1} seconds ago -> accepted={2}")
        @CsvSource({
            "BB-CBK-01,    0, true",     // EP valid and fresh
            "BB-CBK-02,  899, true",     // BVA: 14 min 59 s old
            "BB-CBK-03,  900, true",     // BVA: exactly 15 minutes old
            "BB-CBK-04,  901, false",    // BVA: 15 min 1 s old, expired
            "BB-CBK-05, 7200, false",    // EP expired
            "BB-CBK-06,  -30, true",     // clock skew tolerance: 30 s in the future
            "BB-CBK-07,  -61, false",    // BVA: more than 60 s in the future
        })
        @DisplayName("BVA on the age of a correctly signed callback")
        void age(String id, long secondsAgo, boolean expected) {
            Instant issued = NOW.minus(Duration.ofSeconds(secondsAgo));

            assertThat(jazz.verifyCallback(callbackIssuedAt(issued, null))).as(id).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}: signature \"{1}\" -> accepted=false")
        @CsvSource({
            "BB-CBK-08, deadbeef",                       // EP forged: random hex
            "BB-CBK-09, ''",                              // EP forged: empty
            "BB-CBK-10, 0000000000000000000000000000000000000000000000000000000000000000"
        })
        @DisplayName("EP on the signature: any wrong signature is rejected")
        void forgedSignature(String id, String signature) {
            assertThat(jazz.verifyCallback(callbackIssuedAt(NOW, signature))).as(id).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BB-ID: student id and month at the boundary (FR-002). Valid id = 7 digits starting 20.
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("BB-ID student id and month [FR-002]")
    class IdentifiersAndMonth {

        final BillingInputValidator validator = new BillingInputValidator();

        @ParameterizedTest(name = "{0}: student id \"{1}\" -> valid={2}")
        @CsvSource({
            "BB-ID-01, 2023294,  true",      // EP valid
            "BB-ID-02, 202329,   false",     // BVA: 6 digits
            "BB-ID-03, 2023294,  true",      // BVA: 7 digits
            "BB-ID-04, 20232945, false",     // BVA: 8 digits
            "BB-ID-05, 1023294,  false",     // EP wrong prefix
            "BB-ID-06, 20A3294,  false",     // EP non-digit
            "BB-ID-07, ' ',      false"      // EP blank
        })
        @DisplayName("BVA/EP on the student id")
        void studentId(String id, String value, boolean valid) {
            boolean accepted;
            try {
                validator.studentId(value);
                accepted = true;
            } catch (com.giki.mess.common.InvalidInputException e) {
                accepted = false;
            }
            assertThat(accepted).as(id).isEqualTo(valid);
        }

        @ParameterizedTest(name = "{0}: month {1} -> valid={2}")
        @CsvSource({
            "BB-ID-08, -1, false",
            "BB-ID-09,  0, false",    // BVA: just below
            "BB-ID-10,  1, true",     // BVA: lower bound
            "BB-ID-11,  2, true",
            "BB-ID-12, 11, true",
            "BB-ID-13, 12, true",     // BVA: upper bound
            "BB-ID-14, 13, false"     // BVA: just above
        })
        @DisplayName("BVA on the month")
        void month(String id, int value, boolean valid) {
            boolean accepted;
            try {
                validator.month(value);
                accepted = true;
            } catch (com.giki.mess.common.InvalidInputException e) {
                accepted = false;
            }
            assertThat(accepted).as(id).isEqualTo(valid);
        }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    /** Breakfast, lunch and dinner on each day in [from, to] of November 2026 (400 PKR per day). */
    static List<MealRecord> mealsOnDays(int from, int to) {
        List<MealRecord> meals = new ArrayList<>();
        for (int day = from; day <= to; day++) {
            for (MealType meal : MealType.values()) {
                meals.add(new MealRecord("E-" + day + "-" + meal,
                    LocalDateTime.of(2026, 11, day, 12, 0), meal));
            }
        }
        return meals;
    }

    static BillingService billingWith(InMemoryLedgerRepository ledgers, List<MealRecord> meals,
                                      Set<LocalDate> absences) {
        TariffConfiguration tariff = TariffConfiguration.defaults();
        return new BillingServiceImpl(ledgers, new InMemoryBillRepository(), new AttendanceDataSource() {
            @Override
            public List<MealRecord> mealsFor(String studentId, int month, int year) {
                return meals;
            }

            @Override
            public Set<LocalDate> registeredAbsences(String studentId, int month, int year) {
                return absences;
            }
        }, tariff, BillingStrategies.defaults(tariff));
    }
}
