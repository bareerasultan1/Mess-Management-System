package com.giki.mess.payment.model;

/**
 * ENUM: PaymentChannel
 *
 * <p>Represents the supported payment methods in the GIKI Mess system.
 *
 * <p>Used by:
 *   - PaymentProcessorFactory (Day 3) to select the right gateway handler
 *   - PaymentTransaction to record which method was used
 *   - Reports and dashboards to show payment method breakdown
 */
public enum PaymentChannel {
    JAZZCASH,
    EASYPAISA,
    BANK_TRANSFER
}
