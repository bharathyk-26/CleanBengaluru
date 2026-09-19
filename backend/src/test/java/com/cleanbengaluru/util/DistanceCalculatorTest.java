package com.cleanbengaluru.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceCalculatorTest {

    @Test
    @DisplayName("distance between a point and itself is zero")
    void samepoint() {
        assertThat(DistanceCalculator.distanceMetres(12.9716, 77.5946, 12.9716, 77.5946))
                .isCloseTo(0d, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("MG Road to Indiranagar metro is roughly 3.7 km")
    void knownDistance() {
        double metres = DistanceCalculator.distanceMetres(12.9757, 77.6068, 12.9784, 77.6408);
        assertThat(metres).isBetween(3300d, 4100d);
    }

    @Test
    @DisplayName("bounding box fully contains the requested radius")
    void boundingBoxContainsRadius() {
        double lat = 12.9716, lon = 77.5946, radius = 500;
        double[] box = DistanceCalculator.boundingBox(lat, lon, radius);

        // A point exactly `radius` metres due north must fall inside the box.
        double northLat = lat + (radius / 111_320d);
        assertThat(northLat).isBetween(box[0], box[1]);
        assertThat(lon).isBetween(box[2], box[3]);
    }
}
