package com.cleanbengaluru.exception;

/** Thrown for business-rule violations the client could have avoided. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
