package com.giki.mess.billing.model;

/**
 * ENUM: StudentCategory
 *
 * <p>Determines which tariff strategy applies to the student (Strategy Pattern — Day 2).
 *
 * <p>Why this matters for billing:
 *   - REGULAR students pay the full standard rate
 *   - SCHOLAR students get a 25% discount on net meal costs
 *   - SUBSIDIZED students pay a reduced daily rate (e.g. need-based subsidy)
 *   - FACULTY_GUEST pays a premium rate (different tariff entirely)
 */
public enum StudentCategory {
    REGULAR,
    SCHOLAR,
    SUBSIDIZED,
    FACULTY_GUEST
}
