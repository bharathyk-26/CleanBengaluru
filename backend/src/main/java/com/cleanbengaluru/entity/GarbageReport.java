package com.cleanbengaluru.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to `garbage_reports`. One row = one citizen complaint.
 *
 * Relationships:
 *  - many reports belong to one User (the reporter)
 *  - a report may point to a `parentReport` when duplicate detection decided it is
 *    the same physical garbage pile as an earlier report
 */
@Entity
@Table(name = "garbage_reports", indexes = {
        @Index(name = "idx_reports_status", columnList = "status"),
        @Index(name = "idx_reports_lat_lon", columnList = "latitude,longitude"),
        @Index(name = "idx_reports_area", columnList = "area_name"),
        @Index(name = "idx_reports_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GarbageReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY so listing reports does not silently drag every user row along. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "garbage_type", nullable = false, length = 40)
    private GarbageType garbageType;

    @Column(length = 500)
    private String description;

    /** Relative path of the BEFORE photo, e.g. "abc123.jpg". Served via /api/files/{name}. */
    @Column(name = "before_image", length = 255)
    private String beforeImage;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 255)
    private String address;

    @Column(name = "area_name", length = 100)
    private String areaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Priority priority;

    /** 1 (small) .. 5 (very large) — self-reported by the citizen, feeds priority. */
    @Column(nullable = false)
    @Builder.Default
    private Integer severity = 3;

    @Column(name = "road_blocked", nullable = false)
    @Builder.Default
    private Boolean roadBlocked = false;

    /** How many other citizens reported the same spot. Feeds priority. */
    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private Integer duplicateCount = 0;

    @Column(name = "reopen_count", nullable = false)
    @Builder.Default
    private Integer reopenCount = 0;

    /** Set when this report was detected as a duplicate of an existing one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_report_id")
    private GarbageReport parentReport;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Set when the report reaches CLOSED. Used for average-resolution-time analytics. */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
