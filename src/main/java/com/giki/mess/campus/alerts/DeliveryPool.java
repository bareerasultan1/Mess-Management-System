package com.giki.mess.campus.alerts;

import java.util.concurrent.*;

/** At most two database drains run per instance. A rejected offer leaves the work in PostgreSQL. */
public final class DeliveryPool implements AutoCloseable {
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
        new SynchronousQueue<>(),Thread.ofPlatform().name("campus-delivery-",0).factory(),
        new ThreadPoolExecutor.AbortPolicy());

    public boolean offer(Runnable drain) {
        try {
            workers.execute(drain);
            return true;
        } catch (RejectedExecutionException busy) {
            return false;
        }
    }
    @Override public void close() { workers.close(); }
}
