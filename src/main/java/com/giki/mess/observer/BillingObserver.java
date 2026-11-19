package com.giki.mess.observer;

import com.giki.mess.billing.service.BillingService;

/**
 * Billing's reaction to an accepted scan: record the meal on the student's ledger.
 * Attendance never calls Billing directly, so a slow or failing ledger cannot hold up
 * the turnstile (that was the rejected synchronous alternative).
 */
public final class BillingObserver implements AttendanceObserver {

    private final BillingService billingService;

    /**
     * Creates an observer that records scanned meals through the billing service.
     */
    public BillingObserver(BillingService billingService) {
        if (billingService == null) {
            throw new IllegalArgumentException("billingService cannot be null");
        }
        this.billingService = billingService;
    }

    @Override
    public void onAttendanceRecorded(ScanEvent event) {
        billingService.recordMealSwipe(event.studentId(), event.mealType(),
            event.timestamp(), event.eventId());
    }
}
