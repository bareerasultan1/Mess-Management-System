package com.giki.mess.payment.model;

/**
 * ENUM: PaymentChannel
 *
 * Represents the supported payment methods in the GIKI Mess system.
 *
 * Used by:
 *   - PaymentProcessorFactory (Day 3) to select the right gateway handler
 *   - PaymentTransaction to record which method was used
 *   - Reports and dashboards to show payment method breakdown
 */
public enum PaymentChannel {
    JAZZCASH,
    EASYPAISA,
    BANK_TRANSFER
}
