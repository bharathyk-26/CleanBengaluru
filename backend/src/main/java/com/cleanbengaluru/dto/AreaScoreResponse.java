package com.cleanbengaluru.dto;

/**
 * System-generated cleanliness score for an area. This is computed from data inside
 * this application only — it is NOT an official BBMP rating.
 */
public record AreaScoreResponse(String areaName,
                                double score,
                                String grade,
                                long openReports,
                                long resolvedReports,
                                long reopenedReports,
                                long overflowingBins,
                                Double averageResolutionHours) {
}
