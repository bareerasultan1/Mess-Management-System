package com.mess.observer;

import com.mess.domain.MealType;
import com.mess.domain.ScanType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceSubjectTest {

    private static final AttendanceRecordedEvent EVENT = new AttendanceRecordedEvent(
            1L, 1L, "Ali Khan", ScanType.IN, MealType.LUNCH, Instant.parse("2026-11-19T08:00:00Z"));

    @Test
    void aFailingObserverDoesNotStopTheOthers() throws Exception {
        CountDownLatch healthyCalled = new CountDownLatch(1);
        try (AttendanceSubject subject = new AttendanceSubject(Executors.newFixedThreadPool(2))) {
            subject.subscribe(e -> { throw new IllegalStateException("gateway down"); });
            subject.subscribe(e -> healthyCalled.countDown());
            subject.publish(EVENT);
            assertTrue(healthyCalled.await(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void unsubscribedObserverIsNotCalled() {
        AtomicInteger calls = new AtomicInteger();
        AttendanceObserver observer = e -> calls.incrementAndGet();
        try (AttendanceSubject subject = new AttendanceSubject(Executors.newSingleThreadExecutor())) {
            subject.subscribe(observer);
            subject.unsubscribe(observer);
            subject.publish(EVENT);
        }   // close() waits for queued work
        assertEquals(0, calls.get());
    }
}
