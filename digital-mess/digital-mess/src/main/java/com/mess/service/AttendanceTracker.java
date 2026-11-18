package com.mess.service;

import com.mess.adapter.ScanEvent;
import com.mess.domain.MealType;
import com.mess.domain.ScanLog;
import com.mess.domain.ScanType;
import com.mess.domain.User;
import com.mess.exception.DuplicateEntryException;
import com.mess.exception.ScanRejectedException;
import com.mess.exception.ScanRejectedException.Reason;
import com.mess.observer.AttendanceRecordedEvent;
import com.mess.observer.AttendanceSubject;
import com.mess.repository.IScanLogRepository;
import com.mess.repository.IUserRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Decides whether a scan is a mess ENTRY or EXIT and records it.
 *
 * Rules
 *  1. The credential must belong to an active student.
 *  2. A second scan within the debounce time (default 5 s) is a double scan and is rejected.
 *  3. If the student's last scan today was IN, this scan is OUT (same meal as the IN).
 *  4. Otherwise it is an IN, allowed only inside a meal window, and only once per meal per day.
 *
 * Thread safety: "read last scan, decide, save" must be atomic per student, so
 * each student id has its own ReentrantLock. Different students never block each
 * other. All other fields are final and immutable. The unique index
 * uq_one_entry_per_meal in the database is a second line of defence.
 */
public class AttendanceTracker {

    public static final Duration DEFAULT_DEBOUNCE = Duration.ofSeconds(5);

    private final IUserRepository users;
    private final IScanLogRepository scanLogs;
    private final MealSchedule schedule;
    private final AttendanceSubject subject;
    private final ZoneId zone;
    private final Duration debounce;
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public AttendanceTracker(IUserRepository users, IScanLogRepository scanLogs, MealSchedule schedule,
                             AttendanceSubject subject, ZoneId zone, Duration debounce) {
        this.users = Objects.requireNonNull(users);
        this.scanLogs = Objects.requireNonNull(scanLogs);
        this.schedule = Objects.requireNonNull(schedule);
        this.subject = Objects.requireNonNull(subject);
        this.zone = Objects.requireNonNull(zone);
        this.debounce = Objects.requireNonNull(debounce);
    }

    public AttendanceTracker(IUserRepository users, IScanLogRepository scanLogs, MealSchedule schedule,
                             AttendanceSubject subject, ZoneId zone) {
        this(users, scanLogs, schedule, subject, zone, DEFAULT_DEBOUNCE);
    }

    public ScanResult processScan(ScanEvent event) {
        Objects.requireNonNull(event, "event");

        User user = users.findByCredentialId(event.credentialId())
                .orElseThrow(() -> new ScanRejectedException(Reason.UNKNOWN_CREDENTIAL,
                        "Card or fingerprint is not registered"));
        if (!user.isActive()) {
            throw new ScanRejectedException(Reason.ACCOUNT_INACTIVE, "Account is inactive");
        }
        if (!user.canScanIntoMess()) {
            throw new ScanRejectedException(Reason.NOT_A_STUDENT, "Only students can scan into the mess");
        }

        ReentrantLock lock = locks.computeIfAbsent(user.getId(), id -> new ReentrantLock());
        ScanLog saved;
        lock.lock();
        try {
            saved = decideAndSave(user, event);
        } finally {
            lock.unlock();
        }

        // Notify after the lock is released and after the data is saved.
        subject.publish(new AttendanceRecordedEvent(saved.id(), user.getId(), user.getFullName(),
                saved.scanType(), saved.mealType(), saved.scannedAt()));
        return new ScanResult(saved.scanType(), saved.mealType(), user.getFullName(), saved.scannedAt());
    }

    private ScanLog decideAndSave(User user, ScanEvent event) {
        Optional<ScanLog> last = scanLogs.findLastScan(user.getId());
        LocalDate today = event.scannedAt().atZone(zone).toLocalDate();

        if (last.isPresent() && isDoubleScan(last.get().scannedAt(), event.scannedAt())) {
            throw new ScanRejectedException(Reason.DUPLICATE_SCAN, "Duplicate scan, please wait a moment");
        }

        ScanType type;
        MealType meal;
        if (last.isPresent() && last.get().scanType() == ScanType.IN && last.get().scanDate().equals(today)) {
            type = ScanType.OUT;
            meal = last.get().mealType();
        } else {
            type = ScanType.IN;
            meal = schedule.mealAt(event.scannedAt().atZone(zone).toLocalTime())
                    .orElseThrow(() -> new ScanRejectedException(Reason.OUTSIDE_MEAL_WINDOW,
                            "Mess is not serving a meal right now"));
            if (scanLogs.hasEntry(user.getId(), meal, today)) {
                throw new ScanRejectedException(Reason.ALREADY_ENTERED_FOR_MEAL,
                        "Already entered for " + meal + " today");
            }
        }

        try {
            return scanLogs.save(new ScanLog(null, user.getId(), type, meal,
                    event.scannedAt(), today, event.deviceId()));
        } catch (DuplicateEntryException e) {
            throw new ScanRejectedException(Reason.ALREADY_ENTERED_FOR_MEAL,
                    "Already entered for " + meal + " today");
        }
    }

    private boolean isDoubleScan(Instant previous, Instant current) {
        return Duration.between(previous, current).abs().compareTo(debounce) < 0;
    }
}
