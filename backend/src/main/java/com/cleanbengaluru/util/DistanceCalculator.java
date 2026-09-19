package com.cleanbengaluru.util;

/**
 * Great-circle distance between two lat/lon points using the Haversine formula.
 *
 * WHY HAVERSINE: the Earth is (near enough) a sphere, so we cannot use Pythagoras on
 * raw degrees — one degree of longitude is ~111 km at the equator but shrinks towards
 * the poles. Haversine accounts for that curvature and is accurate to a few metres
 * over city distances, which is far more than we need.
 *
 *   a = sin^2(dLat/2) + cos(lat1) * cos(lat2) * sin^2(dLon/2)
 *   c = 2 * atan2(sqrt(a), sqrt(1-a))
 *   d = R * c
 *
 * TIME COMPLEXITY: O(1) per pair.
 *
 * WHY THE BOUNDING BOX: Haversine cannot be served by a B-tree index, so running it
 * over every row is O(n). Instead we first ask MySQL for rows inside a lat/lon
 * rectangle (which the composite index on (latitude, longitude) can use), then run
 * Haversine only on that small candidate set. The rectangle is slightly larger than
 * the circle we want, so we still filter the corners out in Java.
 */
public final class DistanceCalculator {

    /** Mean radius of the Earth in metres. */
    private static final double EARTH_RADIUS_METRES = 6_371_000d;

    private DistanceCalculator() {
    }

    /** Distance in METRES between two WGS-84 coordinates. */
    public static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METRES * c;
    }

    /**
     * The lat/lon rectangle that fully contains a circle of `radiusMetres` around a point.
     * Returns { minLat, maxLat, minLon, maxLon }.
     *
     * 1 degree of latitude  is always ~111_320 m.
     * 1 degree of longitude is ~111_320 m * cos(latitude), so it narrows as you move north.
     */
    public static double[] boundingBox(double latitude, double longitude, double radiusMetres) {
        double metresPerDegreeLat = 111_320d;
        double metresPerDegreeLon = 111_320d * Math.cos(Math.toRadians(latitude));

        // Guard against division by ~0 very close to the poles.
        if (Math.abs(metresPerDegreeLon) < 1d) {
            metresPerDegreeLon = 1d;
        }

        double deltaLat = radiusMetres / metresPerDegreeLat;
        double deltaLon = radiusMetres / metresPerDegreeLon;

        return new double[]{
                latitude - deltaLat,
                latitude + deltaLat,
                longitude - deltaLon,
                longitude + deltaLon
        };
    }
}
