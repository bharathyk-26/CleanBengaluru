package com.cleanbengaluru.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/** Uniform error envelope produced by GlobalExceptionHandler. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(boolean success,
                            String message,
                            int status,
                            String path,
                            Map<String, String> fieldErrors,
                            LocalDateTime timestamp) {

    public static ErrorResponse of(String message, int status, String path) {
        return new ErrorResponse(false, message, status, path, null, LocalDateTime.now());
    }

    public static ErrorResponse of(String message, int status, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(false, message, status, path, fieldErrors, LocalDateTime.now());
    }
}
