package com.cleanbengaluru.mapper;

import com.cleanbengaluru.dto.ReportResponse;
import com.cleanbengaluru.dto.TaskResponse;
import com.cleanbengaluru.entity.Assignment;
import com.cleanbengaluru.entity.GarbageReport;
import org.springframework.stereotype.Component;

/**
 * Converts entities to DTOs.
 *
 * Why a mapper at all: entities are shaped for the database (lazy proxies, password
 * hashes, bidirectional links). DTOs are shaped for the client. Returning entities
 * directly would leak fields and cause lazy-loading errors during JSON serialisation.
 */
@Component
public class ReportMapper {

    public ReportResponse toResponse(GarbageReport r, Assignment activeAssignment, Double distanceMetres) {
        return new ReportResponse(
                r.getId(),
                r.getReporter() != null ? r.getReporter().getId() : null,
                r.getReporter() != null ? r.getReporter().getName() : null,
                r.getGarbageType(),
                r.getDescription(),
                r.getBeforeImage(),
                activeAssignment != null ? activeAssignment.getAfterImage() : null,
                r.getLatitude(),
                r.getLongitude(),
                r.getAddress(),
                r.getAreaName(),
                r.getStatus(),
                r.getPriority(),
                r.getSeverity(),
                r.getRoadBlocked(),
                r.getDuplicateCount(),
                r.getReopenCount(),
                r.getParentReport() != null ? r.getParentReport().getId() : null,
                r.getRejectionReason(),
                activeAssignment != null ? activeAssignment.getId() : null,
                activeAssignment != null ? activeAssignment.getWorker().getId() : null,
                activeAssignment != null ? activeAssignment.getWorker().getName() : null,
                activeAssignment != null ? activeAssignment.getStatus() : null,
                distanceMetres,
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getResolvedAt()
        );
    }

    public TaskResponse toTaskResponse(Assignment a) {
        GarbageReport r = a.getReport();
        return new TaskResponse(
                a.getId(),
                r.getId(),
                r.getGarbageType(),
                r.getDescription(),
                r.getPriority(),
                r.getStatus(),
                a.getStatus(),
                r.getLatitude(),
                r.getLongitude(),
                r.getAddress(),
                r.getAreaName(),
                r.getBeforeImage(),
                a.getAfterImage(),
                a.getWorkerNotes(),
                r.getCreatedAt(),
                a.getAssignedAt(),
                a.getAcceptedAt(),
                a.getStartedAt(),
                a.getCompletedAt()
        );
    }
}
