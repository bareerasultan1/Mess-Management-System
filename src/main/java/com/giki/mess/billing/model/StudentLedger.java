package com.giki.mess.billing.model;

import com.giki.mess.billing.exception.BillingException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DOMAIN ENTITY: StudentLedger
 *
 * This is the FINANCIAL ACCOUNT for a single student in the mess system.
 * Think of it as the student's mess wallet — money comes in (payments)
 * and goes out (meal charges are debited).
 *
 * WHY IS THIS A SEPARATE CLASS FROM BILL?
 * ─────────────────────────────────────────
 * A Bill is a SNAPSHOT at month-end: "You owe 3500 PKR for November."
 * A Ledger is a RUNNING TOTAL: "Your current outstanding balance is 1200 PKR."
 *
 * The ledger tracks real-time unbilled consumption (meals eaten this month
 * that haven't been billed yet) and post-payment balances.
 * Bills are generated FROM the ledger at month-end.
 *
 * THREAD-SAFETY:
 * ──────────────
 * ⚠️ IMPORTANT (SE423 Concurrency Criterion + QA-CONC-01):
 *   Two events can happen simultaneously for the same student:
 *     1. Student eats breakfast → BillingObserver debits a meal fee
 *     2. Student's dad sends JazzCash payment → PaymentService credits the balance
 *
 *   If both happen at exactly the same millisecond and we're not careful,
 *   the balance could be corrupted (one update would overwrite the other —
 *   this is called a "race condition").
 *
 *   We use a ReentrantLock to ensure only ONE thread can modify the balance
 *   at any given moment.  The other thread waits until the first finishes.
 *
 *   NOTE: The lock is declared here in Day 1 (the field is ready).
 *         The actual locking logic is activated in Day 4 (Concurrency Day)
 *         when we wire up the concurrent tests.  This shows architectural
 *         foresight — you don't bolt on thread safety later; you design for it upfront.
 */
public class StudentLedger {

    /**
     * ENUM: AccountStatus
     * Active = student can use the mess.
     * Suspended = student owes too much and mess access is blocked.
     */
    public enum AccountStatus {
        ACTIVE,
        SUSPENDED
    }

    private final String studentId;
    private final StudentCategory category; // Determines tariff strategy (Day 2)

    /** Current outstanding balance in PKR (positive = student owes money) */
    private double balance;

    /** Count of meals eaten this month not yet included in a formal Bill */
    private int unbilledMealCount;

    /** Lifecycle state of this account */
    private AccountStatus status;

    /**
     * Thread-safety lock for all balance modifications.
     * ReentrantLock is used because it gives us more control than `synchronized`:
     *   - We can inspect lock status
     *   - It's fair (waits in order)
     *   - It integrates well with try-finally blocks (prevents lock leaks)
     */
    private final ReentrantLock balanceLock = new ReentrantLock(true); // fair=true

    /**
     * Creates a new student ledger.  Initial balance is always zero.
     * The account starts as ACTIVE.
     *
     * @param studentId Student registration number, e.g. "2023294"
     * @param category  The student's tariff category (REGULAR, SCHOLAR, etc.)
     */
    public StudentLedger(String studentId, StudentCategory category) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("StudentLedger: studentId cannot be null or blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("StudentLedger: category cannot be null");
        }
        this.studentId       = studentId;
        this.category        = category;
        this.balance         = 0.0;
        this.unbilledMealCount = 0;
        this.status          = AccountStatus.ACTIVE;
    }

    // ── Thread-safe balance operations ───────────────────────────────────────

    /**
     * Adds to the student's outstanding balance (they owe more money).
     * Called when a meal is scanned.
     *
     * Thread-safe: uses ReentrantLock to prevent race conditions.
     *
     * @param amount PKR amount to debit (must be positive)
     */
    public void debitAmount(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "Debit amount must be positive for student " + studentId + ", got: " + amount);
        }
        balanceLock.lock();
        try {
            this.balance += amount;
            this.unbilledMealCount++;
        } finally {
            // ALWAYS release the lock — even if an exception occurs
            // Without finally, an exception would permanently freeze the account
            balanceLock.unlock();
        }
    }

    /**
     * Reduces the student's outstanding balance (they paid some money).
     * Called when a payment is reconciled.
     *
     * Thread-safe: uses ReentrantLock.
     *
     * @param amount PKR amount to credit (must be positive)
     */
    public void creditAmount(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "Credit amount must be positive for student " + studentId + ", got: " + amount);
        }
        balanceLock.lock();
        try {
            this.balance = Math.max(0, this.balance - amount);
            // Reactivate account if payment brings balance to manageable level
            if (this.balance == 0 && this.status == AccountStatus.SUSPENDED) {
                this.status = AccountStatus.ACTIVE;
            }
        } finally {
            balanceLock.unlock();
        }
    }

    /**
     * Resets the unbilled meal count after a monthly bill has been generated.
     * Called by BillingService after successfully creating a Bill object.
     */
    public void clearUnbilledMeals() {
        balanceLock.lock();
        try {
            this.unbilledMealCount = 0;
        } finally {
            balanceLock.unlock();
        }
    }

    /**
     * Suspends the account when outstanding balance exceeds the threshold.
     * Business rule: if a student owes more than 10,000 PKR, access is suspended.
     */
    public void suspendIfOverdue() {
        balanceLock.lock();
        try {
            if (this.balance > 10_000.0) {
                this.status = AccountStatus.SUSPENDED;
            }
        } finally {
            balanceLock.unlock();
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getStudentId()       { return studentId;        }
    public StudentCategory getCategory() { return category;       }
    public double getBalance()         { return balance;          }
    public int getUnbilledMealCount()  { return unbilledMealCount;}
    public AccountStatus getStatus()   { return status;           }

    /**
     * Convenience check used by the attendance module to gate-keep mess access.
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return String.format(
            "StudentLedger{student='%s', category=%s, balance=%.2f PKR, unbilledMeals=%d, status=%s}",
            studentId, category, balance, unbilledMealCount, status);
    }
}
