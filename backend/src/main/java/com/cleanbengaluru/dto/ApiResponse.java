package com.cleanbengaluru.dto;

import java.time.LocalDateTime;

/** Uniform success envelope used by every endpoint. */
public record ApiResponse<T>(boolean success, String message, T data, LocalDateTime timestamp) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null, LocalDateTime.now());
    }
}
