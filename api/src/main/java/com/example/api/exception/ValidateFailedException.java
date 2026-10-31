package com.example.api.exception;

public class ValidateFailedException extends RuntimeException {

    public ValidateFailedException() {
        super();
    }

    public ValidateFailedException(String message) {
        super(message);
    }

}
