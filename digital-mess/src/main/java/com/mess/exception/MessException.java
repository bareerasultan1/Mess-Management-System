package com.mess.exception;

/** Base type for every error this application raises on purpose. */
public class MessException extends RuntimeException {

    public MessException(String message) {
        super(message);
    }

    public MessException(String message, Throwable cause) {
        super(message, cause);
    }
}
