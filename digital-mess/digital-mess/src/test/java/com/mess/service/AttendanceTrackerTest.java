package com.mess.service;

import com.mess.adapter.ScanEvent;
import com.mess.domain.MealType;
import com.mess.domain.Role;
import com.mess.domain.ScanType;
import com.mess.domain.UserFactory;
import com.mess.exception.ScanRejectedException;
import com.mess.exception.ScanRejectedException.Reason;
import com.mess.observer.AttendanceRecordedEvent;
import com.mess.observer.AttendanceSubject;
import com.mess.support.InMemoryScanLogRepository;
import com.mess.support.InMemoryUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceTrackerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Karachi");
    private static final String ALI = "RFID:04A3B2C1";

    private InMemoryUserRepository users;
    private InMemoryScanLogRepository scans;
    private AttendanceSubject subject;
    private AttendanceTracker tracker;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        scans = new InMemoryScanLogRepository();
        subject = new AttendanceSubject(Executors.newSingleThreadExecutor());
        tracker = new AttendanceTracker(users, scans, MealSchedule.defaults(), subject, ZONE,
                Duration.ofSeconds(5));
        users.save(UserFactory.create(Role.STUDENT, null, "ali", "Ali Khan", "hash", ALI, true));
    }

    @AfterEach
    void tearDown() {
        subject.close();
    }

    /** Builds an instant from a Karachi wall-clock time, e.g. at("2026-11-19T13:00:00"). */
    private static Instant at(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + "+05:00").toInstant();
    }

    private static ScanEvent scan(String credential, String localDateTime) {
        return new ScanEvent(credential, "T1", at(localDateTime));
    }

    private Reason rejectionReason(ScanEvent event) {
        return assertThrows(ScanRejectedException.class, () -> tracker.processScan(event)).getReason();
    }

    @Test
    void firstScanInsideMealWindowIsAnEntry() {
        ScanResult r = tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));
        assertEquals(ScanType.IN, r.scanType());
        assertEquals(MealType.LUNCH, r.mealType());
        assertEquals("Ali Khan", r.studentName());
    }

    @Test
    void nextScanAfterEntryIsAnExitForTheSameMeal() {
        tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));
        ScanResult r = tracker.processScan(scan(ALI, "2026-11-19T13:30:00"));
        assertEquals(ScanType.OUT, r.scanType());
        assertEquals(MealType.LUNCH, r.mealType());
    }

    @Test
    void exitIsAllowedEvenAfterTheMealWindowCloses() {
        tracker.processScan(scan(ALI, "2026-11-19T14:20:00"));
        ScanResult r = tracker.processScan(scan(ALI, "2026-11-19T14:45:00"));
        assertEquals(ScanType.OUT, r.scanType());
        assertEquals(MealType.LUNCH, r.mealType());
    }

    @Test
    void scanWithinDebounceTimeIsRejectedAsDuplicate() {
        tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));
        assertEquals(Reason.DUPLICATE_SCAN, rejectionReason(scan(ALI, "2026-11-19T13:00:04")));
        assertEquals(1, scans.count());
    }

    @Test
    void scanExactlyAtDebounceTimeIsAccepted() {
        tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));
        ScanResult r = tracker.processScan(scan(ALI, "2026-11-19T13:00:05"));
        assertEquals(ScanType.OUT, r.scanType());
    }

    @Test
    void entryOutsideAnyMealWindowIsRejected() {
        assertEquals(Reason.OUTSIDE_MEAL_WINDOW, rejectionReason(scan(ALI, "2026-11-19T16:00:00")));
        assertEquals(0, scans.count());
    }

    @Test
    void cannotEnterTheSameMealTwiceInOneDay() {
        tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));   // IN
        tracker.processScan(scan(ALI, "2026-11-19T13:30:00"));   // OUT
        assertEquals(Reason.ALREADY_ENTERED_FOR_MEAL, rejectionReason(scan(ALI, "2026-11-19T13:45:00")));
    }

    @Test
    void differentMealsOnTheSameDayAreAllowed() {
        tracker.processScan(scan(ALI, "2026-11-19T08:00:00"));   // breakfast IN
        tracker.processScan(scan(ALI, "2026-11-19T08:30:00"));   // breakfast OUT
        ScanResult r = tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));
        assertEquals(ScanType.IN, r.scanType());
        assertEquals(MealType.LUNCH, r.mealType());
    }

    @Test
    void forgottenExitYesterdayDoesNotTurnTodaysFirstScanIntoAnExit() {
        tracker.processScan(scan(ALI, "2026-11-18T13:00:00"));   // IN, never scanned out
        ScanResult r = tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));
        assertEquals(ScanType.IN, r.scanType());
    }

    @Test
    void unknownCredentialIsRejected() {
        assertEquals(Reason.UNKNOWN_CREDENTIAL, rejectionReason(scan("RFID:FFFFFFFF", "2026-11-19T13:00:00")));
    }

    @Test
    void staffCannotScanIntoTheMess() {
        users.save(UserFactory.create(Role.STAFF, null, "staff1", "Mess Staff", "hash", "RFID:11111111", true));
        assertEquals(Reason.NOT_A_STUDENT, rejectionReason(scan("RFID:11111111", "2026-11-19T13:00:00")));
    }

    @Test
    void inactiveStudentIsRejected() {
        users.save(UserFactory.create(Role.STUDENT, null, "old", "Old Student", "hash", "RFID:22222222", false));
        assertEquals(Reason.ACCOUNT_INACTIVE, rejectionReason(scan("RFID:22222222", "2026-11-19T13:00:00")));
    }

    @Test
    void manyThreadsScanningTheSameStudentCreateOnlyOneEntry() throws Exception {
        int threads = 20;
        // slow reads make a missing lock show up reliably: every thread would see "no previous scan"
        scans = new InMemoryScanLogRepository(30);
        tracker = new AttendanceTracker(users, scans, MealSchedule.defaults(), subject, ZONE,
                Duration.ofSeconds(5));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ScanEvent event = scan(ALI, "2026-11-19T13:00:00");

        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    tracker.processScan(event);
                    accepted.incrementAndGet();
                } catch (ScanRejectedException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, accepted.get());
        assertEquals(threads - 1, rejected.get());
        assertEquals(1, scans.count());
    }

    @Test
    void observersAreNotifiedAfterAnAcceptedScan() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        List<AttendanceRecordedEvent> events = new ArrayList<>();
        subject.subscribe(e -> {
            synchronized (events) {
                events.add(e);
            }
            received.countDown();
        });

        tracker.processScan(scan(ALI, "2026-11-19T13:00:00"));

        assertTrue(received.await(3, TimeUnit.SECONDS));
        synchronized (events) {
            assertEquals(1, events.size());
            assertEquals(ScanType.IN, events.get(0).scanType());
            assertEquals("Ali Khan", events.get(0).studentName());
        }
    }

    @Test
    void rejectedScanDoesNotNotifyObservers() throws Exception {
        AtomicInteger notifications = new AtomicInteger();
        subject.subscribe(e -> notifications.incrementAndGet());

        rejectionReason(scan(ALI, "2026-11-19T16:00:00"));
        subject.close();   // waits for queued notifications to finish

        assertEquals(0, notifications.get());
    }
}
