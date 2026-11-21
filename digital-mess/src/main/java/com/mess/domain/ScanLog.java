package com.mess.domain;

import java.time.Instant;
import java.time.LocalDate;

/** One accepted mess entry or exit. Immutable. */
public record ScanLog(Long id, long userId, ScanType scanType, MealType mealType,
                      Instant scannedAt, LocalDate scanDate, String deviceId) {

    public ScanLog withId(long newId) {
        return new ScanLog(newId, userId, scanType, mealType, scannedAt, scanDate, deviceId);
    }
}
