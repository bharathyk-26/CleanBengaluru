package com.cleanbengaluru.service;

import com.cleanbengaluru.entity.GarbageReport;
import com.cleanbengaluru.entity.GarbageType;
import com.cleanbengaluru.repository.GarbageReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * The repository is mocked, so this test proves the ALGORITHM (distance filtering and
 * "pick the nearest") without needing MySQL.
 */
@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock
    private GarbageReportRepository reportRepository;

    @InjectMocks
    private DuplicateDetectionService service;

    private static final double LAT = 12.9716;
    private static final double LON = 77.5946;

    @BeforeEach
    void setUp() {
        // @Value fields are not injected outside a Spring context, so set them by hand.
        ReflectionTestUtils.setField(service, "radiusMetres", 100d);
        ReflectionTestUtils.setField(service, "windowHours", 24);
    }

    private GarbageReport at(long id, double lat, double lon) {
        return GarbageReport.builder()
                .id(id).latitude(lat).longitude(lon)
                .garbageType(GarbageType.ROADSIDE_GARBAGE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("no candidates in the box -> no duplicate")
    void noCandidates() {
        when(reportRepository.findDuplicateCandidates(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.findPossibleDuplicate(LAT, LON, GarbageType.ROADSIDE_GARBAGE)).isEmpty();
    }

    @Test
    @DisplayName("a report ~20 m away is flagged as a duplicate")
    void nearbyReportIsDuplicate() {
        // ~0.00018 degrees of latitude is about 20 metres.
        when(reportRepository.findDuplicateCandidates(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(at(1L, LAT + 0.00018, LON)));

        Optional<DuplicateDetectionService.Match> match =
                service.findPossibleDuplicate(LAT, LON, GarbageType.ROADSIDE_GARBAGE);

        assertThat(match).isPresent();
        assertThat(match.get().report().getId()).isEqualTo(1L);
        assertThat(match.get().distanceMetres()).isLessThan(100d);
    }

    @Test
    @DisplayName("a corner-of-the-box report beyond the radius is rejected")
    void tooFarIsNotDuplicate() {
        // ~0.0027 degrees is roughly 300 m — inside a generous box, outside the 100 m circle.
        when(reportRepository.findDuplicateCandidates(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(at(2L, LAT + 0.0027, LON)));

        assertThat(service.findPossibleDuplicate(LAT, LON, GarbageType.ROADSIDE_GARBAGE)).isEmpty();
    }

    @Test
    @DisplayName("when several candidates match, the nearest one wins")
    void nearestWins() {
        when(reportRepository.findDuplicateCandidates(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(
                        at(1L, LAT + 0.00070, LON),   // ~78 m
                        at(2L, LAT + 0.00018, LON),   // ~20 m
                        at(3L, LAT + 0.00045, LON))); // ~50 m

        Optional<DuplicateDetectionService.Match> match =
                service.findPossibleDuplicate(LAT, LON, GarbageType.ROADSIDE_GARBAGE);

        assertThat(match).isPresent();
        assertThat(match.get().report().getId()).isEqualTo(2L);
    }
}
