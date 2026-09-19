package com.cleanbengaluru.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to `assignments`. One row = one "task" given to a worker for one report.
 *
 * A report can have several assignments over time (the original cleanup, then another
 * after the citizen reopened it), so this is ManyToOne on report, not OneToOne.
 * Only one assignment per report is ACTIVE at a time.
 */
@Entity
@Table(name = "assignments", indexes = {
        @Index(name = "idx_assignments_worker", columnList = "worker_id"),
        @Index(name = "idx_assignments_report", columnList = "report_id"),
        @Index(name = "idx_assignments_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private GarbageReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status;

    /** AFTER-cleaning photo uploaded by the worker. Required before COMPLETED. */
    @Column(name = "after_image", length = 255)
    private String afterImage;

    @Column(name = "worker_notes", length = 500)
    private String workerNotes;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (this.assignedAt == null) this.assignedAt = LocalDateTime.now();
    }
}
