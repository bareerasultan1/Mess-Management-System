package com.giki.mess.payment.service;

import com.giki.mess.billing.exception.BillNotFoundException;
import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.payment.exception.DuplicateTransactionException;
import com.giki.mess.payment.exception.GatewayTimeoutException;
import com.giki.mess.payment.exception.InvalidCallbackException;
import com.giki.mess.payment.exception.PaymentException;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.gateway.IPaymentProcessor;
import com.giki.mess.payment.idempotency.IdempotencyManager;
import com.giki.mess.payment.idempotency.TransactionRecord;
import com.giki.mess.payment.model.CallbackStatus;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentRequest;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.payment.model.TransactionStatus;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import com.giki.mess.repository.IPaymentRepository;
import com.giki.mess.util.LogRedactor;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates a payment: start it through a gateway, then settle it when the verified
 * callback arrives. Depends only on interfaces (repositories, IPaymentProcessor via the
 * factory), so storage and gateways can be swapped without changing this class (DIP).
 */
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentServiceImpl.class);
    /** Target from requirement QA-02: a successful payment is recorded within 5 seconds. */
    public static final Duration DEFAULT_SETTLEMENT_SLA = Duration.ofSeconds(5);

    private final PaymentProcessorFactory factory;
    private final IPaymentRepository payments;
    private final IBillRepository bills;
    private final ILedgerRepository ledgers;

    private final IdempotencyManager idempotency;
    private final Duration settlementSla;

    /**
     * Creates the service with its own idempotency gate.
     */
    public PaymentServiceImpl(PaymentProcessorFactory factory, IPaymentRepository payments,
                              IBillRepository bills, ILedgerRepository ledgers) {
        this(factory, payments, bills, ledgers, new IdempotencyManager());
    }

    /**
     * Creates the service with an explicit idempotency gate (shared by every instance in tests).
     */
    public PaymentServiceImpl(PaymentProcessorFactory factory, IPaymentRepository payments,
                              IBillRepository bills, ILedgerRepository ledgers,
                              IdempotencyManager idempotency) {
        this(factory, payments, bills, ledgers, idempotency, DEFAULT_SETTLEMENT_SLA);
    }

    /**
     * Creates the service with an explicit settlement time target; a slower settlement is logged.
     */
    public PaymentServiceImpl(PaymentProcessorFactory factory, IPaymentRepository payments,
                              IBillRepository bills, ILedgerRepository ledgers,
                              IdempotencyManager idempotency, Duration settlementSla) {
        this.factory = factory;
        this.payments = payments;
        this.bills = bills;
        this.ledgers = ledgers;
        this.idempotency = idempotency;
        this.settlementSla = settlementSla;
    }

    @Override
    public PaymentResponse initiatePayment(String studentId, String billId, Money amount,
                                           PaymentChannel channel, String idempotencyKey) {
        requireValidInitiation(studentId, billId, amount, idempotencyKey);
        IPaymentProcessor processor = factory.getProcessor(channel);

        Bill bill = bills.findById(billId).orElseThrow(() -> new BillNotFoundException(billId));
        requirePayable(bill, studentId, amount);
        if (payments.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new DuplicateTransactionException(idempotencyKey);
        }

        PaymentTransaction tx = new PaymentTransaction(UUID.randomUUID().toString(), studentId,
            billId, amount, channel, idempotencyKey);
        payments.save(tx);
        try {
            PaymentResponse response = processor.initiatePayment(new PaymentRequest(
                tx.getTransactionId(), studentId, billId, amount, channel, idempotencyKey));
            LOG.info("Payment started: tx={} channel={} student={}", tx.getTransactionId(),
                channel, LogRedactor.maskStudentId(studentId));
            return response;
        } catch (GatewayTimeoutException e) {
            tx.markFailed();
            payments.save(tx);
            LOG.warn("Payment failed, gateway unavailable: tx={} channel={}", tx.getTransactionId(), channel);
            throw e;
        }
    }

    private static void requireValidInitiation(String studentId, String billId, Money amount,
                                               String idempotencyKey) {
        if (studentId == null || studentId.isBlank() || billId == null || billId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("studentId, billId and idempotencyKey are required");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
    }

    /** The bill must belong to the student, not be paid already, and be owed at least this amount. */
    private static void requirePayable(Bill bill, String studentId, Money amount) {
        if (!bill.getStudentId().equals(studentId)) {
            throw new PaymentException("BILL_OWNER_MISMATCH", "Bill does not belong to this student");
        }
        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new PaymentException("BILL_ALREADY_PAID", "Bill is already paid: " + bill.getBillId());
        }
        if (amount.isGreaterThan(bill.getNetPayable())) {
            throw new PaymentException("AMOUNT_EXCEEDS_DUE",
                "Amount " + amount + " exceeds the outstanding " + bill.getNetPayable());
        }
    }

    /** The callback must describe exactly the payment that was started (student, bill and amount). */
    private static void requireMatchesOriginalRequest(PaymentTransaction tx, PaymentCallback callback) {
        if (!tx.getStudentId().equals(callback.studentId())
                || !tx.getBillId().equals(callback.billId())
                || !tx.getAmount().equals(callback.amount())) {
            throw new InvalidCallbackException("Callback does not match the original payment request");
        }
    }

    // The timer must start at method entry, so its declaration is intentionally far from its use.
    @SuppressWarnings("checkstyle:variabledeclarationusagedistance")
    @Override
    public PaymentTransaction settlePayment(PaymentCallback callback) {
        long startedAt = System.nanoTime();
        if (callback == null) {
            throw new InvalidCallbackException("Callback is missing");
        }
        PaymentTransaction tx = payments.findById(callback.transactionId())
            .orElseThrow(() -> new InvalidCallbackException("Unknown transaction"));
        requireMatchesOriginalRequest(tx, callback);
        factory.getProcessor(tx.getChannel()).reconcile(callback); // signature + freshness

        // Atomic gate: of all concurrent copies of this callback exactly one gets past here.
        TransactionRecord claim = new TransactionRecord(tx.getTransactionId(), Instant.now());
        if (!idempotency.tryAcquire(tx.getIdempotencyKey(), claim)) {
            LOG.info("Duplicate callback ignored: tx={}", tx.getTransactionId());
            throw new DuplicateTransactionException(tx.getIdempotencyKey());
        }
        // Second guard for after a restart, when the in-memory gate is empty.
        if (tx.getStatus() != TransactionStatus.INITIATED) {
            LOG.info("Already-settled transaction ignored: tx={}", tx.getTransactionId());
            throw new DuplicateTransactionException(tx.getIdempotencyKey());
        }

        if (callback.status() == CallbackStatus.FAILED) {
            tx.markFailed();
            payments.save(tx);
            return tx;
        }

        // Look everything up first so a missing record cannot leave a half-settled payment.
        // If a lookup fails nothing has changed yet, so the key is given back for a retry.
        Bill bill;
        StudentLedger ledger;
        try {
            bill = bills.findById(tx.getBillId())
                .orElseThrow(() -> new BillNotFoundException(tx.getBillId()));
            ledger = ledgers.findByStudentId(tx.getStudentId())
                .orElseThrow(() -> new LedgerNotFoundException(tx.getStudentId()));
        } catch (RuntimeException e) {
            idempotency.release(tx.getIdempotencyKey(), claim);
            throw e;
        }

        tx.markSuccess();
        ledger.creditAmount(tx.getAmount());
        bill.applyPayment(tx.getAmount());   // atomic: compare and update under one lock
        tx.markReconciled();
        ledgers.save(ledger);
        bills.save(bill);
        payments.save(tx);

        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
        if (took.compareTo(settlementSla) > 0) {
            LOG.warn("Settlement exceeded the {} ms target: tx={} took={}ms",
                settlementSla.toMillis(), tx.getTransactionId(), took.toMillis());
        }
        LOG.info("Payment settled: tx={} student={} amount={}", tx.getTransactionId(),
            LogRedactor.maskStudentId(tx.getStudentId()), tx.getAmount());
        return tx;
    }
}
