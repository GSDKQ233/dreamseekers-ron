// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.client;

/**
 * Client-library-free elapsed clock, independent of RoN's visual world time.
 * Each owning client advances its instance exactly once at ClientTick.END.
 * A changed context or a true endTick result requires discarding that client's views.
 */
public final class ActiveClientTickClock {
    private Object level;
    private Object connection;
    private long ticks;

    /** Identity, not equality: reconnects and replacement levels invalidate old views. */
    public boolean context(Object nextLevel, Object nextConnection) {
        if (level == nextLevel && connection == nextConnection) return false;
        level = nextLevel;
        connection = nextConnection;
        ticks = 0;
        return true;
    }

    public void clear() {
        level = null;
        connection = null;
        ticks = 0;
    }

    /** True only on rollover; the caller must clear its views before reading their stamps. */
    public boolean endTick(boolean paused) {
        if (paused || level == null || connection == null) return false;
        if (ticks == Long.MAX_VALUE) {
            ticks = 0;
            return true;
        }
        ticks++;
        return false;
    }

    public long now() { return ticks; }

    public boolean queryDue(long queried, long interval) {
        if (interval <= 0) throw new IllegalArgumentException("interval must be positive");
        return queried < 0 || queried > ticks || ticks - queried >= interval;
    }

    public boolean fresh(long received, long ttl) {
        return ttl >= 0 && received >= 0 && received <= ticks && ticks - received <= ttl;
    }

    /** Relative durations avoid overflow from computing now + duration near Long.MAX_VALUE. */
    public long remaining(long received, long duration) {
        if (duration <= 0) return 0;
        if (received < 0 || received > ticks) return duration; // invalid stamp never unlocks a cooldown
        return Math.max(0, duration - (ticks - received));
    }
}
