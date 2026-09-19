package com.cleanbengaluru.service;

import com.cleanbengaluru.entity.GarbageReport;
import com.cleanbengaluru.entity.GarbageType;
import com.cleanbengaluru.repository.GarbageReportRepository;
import com.cleanbengaluru.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RULE-BASED duplicate detection. This is deliberately NOT AI — there is no model here,
 * just four conditions. Saying otherwise in an interview will not survive one follow-up question.
 *
 * ALGORITHM
 *  1. Build a lat/lon bounding box of `radiusMetres` around the new report.
 *  2. Ask MySQL for open reports inside that box, of the SAME category, created within
 *     the last `windowHours`. The composite index on (latitude, longitude) serves this.
 *  3. For each candidate, compute the exact Haversine distance.
 *  4. Keep the nearest one within the radius. That is the "possible existing report".
 *
 * TIME COMPLEXITY
 *   Step 2 is O(log n + k) with the index, where k = rows inside the box.
 *   Steps 3-4 are O(k) — one O(1) Haversine per candidate.
 *   Overall O(log n + k), and k is small because the box is ~100m across.
 *   Without the bounding box it would be O(n): Haversine on every row in the table.
 *
 * WHAT WE DO WITH THE RESULT
 *   We never silently discard the citizen's report. We return "Possible existing report
 *   found" and let them confirm. If they confirm, the new report is saved with
 *   parentReport pointing at the original and the original's duplicateCount is bumped,
 *   which in turn raises its priority. One physical garbage pile, many citizen voices.
 */
@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    private final GarbageReportRepository reportRepository;

    @Value("${app.duplicate.radius-metres:100}")
    private double radiusMetres;

    @Value("${app.duplicate.window-hours:24}")
    private int windowHours;

    /** A candidate duplicate plus how far away it is. */
    public record Match(GarbageReport report, double distanceMetres) {
    }

    @Transactional(readOnly = true)
    public Optional<Match> findPossibleDuplicate(double latitude, double longitude, GarbageType type) {

        double[] box = DistanceCalculator.boundingBox(latitude, longitude, radiusMetres);
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);

        List<GarbageReport> candidates = reportRepository.findDuplicateCandidates(
                box[0], box[1], box[2], box[3], type, since);

        GarbageReport nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (GarbageReport candidate : candidates) {
            double distance = DistanceCalculator.distanceMetres(
                    latitude, longitude, candidate.getLatitude(), candidate.getLongitude());

            if (distance <= radiusMetres && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        return nearest == null ? Optional.empty() : Optional.of(new Match(nearest, nearestDistance));
    }

    public double getRadiusMetres() {
        return radiusMetres;
    }
}
