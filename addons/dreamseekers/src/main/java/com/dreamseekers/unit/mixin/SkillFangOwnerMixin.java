// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinaryFangOrigin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** EvokerFangs is not a Projectile: track only the new Bone origin record, without changing Wind. */
@Mixin(EvokerFangs.class)
public abstract class SkillFangOwnerMixin {
    @Inject(method="setOwner",at=@At("RETURN"),require=1)
    private void dreamseekers$fangOwner(LivingEntity owner,CallbackInfo callback) {
        OrdinaryFangOrigin.ownerChanged((EvokerFangs)(Object)this);
    }
}
