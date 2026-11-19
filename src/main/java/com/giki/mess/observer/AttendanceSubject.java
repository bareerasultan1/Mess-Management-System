package com.giki.mess.observer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OBSERVER pattern, subject side: the Attendance module publishes a ScanEvent here and does
 * not know who listens.
 *
 * <p>ASYNCHRONOUS ON PURPOSE: each observer runs on a worker thread, so publish() returns at
 * once. A slow or failing billing database therefore can never freeze the mess-gate turnstile.
 * An exception inside one observer is logged and contained; the other observers still run.
 *
 * <p>Thread safety: the observer list is a CopyOnWriteArrayList (safe to register while events are
 * being published) and ScanEvent is immutable.
 */
public final class AttendanceSubject implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceSubject.class);

    private final List<AttendanceObserver> observers = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;

    /**
     * Creates a subject that notifies its observers on the given executor.
     */
    public AttendanceSubject(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        this.executor = executor;
    }

    /**
     * Adds an observer that will receive every published scan event.
     */
    public void register(AttendanceObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer cannot be null");
        }
        observers.add(observer);
    }

    public void unregister(AttendanceObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notifies all observers in the background.
     *
     * @return a future that completes when every observer has finished (callers may ignore it)
     */
    public CompletableFuture<Void> publish(ScanEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        List<CompletableFuture<Void>> tasks = observers.stream()
            .map(o -> CompletableFuture.runAsync(() -> notifySafely(o, event), executor))
            .toList();
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
    }

    private void notifySafely(AttendanceObserver observer, ScanEvent event) {
        try {
            observer.onAttendanceRecorded(event);
        } catch (RuntimeException e) {
            LOG.error("Observer {} failed for scan event {}: {}",
                observer.getClass().getSimpleName(), event.eventId(), e.toString());
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
