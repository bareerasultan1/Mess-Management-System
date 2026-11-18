package com.giki.mess.campus.alerts;

import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import com.giki.mess.campus.feedback.Feedback;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="mess.scheduling",havingValue="true",matchIfMissing=true)
public class Workers {
    private static final Logger LOG = LoggerFactory.getLogger(Workers.class);
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
        new SynchronousQueue<>(), Thread.ofPlatform().name("campus-delivery-",0).factory(), new ThreadPoolExecutor.AbortPolicy());
    private final Delivery delivery;
    private final Feedback feedback;
    private final BillReminders bills;
    public Workers(Delivery delivery, Feedback feedback, BillReminders bills) { this.delivery=delivery; this.feedback=feedback; this.bills=bills; }

    @Scheduled(fixedDelay=250)
    public void poll() {
        for (int i=0; i<2; i++) {
            try { pool.execute(this::drain); }
            catch (RejectedExecutionException busy) { return; } // Rows stay ready for the next poll.
        }
    }
    private void drain() {
        try { for (int n=0; n<8 && delivery.runOne(); n++) { /* bounded drain */ } }
        catch (RuntimeException ex) { LOG.warn("Delivery transaction interrupted; pending rows will be polled again."); }
    }
    @Scheduled(fixedDelay=1000)
    public void escalate() { feedback.escalate(); }
    @Scheduled(fixedDelay=1000)
    public void reminders() { bills.tick(); }
    @PreDestroy public void close() { pool.shutdown(); }
}
