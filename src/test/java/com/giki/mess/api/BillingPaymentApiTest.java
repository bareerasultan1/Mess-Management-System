package com.giki.mess.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.service.AttendanceDataSource;
import com.giki.mess.billing.service.BillingServiceImpl;
import com.giki.mess.billing.service.MealRecord;
import com.giki.mess.billing.strategy.BillingStrategies;
import com.giki.mess.common.Money;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.service.PaymentServiceImpl;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import com.giki.mess.repository.persistence.InMemoryPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The boundary turns every failure into a structured, user-safe result and writes an audit trail. */
@DisplayName("BillingPaymentApi (boundary)")
class BillingPaymentApiTest {

    static final String STUDENT = "2023294";
    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    JazzCashProcessor jazz = new JazzCashProcessor("jazz-secret", CLOCK);
    InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
    InMemoryBillRepository bills = new InMemoryBillRepository();
    InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    StudentLedger ledger;
    BillingPaymentApi api;
    ListAppender<ILoggingEvent> audit;
    Logger auditLogger;

    @BeforeEach
    void setUp() {
        ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledgers.save(ledger);
        TariffConfiguration tariff = TariffConfiguration.defaults();
        AttendanceDataSource attendance = new AttendanceDataSource() {
            @Override
            public List<MealRecord> mealsFor(String s, int m, int y) {
                List<MealRecord> meals = new ArrayList<>();
                for (int d = 1; d <= 10; d++) {
                    meals.add(new MealRecord("E" + d, LocalDateTime.of(2026, 11, d, 13, 0),
                        com.giki.mess.billing.model.MealType.LUNCH));
                }
                return meals;                                   // 10 lunches = 1,500 PKR
            }

            @Override
            public Set<LocalDate> registeredAbsences(String s, int m, int y) {
                return Set.of();
            }
        };
        var billing = new BillingServiceImpl(ledgers, bills, attendance, tariff,
            BillingStrategies.defaults(tariff));
        var paymentService = new PaymentServiceImpl(
            new PaymentProcessorFactory(Map.of(PaymentChannel.JAZZCASH, jazz)),
            payments, bills, ledgers);
        api = new BillingPaymentApi(billing, paymentService, new BillingInputValidator());

        auditLogger = (Logger) LoggerFactory.getLogger("com.giki.mess.audit");
        audit = new ListAppender<>();
        audit.start();
        auditLogger.addAppender(audit);
        auditLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(audit);
    }

    private PaymentCallback callback(PaymentResponse started, Money amount, CallbackStatus status) {
        return new PaymentCallback(started.transactionId(), STUDENT,
            "BILL-2023294-2026-11", amount, status, "REF-1", NOW,
            jazz.sign(started.transactionId(), STUDENT, "BILL-2023294-2026-11", amount, status, "REF-1", NOW));
    }

    private String auditText() {
        StringBuilder sb = new StringBuilder();
        audit.list.forEach(e -> sb.append(e.getFormattedMessage()).append('\n'));
        return sb.toString();
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate bill, start payment, settle callback, duplicate callback acknowledged")
    void happyPath() {
        ApiResult<Bill> bill = api.generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30));
        assertThat(bill.success()).isTrue();
        assertThat(bill.data().getNetPayable()).isEqualTo(Money.pkr(1_500));

        ApiResult<PaymentResponse> started = api.startPayment(STUDENT, bill.data().getBillId(),
            new BigDecimal("1500.00"), "jazzcash", "KEY-1");
        assertThat(started.success()).isTrue();

        PaymentCallback cb = callback(started.data(), Money.pkr(1_500), CallbackStatus.SUCCESS);
        assertThat(api.handleGatewayCallback(cb).data()).isEqualTo(CallbackOutcome.SETTLED);
        assertThat(bill.data().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        ApiResult<CallbackOutcome> again = api.handleGatewayCallback(cb);
        assertThat(again.success()).isTrue();
        assertThat(again.data()).isEqualTo(CallbackOutcome.DUPLICATE_ACKNOWLEDGED);
        assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("a FAILED gateway outcome is reported as PAYMENT_FAILED")
    void failedPaymentOutcome() {
        Bill bill = api.generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30)).data();
        PaymentResponse started = api.startPayment(STUDENT, bill.getBillId(),
            new BigDecimal("500"), "JAZZCASH", "KEY-F").data();

        ApiResult<CallbackOutcome> r = api.handleGatewayCallback(
            callback(started, Money.pkr(500), CallbackStatus.FAILED));

        assertThat(r.data()).isEqualTo(CallbackOutcome.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("itemized invoice is available through the boundary")
    void invoice() {
        Bill bill = api.generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30)).data();

        ApiResult<?> r = api.itemizedInvoice(bill.getBillId());

