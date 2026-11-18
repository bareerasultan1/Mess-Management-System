package com.giki.mess.campus;

import com.giki.mess.campus.alerts.DeliveryPool;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Latches hold both workers so saturation can be reproduced without a sleep. */
public final class WorkerChecks {
    private WorkerChecks() {}
    public static void main(String... args) throws Exception {
        var started=new CountDownLatch(2);
        var release=new CountDownLatch(1);
        var finished=new CountDownLatch(2);
        var names=ConcurrentHashMap.<String>newKeySet();
        var overflow=new AtomicInteger();
        try(var workers=new DeliveryPool()) {
            Runnable hold=()->{
                names.add(Thread.currentThread().getName()); started.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("Release timed out"); }
                catch(InterruptedException e) {Thread.currentThread().interrupt();throw new AssertionError(e);}
                finally {finished.countDown();}
            };
            if(!workers.offer(hold) || !workers.offer(hold) || !started.await(5,TimeUnit.SECONDS))
                throw new AssertionError("Both asynchronous workers must start");
            try {
                if(workers.offer(overflow::incrementAndGet) || overflow.get()!=0 || names.size()!=2
                    || names.contains(Thread.currentThread().getName())) throw new AssertionError("Saturation was unsafe");
            } finally {release.countDown();}
            if(!finished.await(5,TimeUnit.SECONDS)) throw new AssertionError("Workers did not drain");
        }
        System.out.println("PASS two distinct background workers; third offer deferred; no caller-thread delivery; shutdown drained");
    }
}
