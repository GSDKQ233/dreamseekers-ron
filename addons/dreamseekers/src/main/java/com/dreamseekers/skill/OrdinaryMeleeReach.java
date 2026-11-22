// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.LivingEntity;

/** Raw geometry from RoN 1.20.1-1.5.0b (67db0a71), AbstractMeleeAttackUnitGoal#getAttackReachSqr.
 * The goal's current return value can be masked by DreamMeleeApproachMixin on occlusion.
 * Keep visibility, cast radii and comparison/validity checks with each caller.
 */
final class OrdinaryMeleeReach {
    private OrdinaryMeleeReach() { }

    static double rawSquared(LivingEntity source, AttackerUnit attacker, LivingEntity target) {
        return rawSquared(source.getBbWidth(), attacker.getBonusMeleeRange(), target.getBbWidth(),
                target instanceof Unit unit ? unit.getBonusMeleeRangeForAttackers() : 0.0F);
    }

    static double rawSquared(float sourceWidth, float sourceBonus, float targetWidth, float targetBonus) {
        float width = sourceWidth + sourceBonus;
        float effectiveTargetWidth = targetWidth + targetBonus;
        // Keep every operation in native float order before widening the result to double.
        return width * 2.0F * width * 2.0F + effectiveTargetWidth;
    }
}
