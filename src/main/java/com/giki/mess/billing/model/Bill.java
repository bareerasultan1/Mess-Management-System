package com.giki.mess.billing.model;

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
 *  baseAmount   → Total PKR charged for all attended meals (before adjustments)
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

    // Monetary amounts (in PKR)
    private final double baseAmount;
    private final double rebateAmount;
    private final double fineAmount;
    private final double discountAmount;
    private double netPayable;

    // Mutable state — only changes via controlled methods below
    private PaymentStatus paymentStatus;

    /**
     * Full constructor — all monetary amounts are passed at creation time.
     * Called by BillingService.calculateMonthlyBill() at month-end.
     */
    public Bill(String billId, String studentId, int month, int year,
                LocalDate dueDate, double baseAmount, double rebateAmount,
                double fineAmount, double discountAmount, List<BillItem> items) {

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
        if (baseAmount < 0) {
            throw new IllegalArgumentException("Base amount cannot be negative");
        }
        if (rebateAmount < 0) {
            throw new IllegalArgumentException("Rebate amount cannot be negative");
        }
        if (fineAmount < 0) {
            throw new IllegalArgumentException("Fine amount cannot be negative");
        }
        if (discountAmount < 0) {
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
        this.netPayable     = Math.max(0, baseAmount - rebateAmount + fineAmount - discountAmount);
        this.paymentStatus  = PaymentStatus.UNPAID;
    }

    // ── Controlled state transitions ─────────────────────────────────────────

    /**
     * Called by PaymentService when a successful payment is reconciled.
     * Marks the bill as fully paid.
     */
    public void markAsPaid() {
        this.paymentStatus = PaymentStatus.PAID;
        this.netPayable    = 0.0; // All settled
    }

    /**
     * Called by a scheduled job when the due date passes and bill is not paid.
     */
    public void markAsOverdue() {
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
    public void applyPartialPayment(double amountPaid) {
        if (amountPaid <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive, got: " + amountPaid);
        }
        this.netPayable = Math.max(0, this.netPayable - amountPaid);
        if (this.netPayable == 0) {
            this.paymentStatus = PaymentStatus.PAID;
        } else {
            this.paymentStatus = PaymentStatus.PARTIALLY_PAID;
        }
    }

    // ── Getters (read-only access from outside) ──────────────────────────────

    public String getBillId()          { return billId;         }
    public String getStudentId()       { return studentId;      }
    public int getMonth()              { return month;          }
    public int getYear()               { return year;           }
    public LocalDate getDueDate()      { return dueDate;        }
    public double getBaseAmount()      { return baseAmount;     }
    public double getRebateAmount()    { return rebateAmount;   }
    public double getFineAmount()      { return fineAmount;     }
    public double getDiscountAmount()  { return discountAmount; }
    public double getNetPayable()      { return netPayable;     }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }

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
            "Bill{id='%s', student='%s', period=%d/%d, net=%.2f PKR, status=%s}",
            billId, studentId, month, year, netPayable, paymentStatus);
    }
}
