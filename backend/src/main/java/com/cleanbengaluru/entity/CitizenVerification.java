package com.cleanbengaluru.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps to `citizen_verifications`. One row each time a citizen answers
 * "was this actually cleaned?" — kept as history, so a report that was
 * reopened twice has three rows.
 */
@Entity
@Table(name = "citizen_verifications", indexes = {
        @Index(name = "idx_verifications_report", columnList = "report_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private GarbageReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    /** true = "YES, CLEANED", false = "NO, NOT PROPERLY CLEANED". */
    @Column(nullable = false)
    private Boolean cleaned;

    @Column(length = 400)
    private String reason;

    @Column(name = "proof_image", length = 255)
    private String proofImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
