package com.mess.exception;

/** Input failed validation at a module boundary. */
public class ValidationException extends MessException {

    public ValidationException(String message) {
        super(message);
    }
}
