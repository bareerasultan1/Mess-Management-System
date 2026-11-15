package com.giki.mess;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.api.ApiResult;
import com.giki.mess.api.CallbackOutcome;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.service.AttendanceDataSource;
import com.giki.mess.billing.service.MealRecord;
import com.giki.mess.common.Money;
import com.giki.mess.observer.ScanEvent;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.payment.model.TransactionStatus;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Scan to bill to payment on a real SQLite file, including an application restart.
 * Validation activity for FR-002, FR-003, QA-01, QA-CONC-01.
 */
@DisplayName("End-to-end: scans -> bill -> JazzCash payment -> duplicate callback -> restart")
class BillingPaymentEndToEndIntegrationTest {

    static final String STUDENT = "2023294";
    static final String BILL_ID = "BILL-2023294-2026-11";
    static final Instant NOW = Instant.parse("2026-11-30T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final Map<PaymentChannel, String> SECRETS = Map.of(
        PaymentChannel.JAZZCASH, "jazz-secret",
        PaymentChannel.EASYPAISA, "easy-secret",
        PaymentChannel.BANK_TRANSFER, "bank-secret");

    /** Stand-in for the Attendance module: remembers every scan it published. */
    static class RecordingAttendance implements AttendanceDataSource {
        final List<MealRecord> meals = new CopyOnWriteArrayList<>();
        final Set<LocalDate> absences = Set.of(
            LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 28));

        @Override
        public List<MealRecord> mealsFor(String studentId, int month, int year) {
            return meals;
        }

