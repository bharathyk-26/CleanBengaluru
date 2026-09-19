package com.cleanbengaluru.repository;

import com.cleanbengaluru.entity.Assignment;
import com.cleanbengaluru.entity.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByWorkerIdOrderByAssignedAtDesc(Long workerId);

    List<Assignment> findByWorkerIdAndStatusInOrderByAssignedAtDesc(Long workerId, List<AssignmentStatus> statuses);

    /** The currently active task for a report (there is at most one). */
    Optional<Assignment> findFirstByReportIdAndStatusNotOrderByAssignedAtDesc(Long reportId, AssignmentStatus status);

    Optional<Assignment> findFirstByReportIdOrderByAssignedAtDesc(Long reportId);

    List<Assignment> findByReportIdOrderByAssignedAtAsc(Long reportId);

    long countByWorkerIdAndStatus(Long workerId, AssignmentStatus status);

    long countByWorkerId(Long workerId);

    long countByStatus(AssignmentStatus status);

    @Query("SELECT a.worker.id, a.worker.name, COUNT(a) FROM Assignment a GROUP BY a.worker.id, a.worker.name")
    List<Object[]> countGroupedByWorker();

    @Query("""
            SELECT a.worker.id, COUNT(a) FROM Assignment a
            WHERE a.status = com.cleanbengaluru.entity.AssignmentStatus.COMPLETED
            GROUP BY a.worker.id
            """)
    List<Object[]> countCompletedGroupedByWorker();
}
