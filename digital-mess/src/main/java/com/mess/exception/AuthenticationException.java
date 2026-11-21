package com.mess.exception;

/** Login failed. The message is deliberately the same for every cause. */
public class AuthenticationException extends MessException {

    public AuthenticationException(String message) {
        super(message);
    }
}
