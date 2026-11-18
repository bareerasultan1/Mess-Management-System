package com.giki.mess.campus.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import com.giki.mess.campus.contract.AlertEvent.Kind;

public final class Rules {
    private Rules() {}
    public static String text(String raw, int max) {
        if (raw == null || raw.isBlank() || raw.strip().length() > max)
            throw new IllegalArgumentException("Enter 1 to " + max + " characters.");
        return raw.strip();
    }
    public static int offset(int page) {
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Page outside valid range.");
        return page * 20;
    }
    public static String meal(String value) {
        if (value == null || !Set.of("BREAKFAST","LUNCH","DINNER").contains(value))
            throw new IllegalArgumentException("Choose breakfast, lunch or dinner.");
        return value;
    }
    public static int rating(int value) {
        if (value < 1 || value > 5) throw new IllegalArgumentException("Choose a rating from 1 to 5.");
        return value;
    }
    public static boolean transition(String from, String to) {
        return switch (from) {
            case "OPEN" -> "IN_REVIEW".equals(to);
            case "IN_REVIEW" -> "RESOLVED".equals(to);
            case "RESOLVED" -> "CLOSED".equals(to);
            default -> false;
        };
    }
    public static Optional<Kind> dueKind(LocalDate due, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(due)) return Optional.empty();
        if (today.equals(due)) return Optional.of(Kind.DUE);
        return Optional.of(Kind.OVERDUE);
    }
}
