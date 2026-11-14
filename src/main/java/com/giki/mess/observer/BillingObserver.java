package com.giki.mess.observer;

import com.giki.mess.billing.service.BillingService;

/**
 * Billing's reaction to an accepted scan: record the meal on the student's ledger.
 * Attendance never calls Billing directly, so a slow or failing ledger cannot hold up
 * the turnstile (that was the rejected synchronous alternative).
 */
public class BillingObserver implements AttendanceObserver {

    private final BillingService billingService;

    public BillingObserver(BillingService billingService) {
        this.billingService = billingService;
    }

    @Override
    public void onAttendanceRecorded(ScanEvent event) {
        // skeleton: behaviour is added test-first
    }
}
