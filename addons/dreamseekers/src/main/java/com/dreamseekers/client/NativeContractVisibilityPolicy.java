package com.dreamseekers.client;

/** Pure distance-extension policy for the two original, unconverted contract target types. */
public final class NativeContractVisibilityPolicy {
    private NativeContractVisibilityPolicy() {}

    /** Both the exact runtime class and its matching native registry identity are required. */
    public static boolean isExactNativeTarget(String namespace, String path,
                                              boolean exactBeetleClass, boolean exactSnailClass) {
        return "pasterdream".equals(namespace)
                && ((exactBeetleClass && "black_beetle".equals(path))
                || (exactSnailClass && "basalt_snail".equals(path)));
    }

    /**
     * Extend only distance eligibility, never a hidden/fogged/out-of-border model. An existing
     * true result is preserved: this is not a replacement for renderer visibility checks.
     */
    public static boolean renderAtDistance(boolean original, boolean rtsView, boolean exactNativeTarget,
                                           boolean hidden, boolean insideBorder, boolean bright,
                                           double distanceSquared) {
        return original || (rtsView && exactNativeTarget && !hidden && insideBorder && bright
                && DreamUnitVisibilityPolicy.renderAtDistance(false, true, true, distanceSquared));
    }
}
