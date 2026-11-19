// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.skill.ManagedAttackSlowdown;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Concrete inherited implementation of AttackerUnit's identity default; not an interface injector.
 * Non-Units and bodies without this exact live managed lease return the original factor untouched.
 */
@Mixin(LivingEntity.class)
public abstract class SkillAttackSlowdownMixin {
    public void refreshManagedAttackRate() {
        com.dreamseekers.skill.SkillEffects.refreshGuardedAttackRate((LivingEntity)(Object)this);
    }
    public float getSkillAttackSlowdownFactor(float nativeFactor) {
        return ManagedAttackSlowdown.factor((LivingEntity)(Object)this,nativeFactor);
    }
}
