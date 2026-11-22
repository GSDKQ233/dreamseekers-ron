// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.DreamRtsUnit;
import com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Being within melee reach on the other side of a floor or wall is not arrival. */
@Mixin(value = AbstractMeleeAttackUnitGoal.class, remap = false)
public abstract class DreamMeleeApproachMixin {
    @Shadow @Final protected Mob mob;

    @Inject(method = "getAttackReachSqr", at = @At("RETURN"), cancellable = true, require = 1)
    private void dreamseekers$requireExposedMeleePosition(LivingEntity target, CallbackInfoReturnable<Double> cir) {
        if (!(mob instanceof DreamRtsUnit unit) || unit.getAttackGoal() != (Object) this) return;
        if (!(mob.getNavigation() instanceof GroundPathNavigation)
                && !(mob.getNavigation() instanceof LowAltitudeFlyingNavigation)) return;

        // The original goal uses the same squared reach for stopping its path and spending its
        // cooldown. While close but occluded, leave both decisions negative and let the existing
        // navigator follow the detour. Sensing caches this ray for the rest of the mob's AI tick.
        if (mob.distanceToSqr(target) <= cir.getReturnValueD() && !mob.getSensing().hasLineOfSight(target))
            cir.setReturnValue(-1.0D);
    }
}
