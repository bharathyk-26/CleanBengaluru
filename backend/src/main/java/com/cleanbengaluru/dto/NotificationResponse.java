package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String title, String message,
                                   Long reportId, Boolean read, LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(),
                n.getReportId(), n.getRead(), n.getCreatedAt());
    }
}
