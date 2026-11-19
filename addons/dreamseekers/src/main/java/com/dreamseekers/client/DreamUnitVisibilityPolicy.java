package com.dreamseekers.client;

/** Small, client-library-free policy for distance eligibility and the existing RTS fog contract. */
public final class DreamUnitVisibilityPolicy {
    private DreamUnitVisibilityPolicy() {}

    // Core camera supports terrain +100, pitch down to 20 degrees, zoom up to 90.
    // At an aspect ratio up to 4:1 the far visible ground radius is below 512:
    // hypot((100 + 45) / sin(20 degrees), 180) is approximately 461 blocks.
    // This only extends the distance test, never frustum/tracking/fog or world loading.
    public static final double RTS_DISTANCE_FLOOR = 512.0;

    public static boolean renderAtDistance(boolean original, boolean rtsView, boolean dreamUnit, double distanceSquared) {
        return original || (rtsView && dreamUnit && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0 && distanceSquared < RTS_DISTANCE_FLOOR * RTS_DISTANCE_FLOOR);
    }

    public static boolean cancelModel(boolean dreamUnit, boolean levelPresent, boolean insideBorder, boolean bright) {
        return dreamUnit && ((levelPresent && !insideBorder) || !bright);
    }
}
