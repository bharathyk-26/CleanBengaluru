package com.cleanbengaluru.service;

import com.cleanbengaluru.entity.GarbageReport;
import com.cleanbengaluru.entity.GarbageType;
import com.cleanbengaluru.entity.Priority;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Rule-based priority. No machine learning, no model — a transparent points system
 * that a human can audit and explain in an interview.
 *
 *   score = categoryWeight
 *         + (severity - 1) * 3        // citizen-reported size, 1..5
 *         + (roadBlocked ? 15 : 0)    // a blocked road is a safety issue
 *         + min(duplicateCount, 5) * 4  // many citizens complaining = real and urgent
 *         + (reopened ? 10 : 0)       // we already failed this citizen once
 *
 *   score >= 45 -> CRITICAL
 *   score >= 30 -> HIGH
 *   score >= 15 -> MEDIUM
 *   else        -> LOW
 *
 * Time complexity: O(1).
 */
@Service
public class PriorityService {

    private static final Map<GarbageType, Integer> CATEGORY_WEIGHT = Map.of(
            GarbageType.ILLEGAL_DUMPING, 25,
            GarbageType.CONSTRUCTION_WASTE, 22,
            GarbageType.OVERFLOWING_DUSTBIN, 20,
            GarbageType.MISSED_COLLECTION, 18,
            GarbageType.ROADSIDE_GARBAGE, 15,
            GarbageType.DAMAGED_BIN, 14,
            GarbageType.PLASTIC_WASTE, 10,
            GarbageType.UNCLEAN_ROAD, 8,
            GarbageType.GARDEN_WASTE, 5
    );

    public Priority calculate(GarbageReport report) {
        int score = score(report);
        if (score >= 45) return Priority.CRITICAL;
        if (score >= 30) return Priority.HIGH;
        if (score >= 15) return Priority.MEDIUM;
        return Priority.LOW;
    }

    /** Exposed separately so unit tests (and the admin UI) can show the raw number. */
    public int score(GarbageReport report) {
        int score = CATEGORY_WEIGHT.getOrDefault(report.getGarbageType(), 10);

        int severity = report.getSeverity() == null ? 3 : report.getSeverity();
        score += (severity - 1) * 3;

        if (Boolean.TRUE.equals(report.getRoadBlocked())) {
            score += 15;
        }

        int duplicates = report.getDuplicateCount() == null ? 0 : report.getDuplicateCount();
        score += Math.min(duplicates, 5) * 4;

        int reopens = report.getReopenCount() == null ? 0 : report.getReopenCount();
        if (reopens > 0) {
            score += 10;
        }

        return score;
    }
}
