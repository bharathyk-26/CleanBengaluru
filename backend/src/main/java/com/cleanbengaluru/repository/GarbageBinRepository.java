package com.cleanbengaluru.repository;

import com.cleanbengaluru.entity.BinStatus;
import com.cleanbengaluru.entity.GarbageBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GarbageBinRepository extends JpaRepository<GarbageBin, Long> {

    boolean existsByCode(String code);

    @Query("""
            SELECT b FROM GarbageBin b
            WHERE b.latitude  BETWEEN :minLat AND :maxLat
              AND b.longitude BETWEEN :minLon AND :maxLon
            """)
    List<GarbageBin> findInBoundingBox(@Param("minLat") double minLat,
                                       @Param("maxLat") double maxLat,
                                       @Param("minLon") double minLon,
                                       @Param("maxLon") double maxLon);

    long countByStatus(BinStatus status);

    long countByAreaNameAndStatusIn(String areaName, List<BinStatus> statuses);

    List<GarbageBin> findByAreaName(String areaName);
}
