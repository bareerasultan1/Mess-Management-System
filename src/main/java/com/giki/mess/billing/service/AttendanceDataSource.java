package com.giki.mess.billing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * PORT: what Billing needs from the Attendance module.
 *
 * <p>Billing depends on this small interface instead of on Bareera's AttendanceTracker
 * (Dependency Inversion + low coupling). Her module can implement it later;
 * until then tests and the demo use a fake.
 */
public interface AttendanceDataSource {

    /** Accepted meal scans of one student in one month. */
    List<MealRecord> mealsFor(String studentId, int month, int year);

    /** Days in that month on which the student registered an absence. */
    Set<LocalDate> registeredAbsences(String studentId, int month, int year);
}
