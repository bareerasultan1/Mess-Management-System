package com.giki.mess.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.giki.mess.billing.exception.BillNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.DuplicateTransactionException;
import com.giki.mess.payment.exception.GatewayTimeoutException;
import com.giki.mess.payment.exception.InvalidCallbackException;
import com.giki.mess.payment.exception.UnsupportedPaymentChannelException;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.gateway.AbstractSignedProcessor;
import com.giki.mess.payment.gateway.BankTransferProcessor;
import com.giki.mess.payment.gateway.EasyPaisaProcessor;
import com.giki.mess.payment.gateway.JazzCashProcessor;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.payment.model.TransactionStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Payment settlement and reconciliation (FR-003 / PY-01, QA-02, QA-05, R-02, R-03). */
@DisplayName("PaymentService (FR-003 / PY-01)")
class PaymentServiceTest {

    static final String STUDENT = "2023294";
    static final String BILL_ID = "BILL-2023294-2026-11";
    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    JazzCashProcessor jazz = new JazzCashProcessor("jazz-secret", CLOCK);
    EasyPaisaProcessor easy = new EasyPaisaProcessor("easy-secret", CLOCK);
    BankTransferProcessor bank = new BankTransferProcessor("bank-secret", CLOCK);

    InMemoryPaymentRepository payments;
    InMemoryBillRepository bills;
    InMemoryLedgerRepository ledgers;
    StudentLedger ledger;
    Bill bill;
    PaymentService service;

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
        PaymentProcessorFactory factory = new PaymentProcessorFactory(Map.of(
            PaymentChannel.JAZZCASH, jazz,
            PaymentChannel.EASYPAISA, easy,
            PaymentChannel.BANK_TRANSFER, bank));
        service = new PaymentServiceImpl(factory, payments, bills, ledgers);
    }

    /** Acts as the gateway: builds the signed callback for a started payment. */
    PaymentCallback callbackFor(AbstractSignedProcessor gateway, PaymentResponse started,
                                Money amount, CallbackStatus status) {
        String ref = "REF-" + started.transactionId();
        String sig = gateway.sign(started.transactionId(), STUDENT, BILL_ID, amount, status, ref, NOW);
        return new PaymentCallback(started.transactionId(), STUDENT, BILL_ID, amount, status, ref, NOW, sig);
    }

    // ── initiate ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initiating a payment stores an INITIATED transaction and acknowledges")
    void initiate_storesTransaction() {
        PaymentResponse res = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000),
            PaymentChannel.JAZZCASH, "KEY-1");

        PaymentTransaction tx = payments.findById(res.transactionId()).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.INITIATED);
        assertThat(tx.getAmount()).isEqualTo(Money.pkr(1_000));
        assertThat(res.channel()).isEqualTo(PaymentChannel.JAZZCASH);
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(4_000)); // nothing credited yet
    }

    @Test
    @DisplayName("unknown bill is rejected")
    void initiate_unknownBill() {
        assertThatExceptionOfType(BillNotFoundException.class).isThrownBy(() ->
            service.initiatePayment(STUDENT, "NOPE", Money.pkr(10), PaymentChannel.JAZZCASH, "K"));
    }

    @Test
    @DisplayName("paying someone else's bill is rejected")
    void initiate_wrongStudent() {
        assertThatExceptionOfType(com.giki.mess.payment.exception.PaymentException.class)
            .isThrownBy(() -> service.initiatePayment("2023999", BILL_ID, Money.pkr(10),
                PaymentChannel.JAZZCASH, "K"));
    }

    @Test
    @DisplayName("paying more than the outstanding amount is rejected")
    void initiate_overpayment() {
        assertThatExceptionOfType(com.giki.mess.payment.exception.PaymentException.class)
            .isThrownBy(() -> service.initiatePayment(STUDENT, BILL_ID, Money.pkr(4_001),
                PaymentChannel.JAZZCASH, "K"));
    }

    @Test
    @DisplayName("an already PAID bill cannot be paid again")
    void initiate_paidBill() {
        bill.markAsPaid();
        assertThatExceptionOfType(com.giki.mess.payment.exception.PaymentException.class)
            .isThrownBy(() -> service.initiatePayment(STUDENT, BILL_ID, Money.pkr(10),
                PaymentChannel.JAZZCASH, "K"));
    }

    @Test
    @DisplayName("reusing an idempotency key is rejected as a duplicate")
    void initiate_duplicateKey() {
        service.initiatePayment(STUDENT, BILL_ID, Money.pkr(100), PaymentChannel.JAZZCASH, "SAME");

        assertThatExceptionOfType(DuplicateTransactionException.class).isThrownBy(() ->
            service.initiatePayment(STUDENT, BILL_ID, Money.pkr(100), PaymentChannel.JAZZCASH, "SAME"));
    }

    @Test
    @DisplayName("null channel is rejected")
    void initiate_nullChannel() {
        assertThatExceptionOfType(UnsupportedPaymentChannelException.class).isThrownBy(() ->
            service.initiatePayment(STUDENT, BILL_ID, Money.pkr(100), null, "K"));
    }

    @Test
    @DisplayName("gateway timeout marks the transaction FAILED and leaves ledger and bill untouched")
    void initiate_gatewayTimeout_isSafe() {
        jazz.setSimulatedOutage(true);

        assertThatExceptionOfType(GatewayTimeoutException.class).isThrownBy(() ->
            service.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000), PaymentChannel.JAZZCASH, "KEY-T"));

        PaymentTransaction tx = payments.findByIdempotencyKey("KEY-T").orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(4_000));
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    // ── settle ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("successful callback: transaction RECONCILED, bill PAID, ledger cleared")
    void settle_success() {
        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(4_000),
            PaymentChannel.JAZZCASH, "KEY-1");

        long begin = System.nanoTime();
        PaymentTransaction tx = service.settlePayment(
            callbackFor(jazz, started, Money.pkr(4_000), CallbackStatus.SUCCESS));
        Duration took = Duration.ofNanos(System.nanoTime() - begin);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ledger.getBalance()).isEqualTo(Money.ZERO);
        assertThat(took).isLessThan(Duration.ofSeconds(5)); // QA-02: recorded within 5 s
    }

    @Test
    @DisplayName("partial payment leaves the bill PARTIALLY_PAID with the remaining balance")
    void settle_partial() {
        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_500),
            PaymentChannel.EASYPAISA, "KEY-2");

        service.settlePayment(callbackFor(easy, started, Money.pkr(1_500), CallbackStatus.SUCCESS));

        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(2_500));
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(2_500));
    }

    @Test
    @DisplayName("bank transfer settles the same way")
    void settle_bankTransfer() {
        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(4_000),
            PaymentChannel.BANK_TRANSFER, "KEY-3");

        PaymentTransaction tx = service.settlePayment(
            callbackFor(bank, started, Money.pkr(4_000), CallbackStatus.SUCCESS));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RECONCILED);
    }

    @Test
    @DisplayName("a FAILED callback marks the transaction FAILED and does not touch balances")
    void settle_failedCallback() {
        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(4_000),
            PaymentChannel.JAZZCASH, "KEY-4");

        PaymentTransaction tx = service.settlePayment(
            callbackFor(jazz, started, Money.pkr(4_000), CallbackStatus.FAILED));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(4_000));
        assertThat(bill.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    @DisplayName("a forged callback is rejected and nothing changes")
    void settle_forgedCallback() {
        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(4_000),
            PaymentChannel.JAZZCASH, "KEY-5");
        PaymentCallback good = callbackFor(jazz, started, Money.pkr(4_000), CallbackStatus.SUCCESS);
        PaymentCallback forged = new PaymentCallback(good.transactionId(), good.studentId(),
            good.billId(), good.amount(), good.status(), good.gatewayReference(), good.issuedAt(), "bad");

        assertThatExceptionOfType(InvalidCallbackException.class)
            .isThrownBy(() -> service.settlePayment(forged));

        assertThat(payments.findById(started.transactionId()).orElseThrow().getStatus())
            .isEqualTo(TransactionStatus.INITIATED);
        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(4_000));
    }

    @Test
    @DisplayName("a correctly signed callback with a different amount than requested is rejected")
    void settle_amountMismatch() {
        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000),
            PaymentChannel.JAZZCASH, "KEY-6");

        assertThatExceptionOfType(InvalidCallbackException.class).isThrownBy(() ->
            service.settlePayment(callbackFor(jazz, started, Money.pkr(4_000), CallbackStatus.SUCCESS)));

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(4_000));
    }

    @Test
    @DisplayName("a callback for an unknown transaction is rejected")
    void settle_unknownTransaction() {
        PaymentResponse ghost = new PaymentResponse("TX-GHOST", PaymentChannel.JAZZCASH, "x");

        assertThatExceptionOfType(InvalidCallbackException.class).isThrownBy(() ->
            service.settlePayment(callbackFor(jazz, ghost, Money.pkr(10), CallbackStatus.SUCCESS)));
    }

    @Test
    @DisplayName("a duplicate callback is rejected and the ledger is credited only once (R-02)")
    void settle_duplicateCallback_creditsOnce() {
        PaymentResponse started = service.initiatePayment(STUDENT, BILL_ID, Money.pkr(1_000),
            PaymentChannel.JAZZCASH, "KEY-7");
        PaymentCallback cb = callbackFor(jazz, started, Money.pkr(1_000), CallbackStatus.SUCCESS);

        service.settlePayment(cb);
        assertThatExceptionOfType(DuplicateTransactionException.class)
            .isThrownBy(() -> service.settlePayment(cb));

        assertThat(ledger.getBalance()).isEqualTo(Money.pkr(3_000)); // 4,000 - 1,000, not 2,000
        assertThat(bill.getNetPayable()).isEqualTo(Money.pkr(3_000));
    }
}
