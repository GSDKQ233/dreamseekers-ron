// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.dreamseekers.spore.JungleSporeCarrier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Projectile.class)
public abstract class SporeProjectileTargetMixin {
    @Inject(method="canHitEntity",at=@At("HEAD"),cancellable=true,require=1)
    private void dreamseekers$onlyIntendedCarrier(Entity entity,CallbackInfoReturnable<Boolean> callback) {
        Projectile projectile=(Projectile)(Object)this;
        // Client prediction keeps native collision; it has no server lease/order/shot authorization.
        if(!projectile.level().isClientSide()&&entity instanceof JungleSporeCarrier carrier&&carrier.wasLeaseInitialized()
                &&!OrdinarySkillHit.mayHitSpore(projectile,carrier))callback.setReturnValue(false);
    }
}
