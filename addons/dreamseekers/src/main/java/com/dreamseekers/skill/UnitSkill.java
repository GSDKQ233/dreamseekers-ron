// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
/** Approved skill protocol identifiers. Reserved economy/research/building interfaces stay separate. */
public enum UnitSkill {
    GEL_GUARD(360, 6, 160),
    JELLY_FLOW(240, 8, 120),
    FOX_BLESSING(480, 8, 120),
    WIND_DASH(440, 7, 100),
    COORDINATED_MARK(400, 10, 120),
    FOX_DREAM_SEED(480, 10, 200),
    FIREFLY_RUSH(500, 0, 100),
    BEETLE_ADVANCE(320, 0, 80),
    SNAIL_SHELL_FORMATION(480, 3, 100),
    STONE_HEAVY_SHOT(360, 10, 20),
    SHADOW_CORROSION_TOUCH(320, 0, 100),
    SQUEAL_SUPPRESSION_WAVE(360, 10, 80),
    TERRORBEAK_DREAD_ROAR(400, 3, 30),
    FRIENDLY_DEBT_CONTRACT(400, 10, 120),
    SHADOW_HAND_GRIP(440, 6, 20),
    ASH_EMBER_LANDING(440, 8, 100),
    SHADOW_GOLEM_STRIKE(480, 3, 20),
    MOTHER_BROOD(900, 0, 600),
    WEEPING_ECHOES(900, 0, 600),
    AARONCOS_LEFT_PRESS(400, 8, 12),
    AARONCOS_RIGHT_SHOCK(600, 6, 20),
    MOTHER_NEST(0, 0, 0),
    AARONCOS_RIGHT_VORTEX(800, 10, 120);
    private final int cooldown, range, duration;
    UnitSkill(int cooldown, int range, int duration) { this.cooldown=cooldown; this.range=range; this.duration=duration; }
    public int cooldownTicks() { return cooldown; }
    public int range() { return range; }
    public int durationTicks() { return duration; }
    public boolean supportsAutocast() { return SkillAutocastPolicy.supports(this); }
    public boolean defaultAutocast() { return SkillAutocastPolicy.defaultEnabled(this); }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
