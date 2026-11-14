package com.giki.mess.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.DuplicateTransactionException;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Duplicate gateway callbacks arriving at the same instant must settle exactly once
 * (risk R-02, requirement QA-CONC-01, test group CT-01).
 */
@DisplayName("CT-01: duplicate payment callbacks settle exactly once")
class IdempotencyConcurrencyTest {

    static final String STUDENT = "2023294";
    static final String BILL_ID = "BILL-2023294-2026-11";
    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Ledger storage that is deliberately slow, which widens any race window. */
    static class SlowLedgerRepository extends InMemoryLedgerRepository {
        @Override
        public Optional<StudentLedger> findByStudentId(String studentId) {
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.findByStudentId(studentId);
        }
    }

    @RepeatedTest(5)
    @DisplayName("10 simultaneous identical callbacks: 1 settles, 9 rejected, ledger credited once")
    void tenDuplicateCallbacks_settleOnce() throws Exception {
        JazzCashProcessor jazz = new JazzCashProcessor("jazz-secret", CLOCK);
        InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
        InMemoryBillRepository bills = new InMemoryBillRepository();
        SlowLedgerRepository ledgers = new SlowLedgerRepository();
        StudentLedger ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(40_000));
        ledgers.save(ledger);
        Bill bill = new Bill(BILL_ID, STUDENT, 11, 2026, LocalDate.of(2026, 12, 10),
            Money.pkr(40_000), Money.ZERO, Money.ZERO, Money.ZERO, List.of());
        bills.save(bill);
        PaymentService service = new PaymentServiceImpl(
            new PaymentProcessorFactory(Map.of(PaymentChannel.JAZZCASH, jazz)),
            payments, bills, ledgers);

        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000),
            PaymentChannel.JAZZCASH, "KEY-CT01");
        String ref = "REF-1";
        PaymentCallback callback = new PaymentCallback(started.transactionId(), STUDENT, BILL_ID,
            Money.pkr(1_000), CallbackStatus.SUCCESS, ref, NOW,
            jazz.sign(started.transactionId(), STUDENT, BILL_ID, Money.pkr(1_000),
                CallbackStatus.SUCCESS, ref, NOW));

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger settled = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    service.settlePayment(callback);
                    settled.incrementAndGet();
                } catch (DuplicateTransactionException e) {
                    duplicates.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(settled.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(9);
        assertThat(unexpected.get()).isZero();
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(39_000));          // credited once
        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(39_000));
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(payments.findById(started.transactionId()).orElseThrow().getStatus())
            .isEqualTo(TransactionStatus.RECONCILED);
    }
}
