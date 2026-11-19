// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import java.util.function.IntSupplier;

/** Client target-distance policy only; server cast validation remains authoritative. */
final class SkillTargetRange {
    private SkillTargetRange() { }

    /** Read the current owner research range only for Heavy Shot, never for other skills. */
    static boolean withinRange(UnitSkill skill, double distanceSquared, IntSupplier stoneRange) {
        int range=skill==UnitSkill.STONE_HEAVY_SHOT?stoneRange.getAsInt():skill.range();
        return distanceSquared <= (double)range*range;
    }
}
