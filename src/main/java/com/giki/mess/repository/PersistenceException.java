package com.giki.mess.repository;

/** Unexpected storage failure (the underlying SQL error is kept as the cause, never shown to users). */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
