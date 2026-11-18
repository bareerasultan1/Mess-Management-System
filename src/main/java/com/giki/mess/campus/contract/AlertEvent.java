package com.giki.mess.campus.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Null recipient denotes the campus student audience. Text must be public-safe. */
public record AlertEvent(UUID id, Instant occurred, Kind kind, String recipient, String summary) {
    public enum Kind { BILL, MENU, ANNOUNCEMENT, DUE, OVERDUE, SPECIAL_MEAL }
    public AlertEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(occurred);
        Objects.requireNonNull(kind);
        if (summary == null || summary.isBlank() || summary.length() > 250)
            throw new IllegalArgumentException("Alert summary must contain 1 to 250 characters.");
        boolean individual = kind == Kind.BILL || kind == Kind.DUE || kind == Kind.OVERDUE;
        if (individual != (recipient != null) || recipient != null && recipient.isBlank())
            throw new IllegalArgumentException("Invalid alert audience.");
    }
}
