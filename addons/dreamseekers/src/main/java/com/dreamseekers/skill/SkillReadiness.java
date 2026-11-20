package com.dreamseekers.skill;

import com.solegendary.reignofnether.client.ActiveClientTickClock;

/** Private HUD clock: an absent, obsolete, or expired view is not a real cooldown. */
public record SkillReadiness(int ticks) {
    public static final SkillReadiness WAITING = new SkillReadiness(-1);
    public static final SkillReadiness READY = new SkillReadiness(0);
    public static SkillReadiness at(ActiveClientTickClock clock, long received, long duration) {
        if (!clock.fresh(received, 40)) return WAITING;
        int remaining = (int)Math.min(Integer.MAX_VALUE, clock.remaining(received, duration));
        return remaining == 0 ? READY : new SkillReadiness(remaining);
    }
    public boolean waiting() { return ticks < 0; }
    public boolean ready() { return ticks == 0; }
    public int seconds() { return ticks <= 0 ? 0 : 1 + (ticks - 1) / 20; }
    public String badge() { return waiting() ? "?" : ready() ? "" : seconds() + "s"; }
    /** Only for the native numeric API, which has no pending state; never use as the
     * displayed number or to decide whether a target click can be consumed. */
    public int nativeCooldown(int max) { return waiting() ? max : ticks; }
}
