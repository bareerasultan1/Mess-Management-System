package com.mess.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Observer pattern. Observers are notified on a separate thread pool, so a slow
 * or failing observer (e.g. a notification gateway) can never delay or fail a scan.
 * The observer list is a CopyOnWriteArrayList, so subscribing while events are
 * being published is safe.
 */
public class AttendanceSubject implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AttendanceSubject.class.getName());

    private final List<AttendanceObserver> observers = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;

    public AttendanceSubject() {
        this(Executors.newFixedThreadPool(2));
    }

    public AttendanceSubject(ExecutorService executor) {
        this.executor = executor;
    }

    public void subscribe(AttendanceObserver observer) {
        observers.add(observer);
    }

    public void unsubscribe(AttendanceObserver observer) {
        observers.remove(observer);
    }

    public void publish(AttendanceRecordedEvent event) {
        for (AttendanceObserver observer : observers) {
            executor.execute(() -> {
                try {
                    observer.onAttendanceRecorded(event);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Attendance observer failed: " + observer.getClass().getSimpleName(), e);
                }
            });
        }
    }

    /** Lets queued notifications finish (up to 3 seconds), then stops the pool. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