        @Override
        public Set<LocalDate> registeredAbsences(String studentId, int month, int year) {
            return absences;
        }
    }

    @TempDir
    Path folder;

    RecordingAttendance attendance = new RecordingAttendance();
    Path dbFile;

    @BeforeEach
    void setUp() {
        dbFile = folder.resolve("mess.db");
    }

    private BillingPaymentModule start() {
        return BillingPaymentModule.create(dbFile, CLOCK, SECRETS, attendance);
    }

    private void scan25Days(BillingPaymentModule app) throws Exception {
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (int day = 1; day <= 25; day++) {
            for (MealType meal : MealType.values()) {
                LocalDateTime when = LocalDateTime.of(2026, 11, day, 8 + meal.ordinal() * 5, 0);
                String eventId = "E-" + day + "-" + meal;
                attendance.meals.add(new MealRecord(eventId, when, meal));
                pending.add(app.attendanceSubject().publish(
                    new ScanEvent(eventId, STUDENT, when, meal, "T1")));
            }
        }
        CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
    }

    private PaymentCallback gatewayCallback(PaymentResponse started, Money amount) {
        JazzCashProcessor gateway = new JazzCashProcessor(SECRETS.get(PaymentChannel.JAZZCASH), CLOCK);
        String ref = "JC-REF-001";
        return new PaymentCallback(started.transactionId(), STUDENT, BILL_ID, amount,
            CallbackStatus.SUCCESS, ref, NOW,
            gateway.sign(started.transactionId(), STUDENT, BILL_ID, amount, CallbackStatus.SUCCESS, ref, NOW));
    }

    @Test
    @DisplayName("full month: 75 scans, 3-day rebate, JazzCash payment, duplicate webhook, restart")
    void fullCycle() throws Exception {
        PaymentCallback callback;
        try (BillingPaymentModule app = start()) {
            // 1. student registered with a zero ledger
            app.ledgers().save(new StudentLedger(STUDENT, StudentCategory.REGULAR));

            // 2. 25 days x 3 meals arrive through the Observer pattern
            scan25Days(app);
            StudentLedger ledger = app.ledgers().findByStudentId(STUDENT).orElseThrow();
            assertThat(ledger.getBalance()).isEqualTo(Money.pkr(10_000));
            assertThat(ledger.getUnbilledMealCount()).isEqualTo(75);

            // 3. month end: bill with the 3-day absence rebate (3 x 320 = 960)
            ApiResult<Bill> billResult = app.api().generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30));
            assertThat(billResult.success()).isTrue();
            Bill bill = billResult.data();
            assertThat(bill.getBaseAmount()).isEqualTo(Money.pkr(10_000));
            assertThat(bill.getRebateAmount()).isEqualTo(Money.pkr(960));
            assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(9_040));
            assertThat(bill.getItems()).hasSize(75);
            assertThat(ledger.getBalance()).isEqualTo(Money.pkr(9_040));     // ledger agrees with the bill

            // 4. student pays through JazzCash
            ApiResult<PaymentResponse> started = app.api().startPayment(STUDENT, BILL_ID,
                new BigDecimal("9040.00"), "JAZZCASH", "PAY-NOV-2026");
            assertThat(started.success()).isTrue();

            // 5. gateway webhook arrives: signature checked, bill PAID, ledger cleared
            callback = gatewayCallback(started.data(), Money.pkr(9_040));
            assertThat(app.api().handleGatewayCallback(callback).data()).isEqualTo(CallbackOutcome.SETTLED);
            assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);

            // 6. the gateway retries the same webhook: acknowledged, nothing credited twice
            ApiResult<CallbackOutcome> duplicate = app.api().handleGatewayCallback(callback);
            assertThat(duplicate.success()).isTrue();
            assertThat(duplicate.data()).isEqualTo(CallbackOutcome.DUPLICATE_ACKNOWLEDGED);
            assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
        }

        // 7. "restart": a brand-new module reads everything back from the SQLite file
        try (BillingPaymentModule restarted = start()) {
            Bill bill = restarted.bills().findById(BILL_ID).orElseThrow();
            assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(bill.getItems()).hasSize(75);
            assertThat(restarted.ledgers().findByStudentId(STUDENT).orElseThrow().getBalance())
                .isEqualTo(Money.ZERO);
            List<PaymentTransaction> txs = restarted.payments().findByBillId(BILL_ID);
            assertThat(txs).hasSize(1);
            assertThat(txs.get(0).getStatus()).isEqualTo(TransactionStatus.RECONCILED);

            // the in-memory idempotency gate is empty now, yet the stored status still blocks a replay
            ApiResult<CallbackOutcome> replay = restarted.api().handleGatewayCallback(callback);
            assertThat(replay.data()).isEqualTo(CallbackOutcome.DUPLICATE_ACKNOWLEDGED);
            assertThat(restarted.ledgers().findByStudentId(STUDENT).orElseThrow().getBalance())
                .isEqualTo(Money.ZERO);

            // and the same payment reference cannot be started again either
            assertThat(restarted.api().startPayment(STUDENT, BILL_ID, new BigDecimal("1.00"),
                "JAZZCASH", "PAY-NOV-2026").error().code()).isIn("DUPLICATE_PAYMENT", "BILL_ALREADY_PAID");
        }
    }

    @Test
    @DisplayName("gateway outage leaves the account untouched, and a retry after recovery settles")
    void outageThenRecovery() throws Exception {
        try (BillingPaymentModule app = start()) {
            app.ledgers().save(new StudentLedger(STUDENT, StudentCategory.REGULAR));
            scan25Days(app);
            app.api().generateMonthlyBill(STUDENT, 11, 2026, LocalDate.of(2026, 11, 30));
            Money owed = Money.pkr(9_040);

            app.jazzCash().setSimulatedOutage(true);
            ApiResult<PaymentResponse> down = app.api().startPayment(STUDENT, BILL_ID,
                new BigDecimal("9040"), "JAZZCASH", "TRY-1");
            assertThat(down.error().code()).isEqualTo("GATEWAY_UNAVAILABLE");
            assertThat(app.ledgers().findByStudentId(STUDENT).orElseThrow().getBalance()).isEqualTo(owed);
            assertThat(app.bills().findById(BILL_ID).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.UNPAID);

            app.jazzCash().setSimulatedOutage(false);
            ApiResult<PaymentResponse> retry = app.api().startPayment(STUDENT, BILL_ID,
                new BigDecimal("9040"), "JAZZCASH", "TRY-2");
            assertThat(retry.success()).isTrue();
            assertThat(app.api().handleGatewayCallback(gatewayCallback(retry.data(), owed)).data())
                .isEqualTo(CallbackOutcome.SETTLED);
            assertThat(app.ledgers().findByStudentId(STUDENT).orElseThrow().getBalance()).isEqualTo(Money.ZERO);
        }
    }
}
