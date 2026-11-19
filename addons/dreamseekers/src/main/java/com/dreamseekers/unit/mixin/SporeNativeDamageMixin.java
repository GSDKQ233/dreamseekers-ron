// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.spore.SporeCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.pasterdream.entity.SporeEntityEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Only the already-entered precise ordinary carrier impact bypasses native Arrow immunity. */
@Mixin(SporeEntityEntity.class)
public abstract class SporeNativeDamageMixin extends PathfinderMob {
    protected SporeNativeDamageMixin(EntityType<? extends PathfinderMob> type,Level level) { super(type,level); }
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true,require=1)
    private void dreamseekers$authorizedArrow(DamageSource source,float amount,CallbackInfoReturnable<Boolean> callback) {
        if((Object)this instanceof JungleSporeCarrier carrier&&source.getDirectEntity() instanceof AbstractArrow
                &&SporeCombat.ordinaryArrowAllowed(carrier,source))callback.setReturnValue(super.hurt(source,amount));
    }
}
