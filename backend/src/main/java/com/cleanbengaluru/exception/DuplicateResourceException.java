package com.cleanbengaluru.exception;

/** Thrown when something unique already exists (e.g. email, bin code). Mapped to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