        assertThat(r.success()).isTrue();
    }

    // ── structured errors ─────────────────────────────────────────────────────

    @Test
    @DisplayName("invalid input is rejected with INPUT_INVALID before any service is called")
    void invalidInput() {
        ApiResult<Bill> r = api.generateMonthlyBill("abc", 11, 2026, LocalDate.of(2026, 11, 30));
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("INPUT_INVALID");

        assertThat(api.generateMonthlyBill(STUDENT, 13, 2026, LocalDate.of(2026, 11, 30))
            .error().code()).isEqualTo("INPUT_INVALID");
        assertThat(api.startPayment(STUDENT, "BILL-2023294-2026-11", new BigDecimal("50000.01"),
            "JAZZCASH", "K").error().code()).isEqualTo("INPUT_INVALID");
        assertThat(api.startPayment(STUDENT, "BILL-2023294-2026-11", new BigDecimal("10"),
            "paypal", "K").error().code()).isEqualTo("INPUT_INVALID");
    }

    @Test
    @DisplayName("unknown student and unknown bill give friendly domain errors")
    void notFoundErrors() {
        assertThat(api.generateMonthlyBill("2023999", 11, 2026, LocalDate.of(2026, 11, 30))
            .error().code()).isEqualTo("LEDGER_NOT_FOUND");
        assertThat(api.startPayment(STUDENT, "BILL-2023294-2026-10", new BigDecimal("10"),
            "JAZZCASH", "K").error().code()).isEqualTo("BILL_NOT_FOUND");
    }

    @Test
    @DisplayName("overpayment and reusing a key are reported without exceptions")
    void paymentRuleErrors() {
        Bill bill = api.generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30)).data();

        assertThat(api.startPayment(STUDENT, bill.getBillId(), new BigDecimal("1500.01"),
            "JAZZCASH", "K1").error().code()).isEqualTo("AMOUNT_EXCEEDS_DUE");

        api.startPayment(STUDENT, bill.getBillId(), new BigDecimal("100"), "JAZZCASH", "SAME");
        assertThat(api.startPayment(STUDENT, bill.getBillId(), new BigDecimal("100"),
            "JAZZCASH", "SAME").error().code()).isEqualTo("DUPLICATE_PAYMENT");
    }

    @Test
    @DisplayName("gateway outage gives GATEWAY_UNAVAILABLE and leaves the account untouched")
    void gatewayOutage() {
        Bill bill = api.generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30)).data();
        jazz.setSimulatedOutage(true);

        ApiResult<PaymentResponse> r = api.startPayment(STUDENT, bill.getBillId(),
            new BigDecimal("500"), "JAZZCASH", "K-OUT");

        assertThat(r.error().code()).isEqualTo("GATEWAY_UNAVAILABLE");
        assertThat(r.error().message()).contains("not been charged");
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(1_500));
    }

    @Test
    @DisplayName("a forged callback gives INVALID_CALLBACK and changes nothing")
    void forgedCallback() {
        Bill bill = api.generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30)).data();
        PaymentResponse started = api.startPayment(STUDENT, bill.getBillId(),
            new BigDecimal("500"), "JAZZCASH", "K-FORGE").data();
        PaymentCallback good = callback(started, Money.pkr(500), CallbackStatus.SUCCESS);
        PaymentCallback forged = new PaymentCallback(good.transactionId(), good.studentId(),
            good.billId(), good.amount(), good.status(), good.gatewayReference(), good.issuedAt(), "bad");

        ApiResult<CallbackOutcome> r = api.handleGatewayCallback(forged);

        assertThat(r.error().code()).isEqualTo("INVALID_CALLBACK");
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(1_500));
        assertThat(api.handleGatewayCallback(null).error().code()).isEqualTo("INVALID_CALLBACK");
    }

    @Test
    @DisplayName("error messages never expose exception names or stack traces")
    void messagesAreUserSafe() {
        List<ApiResult<?>> failures = List.of(
            api.generateMonthlyBill("bad", 11, 2026, LocalDate.of(2026, 11, 30)),
            api.generateMonthlyBill("2023999", 11, 2026, LocalDate.of(2026, 11, 30)),
            api.startPayment(STUDENT, "BILL-2023294-2026-10", new BigDecimal("10"), "JAZZCASH", "K"),
            api.handleGatewayCallback(null));

        for (ApiResult<?> f : failures) {
            assertThat(f.success()).isFalse();
            assertThat(f.error().message()).doesNotContain("Exception", "java.", "at com.", "SQL");
        }
    }

    // ── audit trail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("every operation is audited and student ids are masked (QA-05)")
    void auditTrailMasksStudentIds() {
        Bill bill = api.generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30)).data();
        api.startPayment(STUDENT, bill.getBillId(), new BigDecimal("100"), "JAZZCASH", "K-A");
        api.generateMonthlyBill("2023999", 11, 2026, LocalDate.of(2026, 11, 30));   // failure

        String text = auditText();
        assertThat(text).contains("GENERATE_BILL", "START_PAYMENT", "LEDGER_NOT_FOUND", "202****");
        assertThat(text).doesNotContain("2023294").doesNotContain("2023999");
        assertThat(audit.list).hasSizeGreaterThanOrEqualTo(3);
    }
}
