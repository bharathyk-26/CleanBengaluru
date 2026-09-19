package com.cleanbengaluru.dto;

import com.cleanbengaluru.entity.*;

import java.time.LocalDateTime;

/**
 * What the API returns for a report.
 *
 * Note what is NOT here: the reporter's email, phone or password. A citizen browsing
 * nearby reports sees only the reporter's display name and the report's own data.
 */
public record ReportResponse(Long id,
                             Long reporterId,
                             String reporterName,
                             GarbageType garbageType,
                             String description,
                             String beforeImage,
                             String afterImage,
                             Double latitude,
                             Double longitude,
                             String address,
                             String areaName,
                             ReportStatus status,
                             Priority priority,
                             Integer severity,
                             Boolean roadBlocked,
                             Integer duplicateCount,
                             Integer reopenCount,
                             Long parentReportId,
                             String rejectionReason,
                             Long assignmentId,
                             Long workerId,
                             String workerName,
                             AssignmentStatus assignmentStatus,
                             Double distanceMetres,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt,
                             LocalDateTime resolvedAt) {
}
