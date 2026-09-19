package com.cleanbengaluru.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a garbage report.
 *
 * REPORTED -> UNDER_REVIEW -> ASSIGNED -> WORKER_ACCEPTED -> CLEANING_IN_PROGRESS
 *          -> CLEANING_COMPLETED -> VERIFICATION_PENDING -> VERIFIED -> CLOSED
 *
 * REPORTED -> REJECTED
 * VERIFICATION_PENDING -> REOPENED -> ASSIGNED ...
 */
public enum ReportStatus {
    REPORTED,
    UNDER_REVIEW,
    ASSIGNED,
    WORKER_ACCEPTED,
    CLEANING_IN_PROGRESS,
    CLEANING_COMPLETED,
    VERIFICATION_PENDING,
    VERIFIED,
    CLOSED,
    REJECTED,
    REOPENED;

    /** The single source of truth for "which move is legal". Used by ReportService. */
    private static final Map<ReportStatus, Set<ReportStatus>> ALLOWED = Map.ofEntries(
            Map.entry(REPORTED, EnumSet.of(UNDER_REVIEW, ASSIGNED, REJECTED)),
            Map.entry(UNDER_REVIEW, EnumSet.of(ASSIGNED, REJECTED)),
            Map.entry(ASSIGNED, EnumSet.of(WORKER_ACCEPTED, UNDER_REVIEW)),
            Map.entry(WORKER_ACCEPTED, EnumSet.of(CLEANING_IN_PROGRESS)),
            Map.entry(CLEANING_IN_PROGRESS, EnumSet.of(CLEANING_COMPLETED)),
            Map.entry(CLEANING_COMPLETED, EnumSet.of(VERIFICATION_PENDING)),
            Map.entry(VERIFICATION_PENDING, EnumSet.of(VERIFIED, REOPENED)),
            Map.entry(VERIFIED, EnumSet.of(CLOSED)),
            Map.entry(REOPENED, EnumSet.of(ASSIGNED, REJECTED)),
            Map.entry(REJECTED, EnumSet.noneOf(ReportStatus.class)),
            Map.entry(CLOSED, EnumSet.noneOf(ReportStatus.class))
    );

    public boolean canMoveTo(ReportStatus target) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(ReportStatus.class)).contains(target);
    }

    /** A report that is still "live" in the system (counts against an area's cleanliness). */
    public boolean isOpen() {
        return this != CLOSED && this != REJECTED && this != VERIFIED;
    }
}
