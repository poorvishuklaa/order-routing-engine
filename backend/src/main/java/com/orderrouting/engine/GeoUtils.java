package com.orderrouting.engine;

/** Distance helpers. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_MILES = 3958.8;

    private GeoUtils() {
    }

    /** Great-circle distance in miles between two lat/long points (Haversine formula). */
    public static double distanceMiles(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_MILES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static String zip3(String postalCode) {
        if (postalCode == null || postalCode.length() < 3) {
            return null;
        }
        return postalCode.substring(0, 3);
    }
}
