package com.giki.mess.campus.contract;

import java.time.Instant;
import java.util.UUID;

/** Provisional wire format. No dependency on a producer, database, clock or network. */
public final class BillingAdapter {
    private BillingAdapter() {}
    public record Notice(UUID eventId, Instant occurredAt, String studentId, String type) {}
    public static AlertEvent translate(Notice n) {
        if (!"BILL_ISSUED".equals(n.type())) throw new IllegalArgumentException("Unsupported billing notice.");
        return new AlertEvent(n.eventId(), n.occurredAt(), AlertEvent.Kind.BILL, n.studentId(), "Your bill is available.");
    }
}
