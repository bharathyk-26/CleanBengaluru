package com.cleanbengaluru.exception;

/** Thrown for upload/read failures. Mapped to HTTP 400 or 500 depending on cause. */
public class FileStorageException extends RuntimeException {
    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
