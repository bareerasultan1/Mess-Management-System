package com.giki.mess.observer;

import com.giki.mess.billing.model.MealType;
import java.time.LocalDateTime;

/**
 * Immutable event published by the Attendance module when a scan was accepted.
 * Being an immutable record, it can be shared between threads without any locking.
 * The eventId is unique per physical scan, so a replayed event can be recognised.
 */
public record ScanEvent(String eventId, String studentId, LocalDateTime timestamp,
                        MealType mealType, String terminalId) {

    public ScanEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("ScanEvent eventId cannot be null or blank");
        }
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("ScanEvent studentId cannot be null or blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("ScanEvent timestamp cannot be null");
        }
        if (mealType == null) {
            throw new IllegalArgumentException("ScanEvent mealType cannot be null");
        }
        if (terminalId == null || terminalId.isBlank()) {
            throw new IllegalArgumentException("ScanEvent terminalId cannot be null or blank");
        }
    }
}
