package com.giki.mess.billing.model;

/**
 * ENUM: MealType
 *
 * Represents the three daily meal sessions in the GIKI Mess.
 * Each session has a different tariff rate (Breakfast is cheaper, Dinner is most expensive).
 *
 * WHY AN ENUM AND NOT A STRING?
 * ──────────────────────────────
 * If you store meal types as Strings, you might accidentally store "Brekfast" or "dinner"
 * (lowercase) and your billing logic would miss-calculate rates.
 * An enum makes invalid values impossible at compile time.
 */
public enum MealType {
    BREAKFAST,
    LUNCH,
    DINNER
}
