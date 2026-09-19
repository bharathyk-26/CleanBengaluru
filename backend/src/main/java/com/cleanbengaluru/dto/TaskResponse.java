package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.*;

import java.time.LocalDateTime;

/** One row on the worker dashboard. */
public record TaskResponse(Long taskId,
                           Long reportId,
                           GarbageType garbageType,
                           String description,
                           Priority priority,
                           ReportStatus reportStatus,
                           AssignmentStatus taskStatus,
                           Double latitude,
                           Double longitude,
                           String address,
                           String areaName,
                           String beforeImage,
                           String afterImage,
                           String workerNotes,
                           LocalDateTime reportCreatedAt,
                           LocalDateTime assignedAt,
                           LocalDateTime acceptedAt,
                           LocalDateTime startedAt,
                           LocalDateTime completedAt) {
}
