package com.giki.mess.billing.model;

import com.giki.mess.common.Money;
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
 *   Every write AND every read goes through the lock. The concurrency tests
 *   (StudentLedgerLockingTest, ConcurrentLedgerAndPaymentTest) prove it, and
 *   docs/THREAD_SAFETY_STRATEGY.md explains the strategy.
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

    /** A student owing more than this is suspended from the mess. */
    public static final Money SUSPENSION_THRESHOLD = Money.pkr(10_000);

    private final String studentId;
    private final StudentCategory category; // Determines tariff strategy (Day 2)

    /** Current outstanding balance in PKR (positive = student owes money) */
    private Money balance;

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
        this.balance         = Money.ZERO;
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
    public void debitAmount(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException(
                "Debit amount must be positive for student " + studentId + ", got: " + amount);
        }
        balanceLock.lock();
        try {
            this.balance = this.balance.plus(amount);
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
    public void creditAmount(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException(
                "Credit amount must be positive for student " + studentId + ", got: " + amount);
        }
        balanceLock.lock();
        try {
            this.balance = this.balance.minus(amount).floorAtZero();
            // Reactivate account if payment brings balance to manageable level
            if (this.balance.isZero() && this.status == AccountStatus.SUSPENDED) {
                this.status = AccountStatus.ACTIVE;
            }
        } finally {
            balanceLock.unlock();
        }
    }

    /**
     * Applies a signed bill adjustment: positive for a fine or fee (student owes more),
     * negative for a rebate or discount (student owes less, never below zero).
     * Unlike debitAmount this does not count as an extra meal.
     */
    public void adjustBalance(Money delta) {
        if (delta == null) {
            throw new IllegalArgumentException("Adjustment cannot be null for student " + studentId);
        }
        if (delta.isZero()) {
            return;
        }
        balanceLock.lock();
        try {
            this.balance = this.balance.plus(delta).floorAtZero();
            if (this.balance.isZero() && this.status == AccountStatus.SUSPENDED) {
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
            if (this.balance.isGreaterThan(SUSPENSION_THRESHOLD)) {
                this.status = AccountStatus.SUSPENDED;
            }
        } finally {
            balanceLock.unlock();
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getStudentId()       { return studentId;        }
    public StudentCategory getCategory() { return category;       }
    // Reads take the same lock as writes so a reader always sees the latest committed value
    // (Java gives no visibility guarantee for a plain field read without synchronization).

    public Money getBalance() {
        balanceLock.lock();
        try {
            return balance;
        } finally {
            balanceLock.unlock();
        }
    }

    public int getUnbilledMealCount() {
        balanceLock.lock();
        try {
            return unbilledMealCount;
        } finally {
            balanceLock.unlock();
        }
    }

    public AccountStatus getStatus() {
        balanceLock.lock();
        try {
            return status;
        } finally {
            balanceLock.unlock();
        }
    }

    /** Consistent copy of balance, meal count and status taken in one locked step. */
    public LedgerSnapshot snapshot() {
        balanceLock.lock();
        try {
            return new LedgerSnapshot(balance, unbilledMealCount, status);
        } finally {
            balanceLock.unlock();
        }
    }

    /**
     * Convenience check used by the attendance module to gate-keep mess access.
     */
    public boolean isActive() {
        return getStatus() == AccountStatus.ACTIVE;
    }

    @Override
    public String toString() {
        LedgerSnapshot s = snapshot();
        return String.format(
            "StudentLedger{student='%s', category=%s, balance=%s, unbilledMeals=%d, status=%s}",
            studentId, category, s.balance(), s.unbilledMealCount(), s.status());
    }
}
