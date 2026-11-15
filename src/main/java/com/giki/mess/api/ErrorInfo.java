package com.giki.mess.api;

/** User-safe description of a failure: a stable code for programs, a friendly message for people. */
public record ErrorInfo(String code, String message) {
}
