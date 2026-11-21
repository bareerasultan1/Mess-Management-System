package com.mess.exception;

/** A unique constraint was violated (PostgreSQL SQLState 23505). */
public class DuplicateEntryException extends DataAccessException {

    public DuplicateEntryException(String message, Throwable cause) {
        super(message, cause);
    }
}
