package com.giki.mess.observer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.giki.mess.billing.model.MealType;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Observer pattern: asynchronous publication of scan events (QA-PERF-01, risk R-02/Coupling). */
@DisplayName("AttendanceSubject (Observer pattern)")
class AttendanceSubjectTest {

    static ScanEvent event(String id) {
        return new ScanEvent(id, "2023294", LocalDateTime.of(2026, 11, 20, 13, 0),
            MealType.LUNCH, "T1");
    }

    ExecutorService pool;
    AttendanceSubject subject;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(4);
        subject = new AttendanceSubject(pool);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    @DisplayName("every registered observer is notified")
    void allObserversNotified() throws Exception {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        subject.register(e -> a.incrementAndGet());
        subject.register(e -> b.incrementAndGet());

        subject.publish(event("E1")).get(2, TimeUnit.SECONDS);

        assertThat(a.get()).isEqualTo(1);
        assertThat(b.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("publish returns immediately even if an observer is slow (gate never blocks)")
    void publishDoesNotBlockOnSlowObserver() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger finished = new AtomicInteger();
        subject.register(e -> {
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            finished.incrementAndGet();
        });

        long begin = System.nanoTime();
        CompletableFuture<Void> done = subject.publish(event("E1"));
        long tookMs = (System.nanoTime() - begin) / 1_000_000;

        assertThat(tookMs).isLessThan(200);          // returned while the observer is still stuck
        assertThat(finished.get()).isZero();
        release.countDown();
        done.get(2, TimeUnit.SECONDS);
        assertThat(finished.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("an observer that throws neither breaks the publisher nor the other observers")
    void failingObserverIsIsolated() throws Exception {
        AtomicInteger healthy = new AtomicInteger();
        subject.register(e -> {
            throw new IllegalStateException("billing database down");
        });
        subject.register(e -> healthy.incrementAndGet());

        subject.publish(event("E1")).get(2, TimeUnit.SECONDS);   // must not throw

        assertThat(healthy.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unregistered observer is no longer notified")
    void unregisteredObserverIsSilent() throws Exception {
        AtomicInteger count = new AtomicInteger();
        AttendanceObserver o = e -> count.incrementAndGet();
        subject.register(o);
        subject.unregister(o);

        subject.publish(event("E1")).get(2, TimeUnit.SECONDS);

        assertThat(count.get()).isZero();
    }

    @Test
    @DisplayName("publishing a null event is rejected")
    void nullEventRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> subject.publish(null));
    }

    @Test
    @DisplayName("ScanEvent rejects missing fields")
    void scanEventValidation() {
        LocalDateTime t = LocalDateTime.now();
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ScanEvent(" ", "2023294", t, MealType.LUNCH, "T1"));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ScanEvent("E", null, t, MealType.LUNCH, "T1"));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ScanEvent("E", "2023294", null, MealType.LUNCH, "T1"));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ScanEvent("E", "2023294", t, null, "T1"));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new ScanEvent("E", "2023294", t, MealType.LUNCH, ""));
    }
}
