package com.mess.support;

import com.mess.domain.MealType;
import com.mess.domain.ScanLog;
import com.mess.domain.ScanType;
import com.mess.repository.IScanLogRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Test double for IScanLogRepository. Each method is synchronized on its own, but
 * "find then save" is NOT atomic here, so the concurrency test really exercises
 * the lock inside AttendanceTracker.
 */
public class InMemoryScanLogRepository implements IScanLogRepository {

    private final List<ScanLog> logs = new ArrayList<>();
    private final long readDelayMillis;
    private long nextId = 1;

    public InMemoryScanLogRepository() {
        this(0);
    }

    /** A read delay widens the gap between "find last scan" and "save", exposing missing locks. */
    public InMemoryScanLogRepository(long readDelayMillis) {
        this.readDelayMillis = readDelayMillis;
    }

    @Override
    public synchronized ScanLog save(ScanLog log) {
        ScanLog saved = log.withId(nextId++);
        logs.add(saved);
        return saved;
    }

    @Override
    public Optional<ScanLog> findLastScan(long userId) {
        Optional<ScanLog> result;
        synchronized (this) {
            result = logs.stream()
                    .filter(l -> l.userId() == userId)
                    .max(Comparator.comparing(ScanLog::scannedAt).thenComparing(ScanLog::id));
        }
        pause();
        return result;
    }

    @Override
    public boolean hasEntry(long userId, MealType mealType, LocalDate scanDate) {
        boolean result;
        synchronized (this) {
            result = logs.stream().anyMatch(l -> l.userId() == userId && l.mealType() == mealType
                    && l.scanDate().equals(scanDate) && l.scanType() == ScanType.IN);
        }
        pause();
        return result;
    }

    /** Sleeps outside the monitor so other threads can read at the same time. */
    private void pause() {
        if (readDelayMillis > 0) {
            try {
                Thread.sleep(readDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized int count() {
        return logs.size();
    }
}
