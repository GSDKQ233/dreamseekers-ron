// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.WindFangDamage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Before RoN's fixed-damage override; Wind or proven ordinary-born effects/Ascendancy opt in. */
// Mixin 0.8.5 inserts HEAD callbacks in ascending priority: run before core's cancelling 1000.
@Mixin(value=EvokerFangs.class,priority=900)
public abstract class WindFangsDamageMixin {
    @Inject(method="dealDamageTo",at=@At("HEAD"),cancellable=true,require=1)
    private void dreamseekers$ordinaryFangDamage(LivingEntity victim,CallbackInfo callback) {
        if(WindFangDamage.hurtIfEnhanced((EvokerFangs)(Object)this,victim))callback.cancel();
    }
}
