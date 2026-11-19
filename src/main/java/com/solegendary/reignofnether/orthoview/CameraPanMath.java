package com.solegendary.reignofnether.orthoview;

/** Pure arithmetic for one requested camera movement, independent of Minecraft client linkage. */
public final class CameraPanMath {
    private CameraPanMath() { }
    public static int duration(int requestedTicks) { return Math.max(1, requestedTicks); }
    public static float step(float from, float to, int requestedTicks) {
        return (to - from) / duration(requestedTicks);
    }
    public static int lockDuration(int panTicks, int extraLockTicks) {
        return (int) Math.min(Integer.MAX_VALUE, (long) duration(panTicks) + Math.max(0, extraLockTicks));
    }
}
