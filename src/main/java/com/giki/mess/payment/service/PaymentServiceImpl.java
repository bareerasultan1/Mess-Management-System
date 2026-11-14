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
    private static final Duration SETTLEMENT_SLA = Duration.ofSeconds(5);

    private final PaymentProcessorFactory factory;
    private final IPaymentRepository payments;
    private final IBillRepository bills;
    private final ILedgerRepository ledgers;

    private final IdempotencyManager idempotency;

    public PaymentServiceImpl(PaymentProcessorFactory factory, IPaymentRepository payments,
                              IBillRepository bills, ILedgerRepository ledgers) {
        this(factory, payments, bills, ledgers, new IdempotencyManager());
    }

    public PaymentServiceImpl(PaymentProcessorFactory factory, IPaymentRepository payments,
                              IBillRepository bills, ILedgerRepository ledgers,
                              IdempotencyManager idempotency) {
        this.factory = factory;
        this.payments = payments;
        this.bills = bills;
        this.ledgers = ledgers;
        this.idempotency = idempotency;
    }

    @Override
    public PaymentResponse initiatePayment(String studentId, String billId, Money amount,
                                           PaymentChannel channel, String idempotencyKey) {
        if (studentId == null || studentId.isBlank() || billId == null || billId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("studentId, billId and idempotencyKey are required");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        IPaymentProcessor processor = factory.getProcessor(channel);

        Bill bill = bills.findById(billId).orElseThrow(() -> new BillNotFoundException(billId));
        if (!bill.getStudentId().equals(studentId)) {
            throw new PaymentException("BILL_OWNER_MISMATCH", "Bill does not belong to this student");
        }
        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new PaymentException("BILL_ALREADY_PAID", "Bill is already paid: " + billId);
        }
        if (amount.isGreaterThan(bill.getNetPayable())) {
            throw new PaymentException("AMOUNT_EXCEEDS_DUE",
                "Amount " + amount + " exceeds the outstanding " + bill.getNetPayable());
        }
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

    @Override
    public PaymentTransaction settlePayment(PaymentCallback callback) {
        long startedAt = System.nanoTime();
        if (callback == null) {
            throw new InvalidCallbackException("Callback is missing");
        }
        PaymentTransaction tx = payments.findById(callback.transactionId())
            .orElseThrow(() -> new InvalidCallbackException("Unknown transaction"));
        if (!tx.getStudentId().equals(callback.studentId())
                || !tx.getBillId().equals(callback.billId())
                || !tx.getAmount().equals(callback.amount())) {
            throw new InvalidCallbackException("Callback does not match the original payment request");
        }
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
        if (tx.getAmount().compareTo(bill.getNetPayable()) >= 0) {
            bill.markAsPaid();
        } else {
            bill.applyPartialPayment(tx.getAmount());
        }
        tx.markReconciled();
        ledgers.save(ledger);
        bills.save(bill);
        payments.save(tx);

        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);
        if (took.compareTo(SETTLEMENT_SLA) > 0) {
            LOG.warn("Settlement exceeded the 5 s target: tx={} took={}ms",
                tx.getTransactionId(), took.toMillis());
        }
        LOG.info("Payment settled: tx={} student={} amount={}", tx.getTransactionId(),
            LogRedactor.maskStudentId(tx.getStudentId()), tx.getAmount());
        return tx;
    }
}
