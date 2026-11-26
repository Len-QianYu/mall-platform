package com.example.api.exception;

import org.springframework.http.HttpStatus;

public class AccountFlowException extends RuntimeException {
    private final HttpStatus status;
    private final int retryAfterSeconds;

    public AccountFlowException(String message) {
        this(HttpStatus.BAD_REQUEST, message, 0);
    }

    public AccountFlowException(HttpStatus status, String message) {
        this(status, message, 0);
    }

    public AccountFlowException(HttpStatus status, String message, int retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() { return this.status; }
    public int retryAfterSeconds() { return this.retryAfterSeconds; }
}
