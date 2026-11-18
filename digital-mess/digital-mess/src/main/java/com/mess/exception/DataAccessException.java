package com.mess.exception;

/** The database could not complete an operation. Wraps SQLException so callers never see JDBC types. */
public class DataAccessException extends MessException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
