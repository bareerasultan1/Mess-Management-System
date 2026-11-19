package com.giki.mess;

import com.giki.mess.api.ApiResult;
import com.giki.mess.api.CallbackOutcome;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.service.AttendanceDataSource;
import com.giki.mess.billing.service.MealRecord;
import com.giki.mess.observer.ScanEvent;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.repository.DatabaseConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Runs one student's month against the REAL PostgreSQL database named by the environment
 * variables MESS_DB_URL, MESS_DB_USER and MESS_DB_PASSWORD, and prints what happens.
 * Afterwards you can open pgAdmin or psql and see the rows this program created.
 *
 * <p>Run it with:   mvn -q compile exec:java
 *
 * <p>Each run uses a fresh demo student (id 20990xx) so it can be repeated; the rows stay in the
 * database on purpose so that you can inspect them.
 */
public final class DemoRunner {

    private DemoRunner() {
    }

    /**
     * Runs the demonstration against the database named by the environment.
     */
    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        System.out.println("Connecting with " + config);

        Clock clock = Clock.systemUTC();
        Map<PaymentChannel, String> secrets = Map.of(
            PaymentChannel.JAZZCASH, "demo-jazzcash-secret",
            PaymentChannel.EASYPAISA, "demo-easypaisa-secret",
            PaymentChannel.BANK_TRANSFER, "demo-bank-secret");

        List<MealRecord> meals = new CopyOnWriteArrayList<>();
        AttendanceDataSource attendance = new AttendanceDataSource() {
            @Override
            public List<MealRecord> mealsFor(String studentId, int month, int year) {
                return meals;
            }

            @Override
            public Set<LocalDate> registeredAbsences(String studentId, int month, int year) {
                return Set.of(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 27),
                    LocalDate.of(2026, 11, 28));
            }
        };

        String student = "20990" + ThreadLocalRandom.current().nextInt(10, 99);
        String billId = "BILL-" + student + "-2026-11";

        try (BillingPaymentModule app = BillingPaymentModule.create(config, clock, secrets, attendance)) {
            System.out.println("Connected. Tables are ready.");
            app.ledgers().save(new StudentLedger(student, StudentCategory.REGULAR));
            System.out.println("1. Registered student " + student);

            List<CompletableFuture<Void>> pending = new ArrayList<>();
            for (int day = 1; day <= 25; day++) {
                for (MealType meal : MealType.values()) {
                    LocalDateTime when = LocalDateTime.of(2026, 11, day, 8 + meal.ordinal() * 5, 0);
                    String eventId = UUID.randomUUID().toString();
                    meals.add(new MealRecord(eventId, when, meal));
                    pending.add(app.attendanceSubject().publish(
                        new ScanEvent(eventId, student, when, meal, "T1")));
                }
            }
            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            System.out.println("2. 75 meal scans recorded. Ledger balance: "
                + app.ledgers().findByStudentId(student).orElseThrow().getBalance());

            ApiResult<Bill> bill = app.api().generateMonthlyBill(student, 11, 2026, LocalDate.of(2026, 11, 30));
            System.out.println("3. Bill " + billId + ": base " + bill.data().getBaseAmount()
                + ", rebate " + bill.data().getRebateAmount() + ", payable " + bill.data().getNetPayable());

            ApiResult<PaymentResponse> started = app.api().startPayment(student, billId,
                new BigDecimal("9040.00"), "JAZZCASH", "DEMO-" + UUID.randomUUID());
            System.out.println("4. Payment started: " + (started.success() ? "OK" : started.error()));

            JazzCashProcessor gateway = new JazzCashProcessor(secrets.get(PaymentChannel.JAZZCASH), clock);
            Instant now = Instant.now(clock);
            com.giki.mess.common.Money amount = com.giki.mess.common.Money.pkr(9_040);
            PaymentCallback callback = new PaymentCallback(started.data().transactionId(), student, billId,
                amount, CallbackStatus.SUCCESS, "DEMO-REF", now,
                gateway.sign(started.data().transactionId(), student, billId, amount,
                    CallbackStatus.SUCCESS, "DEMO-REF", now));
            ApiResult<CallbackOutcome> settled = app.api().handleGatewayCallback(callback);
            System.out.println("5. Gateway callback: " + settled.data());
            System.out.println("6. Same callback again: " + app.api().handleGatewayCallback(callback).data());
            System.out.println("7. Final ledger balance: "
                + app.ledgers().findByStudentId(student).orElseThrow().getBalance()
                + ", bill status: " + bill.data().getPaymentStatus());
        }
        System.out.println("Done. Open pgAdmin or psql and look at the tables for student " + student + ".");
    }
}
