package com.cleanbengaluru.dto;

import java.util.List;
import java.util.Map;

/** Everything the admin dashboard needs, in one call. */
public record DashboardResponse(long totalReports,
                                long pendingReports,
                                long assignedReports,
                                long inProgressReports,
                                long completedReports,
                                long reopenedReports,
                                long rejectedReports,
                                long totalBins,
                                long overflowingBins,
                                long damagedBins,
                                long totalCitizens,
                                long totalWorkers,
                                Double averageResolutionHours,
                                Map<String, Long> reportsByCategory,
                                Map<String, Long> reportsByArea,
                                Map<String, Long> reportsByMonth,
                                List<WorkerStatsResponse> workerStats,
                                List<AreaScoreResponse> areaScores) {
}
