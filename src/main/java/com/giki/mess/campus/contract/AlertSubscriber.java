package com.giki.mess.campus.contract;

/** Consume a bounded page. Return the last processed student ID, or null when complete. */
public interface AlertSubscriber {
    String deliver(AlertEvent event, String after);
}
