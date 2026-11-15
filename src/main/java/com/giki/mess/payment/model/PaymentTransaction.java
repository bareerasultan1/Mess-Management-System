package com.giki.mess.payment.model;

import com.giki.mess.common.Money;
import java.time.LocalDateTime;

/**
 * DOMAIN ENTITY: PaymentTransaction
 *
 * Records ONE payment attempt by a student — analogous to a bank transaction record.
 * Every payment attempt creates a new PaymentTransaction regardless of outcome.
 *
 * KEY FIELDS:
 * ───────────
 *  transactionId   → Unique ID (UUID) generated when student clicks "Pay"
 *  idempotencyKey  → A deduplification key we give to the gateway.
 *                    If JazzCash sends the same callback twice, the idempotencyKey
 *                    lets us detect and reject the duplicate.
 *                    (Solves Risk R-02, enables QA-CONC-01)
 *
 * IMMUTABILITY DECISION:
 *   The transaction record itself (who paid, how much, when) is IMMUTABLE.
 *   Only the status can change (INITIATED → SUCCESS → RECONCILED).
 *   This is similar to how bank records work: the transaction amount never
 *   changes after it's recorded — you just add a new status entry.
 *
 * HOW IT CONNECTS TO THE SYSTEM:
 *   Student clicks "Pay 3500 PKR via JazzCash"
 *     → PaymentService creates a PaymentTransaction (status = INITIATED)
 *     → JazzCash processes the payment
 *     → JazzCash sends a callback to our server
 *     → PaymentService finds this transaction by transactionId
 *     → Updates status to SUCCESS / FAILED
 *     → If SUCCESS: updates StudentLedger + Bill, sets status to RECONCILED
 */
public class PaymentTransaction {

    /** Maximum single online payment (decision: 50,000 PKR, used by validation and BVA tests). */
    public static final Money MAX_AMOUNT = Money.pkr(50_000);

    private final String transactionId;
    private final String studentId;
    private final String billId;
    private final Money amount;             // Amount paid
    private final PaymentChannel channel;  // JazzCash / EasyPaisa / Bank Transfer
    private final String idempotencyKey;   // Unique key for duplicate detection
    private final LocalDateTime createdAt; // When the transaction was initiated

    // Only status is mutable — everything else is sealed at creation
    private TransactionStatus status;

    /**
     * Creates a new payment transaction at the moment the student initiates payment.
     *
     * @param transactionId   System-generated UUID
     * @param studentId       Student registration number
     * @param billId          The bill this payment is meant to settle
     * @param amount          Amount in PKR (must be > 0)
     * @param channel         Which payment method
     * @param idempotencyKey  Unique key for this specific payment attempt
     */
    public PaymentTransaction(String transactionId, String studentId, String billId,
                              Money amount, PaymentChannel channel, String idempotencyKey) {
        this(transactionId, studentId, billId, amount, channel, idempotencyKey,
            LocalDateTime.now(), TransactionStatus.INITIATED);
    }

    private PaymentTransaction(String transactionId, String studentId, String billId,
                               Money amount, PaymentChannel channel, String idempotencyKey,
                               LocalDateTime createdAt, TransactionStatus status) {
        // Defensive validation
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId cannot be null or blank");
        }
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("studentId cannot be null or blank");
        }
        if (billId == null || billId.isBlank()) {
            throw new IllegalArgumentException("billId cannot be null or blank");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException(
                "Payment amount must be positive, got: " + amount);
        }
        if (amount.isGreaterThan(MAX_AMOUNT)) {
            throw new IllegalArgumentException(
                "Payment amount exceeds maximum limit (" + MAX_AMOUNT + "): " + amount);
        }
        if (channel == null) {
            throw new IllegalArgumentException("PaymentChannel cannot be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be null or blank");
        }

        this.transactionId   = transactionId;
        this.studentId       = studentId;
        this.billId          = billId;
        this.amount          = amount;
        this.channel         = channel;
        this.idempotencyKey  = idempotencyKey;
        if (createdAt == null || status == null) {
            throw new IllegalArgumentException("createdAt and status cannot be null");
        }
        this.createdAt       = createdAt;
        this.status          = status;
    }

    /** Rebuilds a stored transaction (used by the database layer only). */
    public static PaymentTransaction restore(String transactionId, String studentId, String billId,
                                             Money amount, PaymentChannel channel,
                                             String idempotencyKey, LocalDateTime createdAt,
                                             TransactionStatus status) {
        return new PaymentTransaction(transactionId, studentId, billId, amount, channel,
            idempotencyKey, createdAt, status);
    }

    // ── Controlled status transitions ─────────────────────────────────────────

    /** Called when the gateway confirms payment was successful. */
    public void markSuccess() {
        this.status = TransactionStatus.SUCCESS;
    }

    /** Called when the gateway reports failure (rejected, expired, etc.) */
    public void markFailed() {
        this.status = TransactionStatus.FAILED;
    }

    /**
     * Called after ledger and bill have been updated.
     * This is the final settled state.
     */
    public void markReconciled() {
        this.status = TransactionStatus.RECONCILED;
    }

    /** Called by IdempotencyManager when a duplicate callback is detected. */
    public void markDuplicate() {
        this.status = TransactionStatus.DUPLICATE;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getTransactionId()     { return transactionId;  }
    public String getStudentId()         { return studentId;      }
    public String getBillId()            { return billId;         }
    public Money getAmount()             { return amount;         }
    public PaymentChannel getChannel()   { return channel;        }
    public String getIdempotencyKey()    { return idempotencyKey; }
    public LocalDateTime getCreatedAt()  { return createdAt;      }
    public TransactionStatus getStatus() { return status;         }

    @Override
    public String toString() {
        return String.format(
            "PaymentTransaction{id='%s', student='%s', amount=%s, channel=%s, status=%s}",
            transactionId, studentId, amount, channel, status);
    }
}
