package com.giki.mess.observer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/** Skeleton: behaviour is added test-first. */
public class AttendanceSubject implements AutoCloseable {

    public AttendanceSubject(ExecutorService executor) {
    }

    public void register(AttendanceObserver observer) {
    }

    public void unregister(AttendanceObserver observer) {
    }

    public CompletableFuture<Void> publish(ScanEvent event) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
    }
}
