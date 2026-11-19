// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.NativeOrdinaryDebtHit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Vanilla override, not Mob's inherited path. Wild golems and unrelated callers stay pass-through. */
@Mixin(IronGolem.class)
public abstract class OrdinaryIronGolemDebtHitMixin {
    @Redirect(method="doHurtTarget",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),require=1)
    private boolean dreamseekers$exactGolemHit(Entity victim,DamageSource damage,float amount) {
        return NativeOrdinaryDebtHit.hurt((Mob)(Object)this,victim,damage,()->victim.hurt(damage,amount));
    }
    @Redirect(method="doHurtTarget",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"),require=1)
    private void dreamseekers$excludeAnimationCallback(Level level,Entity source,byte event) {
        NativeOrdinaryDebtHit.animation((Mob)(Object)this,()->level.broadcastEntityEvent(source,event));
    }
}
