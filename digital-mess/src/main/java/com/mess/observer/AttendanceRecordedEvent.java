package com.mess.observer;

import com.mess.domain.MealType;
import com.mess.domain.ScanType;

import java.time.Instant;

/** Published after a scan has been saved. Other modules (billing, alerts) can subscribe to it. */
public record AttendanceRecordedEvent(long scanLogId, long userId, String studentName,
                                      ScanType scanType, MealType mealType, Instant at) { }
