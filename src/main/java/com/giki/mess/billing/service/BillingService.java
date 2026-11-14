package com.giki.mess.billing.service;

import com.giki.mess.billing.model.Bill;
import java.time.LocalDate;

/** Public contract of the Billing module (FR-002). */
public interface BillingService {

    /**
     * Calculates (and stores) the monthly bill of one student.
     *
     * @param asOfDate the date the bill is evaluated on; a late fine applies if it is after the due date
     * @throws IllegalArgumentException if studentId is blank or month is not 1-12
     * @throws com.giki.mess.billing.exception.LedgerNotFoundException if the student has no ledger
     */
    Bill calculateMonthlyBill(String studentId, int month, int year, LocalDate asOfDate);
}
