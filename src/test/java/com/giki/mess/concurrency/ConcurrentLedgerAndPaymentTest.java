package com.giki.mess.concurrency;

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
import com.giki.mess.observer.AttendanceSubject;
import com.giki.mess.observer.BillingObserver;
import com.giki.mess.observer.ScanEvent;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.TransactionStatus;
import com.giki.mess.payment.service.PaymentService;
import com.giki.mess.payment.service.PaymentServiceImpl;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import com.giki.mess.repository.persistence.InMemoryPaymentRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Stress tests for thread safety (QA-CONC-01, test group CT-01): many threads change the same
 * student's money at once and no update may be lost.
 */
@DisplayName("CT-01: concurrent ledger, meal swipes and payments")
class ConcurrentLedgerAndPaymentTest {

    static final String STUDENT = "2023294";
    static final String BILL_ID = "BILL-2023294-2026-11";
    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Starts all tasks at the same moment so they really overlap. */
    private static void runTogether(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                task.run();
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);   // rethrows any exception from a task
        }
        pool.shutdown();
    }

    @RepeatedTest(5)
    @DisplayName("50 threads debit and credit one ledger: no lost updates")
    void fiftyThreads_noLostUpdates() throws Exception {
        StudentLedger ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(1_000_000));   // large start so the zero floor is never hit
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < 25; t++) {
            tasks.add(() -> {
                for (int i = 0; i < 20; i++) {
                    ledger.debitAmount(Money.pkr(100));
                }
            });
            tasks.add(() -> {
                for (int i = 0; i < 20; i++) {
                    ledger.creditAmount(Money.pkr(50));
                }
            });
        }

        runTogether(tasks);

        // 1,000,000 + 25*20*100 - 25*20*50 = 1,025,000
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(1_025_000));
        assertThat(ledger.getUnbilledMealCount()).isEqualTo(1 + 25 * 20);
    }

    @RepeatedTest(5)
    @DisplayName("200 meal scans arriving through the observer while a payment settles")
    void scansAndPayment_together() throws Exception {
        InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
        InMemoryBillRepository bills = new InMemoryBillRepository();
        InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        JazzCashProcessor jazz = new JazzCashProcessor("jazz-secret", CLOCK);
        StudentLedger ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(40_000));
        ledgers.save(ledger);
        Bill bill = new Bill(BILL_ID, STUDENT, 11, 2026, LocalDate.of(2026, 12, 10),
            Money.pkr(40_000), Money.ZERO, Money.ZERO, Money.ZERO, List.of());
        bills.save(bill);
        TariffConfiguration tariff = TariffConfiguration.defaults();
        BillingService billing = new BillingServiceImpl(ledgers, bills, new AttendanceDataSource() {
            @Override
            public List<MealRecord> mealsFor(String s, int m, int y) {
                return List.of();
            }

            @Override
            public Set<LocalDate> registeredAbsences(String s, int m, int y) {
                return Set.of();
            }
        }, tariff, BillingStrategies.defaults(tariff));
        PaymentService payment = new PaymentServiceImpl(
            new PaymentProcessorFactory(Map.of(PaymentChannel.JAZZCASH, jazz)),
            payments, bills, ledgers);
        ExecutorService observerPool = Executors.newFixedThreadPool(8);
        AttendanceSubject subject = new AttendanceSubject(observerPool);
        subject.register(new BillingObserver(billing));

        PaymentResponse started = payment.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000),
            PaymentChannel.JAZZCASH, "KEY-MIX");
        PaymentCallback cb = new PaymentCallback(started.transactionId(), STUDENT, BILL_ID,
            Money.pkr(1_000), CallbackStatus.SUCCESS, "REF-1", NOW,
            jazz.sign(started.transactionId(), STUDENT, BILL_ID, Money.pkr(1_000),
                CallbackStatus.SUCCESS, "REF-1", NOW));

        List<CompletableFuture<Void>> scans = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(() -> payment.settlePayment(cb));
        for (int i = 0; i < 200; i++) {
            final int n = i;
            tasks.add(() -> {
                CompletableFuture<Void> f = subject.publish(new ScanEvent("E-" + n, STUDENT,
                    LocalDateTime.of(2026, 11, 20, 13, 0), MealType.LUNCH, "T1"));
                synchronized (scans) {
                    scans.add(f);
                }
            });
        }
        runTogether(tasks);
        for (CompletableFuture<Void> f : scans) {
            f.get(10, TimeUnit.SECONDS);
        }
        observerPool.shutdown();

        // 40,000 owed + 200 lunches x 150 - 1,000 paid = 69,000
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(69_000));
    }

    @RepeatedTest(5)
    @DisplayName("100 different payments settle one bill at once: bill ends exactly PAID")
    void hundredPaymentsOnOneBill() throws Exception {
        InMemoryLedgerRepository ledgers = new InMemoryLedgerRepository();
        InMemoryBillRepository bills = new InMemoryBillRepository();
        InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        JazzCashProcessor jazz = new JazzCashProcessor("jazz-secret", CLOCK);
        StudentLedger ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(40_000));
        ledgers.save(ledger);
        Bill bill = new Bill(BILL_ID, STUDENT, 11, 2026, LocalDate.of(2026, 12, 10),
            Money.pkr(40_000), Money.ZERO, Money.ZERO, Money.ZERO, List.of());
        bills.save(bill);
        PaymentService payment = new PaymentServiceImpl(
            new PaymentProcessorFactory(Map.of(PaymentChannel.JAZZCASH, jazz)),
            payments, bills, ledgers);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            PaymentResponse started = payment.initiatePayment(STUDENT, BILL_ID, Money.pkr(400),
                PaymentChannel.JAZZCASH, "KEY-" + i);
            PaymentCallback cb = new PaymentCallback(started.transactionId(), STUDENT, BILL_ID,
                Money.pkr(400), CallbackStatus.SUCCESS, "REF-" + i, NOW,
                jazz.sign(started.transactionId(), STUDENT, BILL_ID, Money.pkr(400),
                    CallbackStatus.SUCCESS, "REF-" + i, NOW));
            tasks.add(() -> payment.settlePayment(cb));
        }

        runTogether(tasks);

        assertThat(bill.getNetPayable()).isEqualTo(Money.ZERO);
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
        assertThat(payments.findAll()).allMatch(t -> t.getStatus() == TransactionStatus.RECONCILED);
    }
}
