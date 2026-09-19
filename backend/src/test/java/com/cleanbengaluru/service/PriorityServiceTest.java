package com.cleanbengaluru.service;

import com.cleanbengaluru.entity.GarbageReport;
import com.cleanbengaluru.entity.GarbageType;
import com.cleanbengaluru.entity.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure business logic: no Spring context, no database, runs in milliseconds. */
class PriorityServiceTest {

    private final PriorityService priorityService = new PriorityService();

    private GarbageReport report(GarbageType type, int severity, boolean roadBlocked,
                                 int duplicates, int reopens) {
        return GarbageReport.builder()
                .garbageType(type)
                .severity(severity)
                .roadBlocked(roadBlocked)
                .duplicateCount(duplicates)
                .reopenCount(reopens)
                .build();
    }

    @Test
    @DisplayName("small garden waste is LOW priority")
    void gardenWasteIsLow() {
        assertThat(priorityService.calculate(report(GarbageType.GARDEN_WASTE, 1, false, 0, 0)))
                .isEqualTo(Priority.LOW);
    }

    @Test
    @DisplayName("illegal dumping blocking a road is CRITICAL")
    void illegalDumpingBlockingRoadIsCritical() {
        assertThat(priorityService.calculate(report(GarbageType.ILLEGAL_DUMPING, 5, true, 0, 0)))
                .isEqualTo(Priority.CRITICAL);
    }

    @Test
    @DisplayName("more duplicate reports raise the priority")
    void duplicatesRaisePriority() {
        int alone = priorityService.score(report(GarbageType.ROADSIDE_GARBAGE, 3, false, 0, 0));
        int crowded = priorityService.score(report(GarbageType.ROADSIDE_GARBAGE, 3, false, 4, 0));
        assertThat(crowded).isGreaterThan(alone);
    }

    @Test
    @DisplayName("duplicate contribution is capped at 5 reports")
    void duplicateContributionIsCapped() {
        int five = priorityService.score(report(GarbageType.ROADSIDE_GARBAGE, 3, false, 5, 0));
        int fifty = priorityService.score(report(GarbageType.ROADSIDE_GARBAGE, 3, false, 50, 0));
        assertThat(fifty).isEqualTo(five);
    }

    @Test
    @DisplayName("a reopened report gets a bump")
    void reopenedGetsBump() {
        int first = priorityService.score(report(GarbageType.PLASTIC_WASTE, 3, false, 0, 0));
        int reopened = priorityService.score(report(GarbageType.PLASTIC_WASTE, 3, false, 0, 1));
        assertThat(reopened).isEqualTo(first + 10);
    }
}
