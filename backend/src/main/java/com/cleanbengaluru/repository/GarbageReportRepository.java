package com.cleanbengaluru.repository;

import com.cleanbengaluru.entity.GarbageReport;
import com.cleanbengaluru.entity.GarbageType;
import com.cleanbengaluru.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GarbageReportRepository extends JpaRepository<GarbageReport, Long> {

    Page<GarbageReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    Page<GarbageReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);

    /**
     * BOUNDING BOX pre-filter. We cannot do trigonometry efficiently in an index,
     * so we first narrow to a lat/lon rectangle (which the composite index on
     * latitude,longitude can serve), then compute exact Haversine distance in Java.
     */
    @Query("""
            SELECT r FROM GarbageReport r
            WHERE r.latitude  BETWEEN :minLat AND :maxLat
              AND r.longitude BETWEEN :minLon AND :maxLon
              AND r.status NOT IN (com.cleanbengaluru.entity.ReportStatus.CLOSED,
                                   com.cleanbengaluru.entity.ReportStatus.REJECTED)
            """)
    List<GarbageReport> findInBoundingBox(@Param("minLat") double minLat,
                                          @Param("maxLat") double maxLat,
                                          @Param("minLon") double minLon,
                                          @Param("maxLon") double maxLon);

    /** Same bounding box, but narrowed to one category and a recent time window. Used by duplicate detection. */
    @Query("""
            SELECT r FROM GarbageReport r
            WHERE r.latitude  BETWEEN :minLat AND :maxLat
              AND r.longitude BETWEEN :minLon AND :maxLon
              AND r.garbageType = :type
              AND r.createdAt >= :since
              AND r.parentReport IS NULL
              AND r.status NOT IN (com.cleanbengaluru.entity.ReportStatus.CLOSED,
                                   com.cleanbengaluru.entity.ReportStatus.REJECTED)
            """)
    List<GarbageReport> findDuplicateCandidates(@Param("minLat") double minLat,
                                                @Param("maxLat") double maxLat,
                                                @Param("minLon") double minLon,
                                                @Param("maxLon") double maxLon,
                                                @Param("type") GarbageType type,
                                                @Param("since") LocalDateTime since);

    @Query("SELECT r.garbageType, COUNT(r) FROM GarbageReport r GROUP BY r.garbageType")
    List<Object[]> countGroupedByType();

    @Query("SELECT COALESCE(r.areaName, 'UNKNOWN'), COUNT(r) FROM GarbageReport r GROUP BY r.areaName")
    List<Object[]> countGroupedByArea();

    @Query("""
            SELECT FUNCTION('DATE_FORMAT', r.createdAt, '%Y-%m'), COUNT(r)
            FROM GarbageReport r
            GROUP BY FUNCTION('DATE_FORMAT', r.createdAt, '%Y-%m')
            ORDER BY 1
            """)
    List<Object[]> countGroupedByMonth();

    /** Average resolution time in HOURS, over reports that actually reached CLOSED. */
    @Query("""
            SELECT AVG(CAST(FUNCTION('TIMESTAMPDIFF', HOUR, r.createdAt, r.resolvedAt) AS DOUBLE))
            FROM GarbageReport r WHERE r.resolvedAt IS NOT NULL
            """)
    Double averageResolutionHours();

    @Query("""
            SELECT AVG(CAST(FUNCTION('TIMESTAMPDIFF', HOUR, r.createdAt, r.resolvedAt) AS DOUBLE))
            FROM GarbageReport r WHERE r.resolvedAt IS NOT NULL AND r.areaName = :area
            """)
    Double averageResolutionHoursForArea(@Param("area") String area);

    @Query("SELECT DISTINCT r.areaName FROM GarbageReport r WHERE r.areaName IS NOT NULL")
    List<String> findDistinctAreas();

    long countByAreaNameAndStatusIn(String areaName, List<ReportStatus> statuses);

    long countByAreaName(String areaName);

    @Query("SELECT COUNT(r) FROM GarbageReport r WHERE r.areaName = :area AND r.reopenCount > 0")
    long countReopenedInArea(@Param("area") String area);

    @Query("SELECT COUNT(r) FROM GarbageReport r WHERE r.reopenCount > 0")
    long countReopened();

    List<GarbageReport> findByParentReportId(Long parentId);
}
