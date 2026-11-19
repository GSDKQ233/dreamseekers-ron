// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Untracked projectiles are no-ops; tracked owner A->B->A invalidates launch authority permanently. */
@Mixin(Projectile.class)
public abstract class SkillProjectileOwnerMixin {
    @Inject(method="setOwner",at=@At("RETURN"),require=1)
    private void dreamseekers$ownerEpoch(Entity owner,CallbackInfo callback) {
        OrdinarySkillHit.projectileOwnerChanged((Projectile)(Object)this);
        com.dreamseekers.skill.BeetleGuard.projectileOwnerChanged((Projectile)(Object)this);
    }
}
