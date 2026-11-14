package com.giki.mess.billing.service;

import com.giki.mess.billing.model.MealType;
import java.time.LocalDateTime;

/**
 * One accepted meal scan, as the Billing module sees it.
 * The eventId is the unique scan event id (used so a replayed scan is never billed twice).
 */
public record MealRecord(String eventId, LocalDateTime scanTime, MealType mealType) {
}
