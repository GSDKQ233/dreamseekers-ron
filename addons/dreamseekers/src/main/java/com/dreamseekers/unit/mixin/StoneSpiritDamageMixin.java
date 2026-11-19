// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.StoneSpiritThrowerUnit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.pasterdream.entity.SmallStoneSpiritEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Only new paid combat ID takes normal arrow damage. Wild and old-worker native immunity are unchanged. */
@Mixin(SmallStoneSpiritEntity.class)
public abstract class StoneSpiritDamageMixin extends Monster {
    protected StoneSpiritDamageMixin(EntityType<? extends Monster> type,Level level){super(type,level);}
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void dreamseekers$ordinaryCombatDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> callback){
        if((Object)this instanceof StoneSpiritThrowerUnit && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow)callback.setReturnValue(super.hurt(source,amount));
    }
}
