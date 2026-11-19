package com.solegendary.reignofnether.startpos;

/** Display clock only; the server alone starts, cancels, or completes a match. */
public final class StartPosCountdown {
    public static final int INACTIVE = -1;
    public static final int DURATION = 100;
    private int remaining = INACTIVE;

    public static int check(int ticks) {
        if (ticks < INACTIVE || ticks > DURATION)
            throw new IllegalArgumentException("Invalid lobby countdown: " + ticks);
        return ticks;
    }

    public void sync(int ticks) { remaining = check(ticks); }
    public int remaining() { return remaining; }

    public void tick() {
        if (remaining > 0) remaining--;
    }
}
