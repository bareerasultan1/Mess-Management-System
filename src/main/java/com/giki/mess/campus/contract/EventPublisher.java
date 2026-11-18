package com.giki.mess.campus.contract;

/** Publish in the producer's local transaction, or from a durable transport receiver. */
public interface EventPublisher {
    void publish(AlertEvent event);
}
