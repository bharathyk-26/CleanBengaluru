package com.cleanbengaluru.service;

import com.cleanbengaluru.dto.AreaScoreResponse;
import com.cleanbengaluru.dto.DashboardResponse;
import com.cleanbengaluru.dto.WorkerStatsResponse;
import com.cleanbengaluru.entity.*;
import com.cleanbengaluru.repository.AssignmentRepository;
import com.cleanbengaluru.repository.GarbageBinRepository;
import com.cleanbengaluru.repository.GarbageReportRepository;
import com.cleanbengaluru.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final GarbageReportRepository reportRepository;
    private final GarbageBinRepository binRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {

        long total = reportRepository.count();
        long pending = reportRepository.countByStatus(ReportStatus.REPORTED)
                + reportRepository.countByStatus(ReportStatus.UNDER_REVIEW);
        long assigned = reportRepository.countByStatus(ReportStatus.ASSIGNED)
                + reportRepository.countByStatus(ReportStatus.WORKER_ACCEPTED);
        long inProgress = reportRepository.countByStatus(ReportStatus.CLEANING_IN_PROGRESS);
        long completed = reportRepository.countByStatus(ReportStatus.CLOSED)
                + reportRepository.countByStatus(ReportStatus.VERIFIED);
        long reopened = reportRepository.countReopened();
        long rejected = reportRepository.countByStatus(ReportStatus.REJECTED);

        return new DashboardResponse(
                total, pending, assigned, inProgress, completed, reopened, rejected,
                binRepository.count(),
                binRepository.countByStatus(BinStatus.OVERFLOWING),
                binRepository.countByStatus(BinStatus.DAMAGED),
                userRepository.countByRole(Role.CITIZEN),
                userRepository.countByRole(Role.WORKER),
                round(reportRepository.averageResolutionHours()),
                toMap(reportRepository.countGroupedByType()),
                toMap(reportRepository.countGroupedByArea()),
                toMap(reportRepository.countGroupedByMonth()),
                workerStats(),
                areaScores()
        );
    }

    @Transactional(readOnly = true)
    public List<WorkerStatsResponse> workerStats() {
        List<WorkerStatsResponse> stats = new ArrayList<>();

        for (User worker : userRepository.findByRole(Role.WORKER)) {
            long totalTasks = assignmentRepository.countByWorkerId(worker.getId());
            long completedTasks = assignmentRepository
                    .countByWorkerIdAndStatus(worker.getId(), AssignmentStatus.COMPLETED);
            long activeTasks = assignmentRepository
                    .countByWorkerIdAndStatus(worker.getId(), AssignmentStatus.ASSIGNED)
                    + assignmentRepository.countByWorkerIdAndStatus(worker.getId(), AssignmentStatus.ACCEPTED)
                    + assignmentRepository.countByWorkerIdAndStatus(worker.getId(), AssignmentStatus.IN_PROGRESS);

            stats.add(new WorkerStatsResponse(worker.getId(), worker.getName(), worker.getAreaName(),
                    totalTasks, completedTasks, activeTasks));
        }
        return stats;
    }

    /**
     * AREA CLEANLINESS SCORE — a system-generated indicator computed only from data inside
     * this application. It is NOT an official BBMP rating.
     *
     * Start at 100 and subtract penalties:
     *
     *   score = 100
     *         - min(openReports  * 3, 30)     unresolved complaints hurt most
     *         - min(overflowing  * 4, 20)     overflowing / damaged bins
     *         - min(reopened     * 5, 20)     we cleaned it badly and had to redo it
     *         - min(avgResolutionHours / 2, 20)   slow response
     *         + min(resolvedReports, 10)      credit for problems actually fixed
     *
     * Clamped to 0..100.  A >= 85, B >= 70, C >= 55, D >= 40, E below that.
     * Every term is bounded, so one bad month cannot drive the score to nonsense.
     */
    @Transactional(readOnly = true)
    public List<AreaScoreResponse> areaScores() {

        List<AreaScoreResponse> scores = new ArrayList<>();
        List<String> areas = reportRepository.findDistinctAreas();

        List<ReportStatus> openStatuses = Arrays.stream(ReportStatus.values())
                .filter(ReportStatus::isOpen).toList();

        for (String area : areas) {
            if (area == null || area.isBlank()) continue;

            long openReports = reportRepository.countByAreaNameAndStatusIn(area, openStatuses);
            long allReports = reportRepository.countByAreaName(area);
            long resolvedReports = allReports - openReports;
            long reopenedReports = reportRepository.countReopenedInArea(area);
            long badBins = binRepository.countByAreaNameAndStatusIn(area,
                    List.of(BinStatus.OVERFLOWING, BinStatus.DAMAGED));
            Double avgHours = reportRepository.averageResolutionHoursForArea(area);

            double score = 100d;
            score -= Math.min(openReports * 3d, 30d);
            score -= Math.min(badBins * 4d, 20d);
            score -= Math.min(reopenedReports * 5d, 20d);
            score -= avgHours == null ? 0d : Math.min(avgHours / 2d, 20d);
            score += Math.min(resolvedReports, 10d);

            score = Math.max(0d, Math.min(100d, score));

            scores.add(new AreaScoreResponse(area, round(score), grade(score),
                    openReports, resolvedReports, reopenedReports, badBins, round(avgHours)));
        }

        scores.sort(Comparator.comparingDouble(AreaScoreResponse::score).reversed());
        return scores;
    }

    private String grade(double score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        if (score >= 40) return "D";
        return "E";
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            map.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 10d) / 10d;
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
