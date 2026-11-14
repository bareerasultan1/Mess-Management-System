package com.giki.mess.billing.model;

import com.giki.mess.common.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DOMAIN ENTITY: Bill
 *
 * Represents the MONTHLY SUMMARY invoice generated for a single student.
 * Think of it like a monthly electricity bill — it shows you the totals
 * and breakdown, not each individual unit consumed (that's BillItem's job).
 *
 * Fields explanation:
 * ───────────────────
 *  baseAmount   → Total money charged for all attended meals (before adjustments)
 *  rebateAmount → PKR deducted for registered absences of >= 3 consecutive days
 *  fineAmount   → PKR added as penalty if bill is not paid by due date (10th)
 *  discountAmount → PKR deducted for scholarship students (25% of net meals)
 *  netPayable   → Final amount = baseAmount - rebateAmount + fineAmount - discountAmount
 *
 * MUTABILITY:
 *   Unlike BillItem, a Bill CAN change state.  Why?  Because:
 *     - A bill starts as UNPAID
 *     - Later it becomes PAID (when payment settles)
 *     - Or OVERDUE (if due date passes)
 *   The bill itself (amounts) is sealed at generation time, but its
 *   paymentStatus and netPayable get updated through controlled methods only.
 */
public class Bill {

    private final String billId;
    private final String studentId;
    private final int month;         // 1–12
    private final int year;          // e.g. 2026
    private final LocalDate dueDate; // Typically the 10th of the following month
    private final List<BillItem> items; // The itemized breakdown (audit trail)

    // Monetary amounts (Money = exact whole paisa, never floating point)
    private final Money baseAmount;
    private final Money rebateAmount;
    private final Money fineAmount;
    private final Money discountAmount;
    private Money netPayable;

    // Mutable state — only changes via controlled methods below
    private PaymentStatus paymentStatus;

    /**
     * Full constructor — all monetary amounts are passed at creation time.
     * Called by BillingService.calculateMonthlyBill() at month-end.
     */
    public Bill(String billId, String studentId, int month, int year,
                LocalDate dueDate, Money baseAmount, Money rebateAmount,
                Money fineAmount, Money discountAmount, List<BillItem> items) {

        // Defensive validation
        if (billId == null || billId.isBlank()) {
            throw new IllegalArgumentException("Bill ID cannot be null or blank");
        }
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be null or blank");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
        }
        if (year < 2020) {
            throw new IllegalArgumentException("Year seems invalid: " + year);
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date cannot be null");
        }
        if (baseAmount == null || rebateAmount == null
                || fineAmount == null || discountAmount == null) {
            throw new IllegalArgumentException("Monetary amounts cannot be null");
        }
        if (items == null) {
            throw new IllegalArgumentException("Bill items list cannot be null");
        }
        if (baseAmount.isNegative()) {
            throw new IllegalArgumentException("Base amount cannot be negative");
        }
        if (rebateAmount.isNegative()) {
            throw new IllegalArgumentException("Rebate amount cannot be negative");
        }
        if (fineAmount.isNegative()) {
            throw new IllegalArgumentException("Fine amount cannot be negative");
        }
        if (discountAmount.isNegative()) {
            throw new IllegalArgumentException("Discount amount cannot be negative");
        }

        this.billId         = billId;
        this.studentId      = studentId;
        this.month          = month;
        this.year           = year;
        this.dueDate        = dueDate;
        this.baseAmount     = baseAmount;
        this.rebateAmount   = rebateAmount;
        this.fineAmount     = fineAmount;
        this.discountAmount = discountAmount;
        this.items          = new ArrayList<>(items); // Defensive copy — caller can't mutate our list

        // Calculate net payable: base - rebate + fine - discount, clamped at 0 minimum
        this.netPayable     = baseAmount.minus(rebateAmount).plus(fineAmount).minus(discountAmount).floorAtZero();
        this.paymentStatus  = PaymentStatus.UNPAID;
    }

    // ── Controlled state transitions ─────────────────────────────────────────

    /**
     * Called by PaymentService when a successful payment is reconciled.
     * Marks the bill as fully paid.
     */
    public synchronized void markAsPaid() {
        this.paymentStatus = PaymentStatus.PAID;
        this.netPayable    = Money.ZERO; // All settled
    }

    /**
     * Called by a scheduled job when the due date passes and bill is not paid.
     */
    public synchronized void markAsOverdue() {
        if (this.paymentStatus == PaymentStatus.UNPAID
                || this.paymentStatus == PaymentStatus.PARTIALLY_PAID) {
            this.paymentStatus = PaymentStatus.OVERDUE;
        }
    }

    /**
     * Called when a partial payment is received.
     * Reduces the net payable balance.
     *
     * @param amountPaid The amount received in this payment (PKR)
     */
    public synchronized void applyPartialPayment(Money amountPaid) {
        if (amountPaid == null || !amountPaid.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be positive, got: " + amountPaid);
        }
        this.netPayable = this.netPayable.minus(amountPaid).floorAtZero();
        if (this.netPayable.isZero()) {
            this.paymentStatus = PaymentStatus.PAID;
        } else {
            this.paymentStatus = PaymentStatus.PARTIALLY_PAID;
        }
    }

    /**
     * Applies a settled payment in ONE atomic step: PAID if it covers what is still owed,
     * otherwise PARTIALLY_PAID. Doing the comparison and the update under one lock is what
     * stops two simultaneous payments from both reading the same old balance (lost update).
     */
    public synchronized void applyPayment(Money amountPaid) {
        if (amountPaid == null || !amountPaid.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be positive, got: " + amountPaid);
        }
        if (amountPaid.compareTo(netPayable) >= 0) {
            markAsPaid();
        } else {
            applyPartialPayment(amountPaid);
        }
    }

    // ── Getters (read-only access from outside) ──────────────────────────────

    public String getBillId()          { return billId;         }
    public String getStudentId()       { return studentId;      }
    public int getMonth()              { return month;          }
    public int getYear()               { return year;           }
    public LocalDate getDueDate()      { return dueDate;        }
    public Money getBaseAmount()      { return baseAmount;     }
    public Money getRebateAmount()    { return rebateAmount;   }
    public Money getFineAmount()      { return fineAmount;     }
    public Money getDiscountAmount()  { return discountAmount; }
    public synchronized Money getNetPayable() { return netPayable; }
    public synchronized PaymentStatus getPaymentStatus() { return paymentStatus; }

    /**
     * Returns an UNMODIFIABLE view of the bill items.
     * External code can read them but cannot add/remove items from the list.
     * (Defensive programming)
     */
    public List<BillItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public String toString() {
        return String.format(
            "Bill{id='%s', student='%s', period=%d/%d, net=%s, status=%s}",
            billId, studentId, month, year, netPayable, paymentStatus);
    }
}
