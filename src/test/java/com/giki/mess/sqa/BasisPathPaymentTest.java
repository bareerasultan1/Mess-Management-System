package com.giki.mess.sqa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.giki.mess.billing.exception.BillNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.DuplicateTransactionException;
import com.giki.mess.payment.exception.InvalidCallbackException;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.idempotency.IdempotencyManager;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.payment.model.TransactionStatus;
import com.giki.mess.payment.service.PaymentService;
import com.giki.mess.payment.service.PaymentServiceImpl;
import com.giki.mess.repository.persistence.InMemoryBillRepository;
import com.giki.mess.repository.persistence.InMemoryLedgerRepository;
import com.giki.mess.repository.persistence.InMemoryPaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * SE431 white-box tests: one test per basis path of PaymentServiceImpl.settlePayment.
 * V(G) = 7 (predicates: callback missing, key already claimed, transaction not INITIATED,
 * gateway reported FAILED, lookup failed, settlement slower than the target).
 * The control flow graph and the path derivation are in docs/sqa/WHITE_BOX_ANALYSIS.md.
 */
@DisplayName("White-box: basis paths of settlePayment (V(G) = 7)")
class BasisPathPaymentTest {

    static final String STUDENT = "2023294";
    static final String BILL_ID = "BILL-2023294-2026-11";
    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    JazzCashProcessor jazz = new JazzCashProcessor("secret", CLOCK);
    InMemoryPaymentRepository payments;
    InMemoryBillRepository bills;
    InMemoryLedgerRepository ledgers;
    StudentLedger ledger;
    Bill bill;
    PaymentProcessorFactory factory;
    PaymentService service;
    ListAppender<ILoggingEvent> log;
    Logger serviceLogger;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        bills = new InMemoryBillRepository();
        ledgers = new InMemoryLedgerRepository();
        ledger = new StudentLedger(STUDENT, StudentCategory.REGULAR);
        ledger.debitAmount(Money.pkr(4_000));
        ledgers.save(ledger);
        bill = new Bill(BILL_ID, STUDENT, 11, 2026, LocalDate.of(2026, 12, 10),
            Money.pkr(4_000), Money.ZERO, Money.ZERO, Money.ZERO, List.of());
        bills.save(bill);
        factory = new PaymentProcessorFactory(Map.of(PaymentChannel.JAZZCASH, jazz));
        service = new PaymentServiceImpl(factory, payments, bills, ledgers);

        serviceLogger = (Logger) LoggerFactory.getLogger(PaymentServiceImpl.class);
        log = new ListAppender<>();
        log.start();
        serviceLogger.addAppender(log);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(log);
    }

    private PaymentCallback started(CallbackStatus status) {
        PaymentResponse r = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000),
            PaymentChannel.JAZZCASH, "KEY-" + System.nanoTime());
        String ref = "REF-" + r.transactionId();
        return new PaymentCallback(r.transactionId(), STUDENT, BILL_ID, Money.pkr(1_000), status, ref, NOW,
            jazz.sign(r.transactionId(), STUDENT, BILL_ID, Money.pkr(1_000), status, ref, NOW));
    }

    private boolean logged(String fragment) {
        return log.list.stream().anyMatch(e -> e.getFormattedMessage().contains(fragment));
    }

    @Test
    @DisplayName("Path 1: a missing callback is rejected")
    void path1_nullCallback() {
        assertThatExceptionOfType(InvalidCallbackException.class)
            .isThrownBy(() -> service.settlePayment(null))
            .withMessageContaining("missing");
    }

    @Test
    @DisplayName("Path 2: a second copy of the same callback is stopped by the idempotency gate")
    void path2_duplicateStoppedByGate() {
        PaymentCallback cb = started(CallbackStatus.SUCCESS);
        service.settlePayment(cb);

        assertThatExceptionOfType(DuplicateTransactionException.class)
            .isThrownBy(() -> service.settlePayment(cb));

        assertThat(logged("Duplicate callback ignored")).isTrue();
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(3_000));
    }

    @Test
    @DisplayName("Path 3: after a restart (empty gate) the stored status still stops a replay")
    void path3_duplicateStoppedByStoredStatus() {
        PaymentCallback cb = started(CallbackStatus.SUCCESS);
        service.settlePayment(cb);
        PaymentService restarted = new PaymentServiceImpl(factory, payments, bills, ledgers,
            new IdempotencyManager());                                    // gate is empty again

        assertThatExceptionOfType(DuplicateTransactionException.class)
            .isThrownBy(() -> restarted.settlePayment(cb));

        assertThat(logged("Already-settled transaction ignored")).isTrue();
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(3_000));
    }

    @Test
    @DisplayName("Path 4: a FAILED gateway outcome marks the transaction FAILED and returns early")
    void path4_failedOutcome() {
        PaymentCallback cb = started(CallbackStatus.FAILED);

        PaymentTransaction tx = service.settlePayment(cb);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(4_000));
    }

    @Test
    @DisplayName("Path 5: a missing bill releases the key, so the same callback succeeds after recovery")
    void path5_lookupFails_keyReleased() {
        PaymentCallback cb = started(CallbackStatus.SUCCESS);
        bills.deleteById(BILL_ID);

        assertThatExceptionOfType(BillNotFoundException.class)
            .isThrownBy(() -> service.settlePayment(cb));
        bills.save(bill);                                                 // the bill is available again
        PaymentTransaction tx = service.settlePayment(cb);                // retry is allowed

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
    }

    @Test
    @DisplayName("Path 6: a normal successful settlement within the time target")
    void path6_success() {
        PaymentTransaction tx = service.settlePayment(started(CallbackStatus.SUCCESS));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(logged("exceeded")).isFalse();
    }

    @Test
    @DisplayName("Path 7: a settlement slower than the target is still completed and a warning is logged")
    void path7_slowSettlement() {
        PaymentService strict = new PaymentServiceImpl(factory, payments, bills, ledgers,
            new IdempotencyManager(), Duration.ZERO);                     // any duration is "too slow"
        PaymentResponse r = strict.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000),
            PaymentChannel.JAZZCASH, "KEY-SLOW");
        PaymentCallback cb = new PaymentCallback(r.transactionId(), STUDENT, BILL_ID, Money.pkr(1_000),
            CallbackStatus.SUCCESS, "REF", NOW, jazz.sign(r.transactionId(), STUDENT, BILL_ID,
                Money.pkr(1_000), CallbackStatus.SUCCESS, "REF", NOW));

        PaymentTransaction tx = strict.settlePayment(cb);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
        assertThat(logged("exceeded")).isTrue();
    }
}
