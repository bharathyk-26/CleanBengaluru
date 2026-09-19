package com.cleanbengaluru.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Maps to `garbage_bins`. Physical dustbins placed around the city. */
@Entity
@Table(name = "garbage_bins", indexes = {
        @Index(name = "idx_bins_lat_lon", columnList = "latitude,longitude"),
        @Index(name = "idx_bins_status", columnList = "status"),
        @Index(name = "idx_bins_area", columnList = "area_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GarbageBin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable code painted on the bin, e.g. "BLR-RJN-014". */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "location_name", nullable = false, length = 200)
    private String locationName;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "bin_type", nullable = false, length = 20)
    private BinType binType;

    @Column(name = "capacity_litres", nullable = false)
    private Integer capacityLitres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BinStatus status;

    @Column(name = "last_collection_at")
    private LocalDateTime lastCollectionAt;

    @Column(name = "area_name", length = 100)
    private String areaName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
