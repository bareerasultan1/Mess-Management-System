package com.giki.mess.common;

/**
 * Small argument-checking helpers used by constructors and boundary methods.
 * Each helper throws IllegalArgumentException with the caller's own message, so a long list of
 * validation rules reads as a flat list of one-line statements instead of nested branches.
 */
public final class Guard {

    private Guard() {
    }

    /** Fails unless the text is neither null nor blank. */
    public static String notBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** Fails if the value is null. */
    public static <T> T notNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** Fails unless the condition holds. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
