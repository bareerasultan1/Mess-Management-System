package com.mess.repository;

import com.mess.domain.MealType;
import com.mess.domain.ScanLog;

import java.time.LocalDate;
import java.util.Optional;

public interface IScanLogRepository {

    /** Inserts the log and returns a copy that carries the generated id. */
    ScanLog save(ScanLog log);

    Optional<ScanLog> findLastScan(long userId);

    /** True if the student already has an IN scan for this meal on this mess date. */
    boolean hasEntry(long userId, MealType mealType, LocalDate scanDate);
}
