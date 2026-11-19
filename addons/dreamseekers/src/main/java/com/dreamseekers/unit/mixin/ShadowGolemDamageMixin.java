// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.ShadowGolemUnit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.pasterdream.entity.ShadowGolemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Only new paid combat ID takes normal arrow damage. Wild native immunity are unchanged. */
@Mixin(ShadowGolemEntity.class)
public abstract class ShadowGolemDamageMixin extends Monster {
    protected ShadowGolemDamageMixin(EntityType<? extends Monster> type,Level level){super(type,level);}
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void dreamseekers$ordinaryCombatDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> callback){
        if((Object)this instanceof ShadowGolemUnit && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow)callback.setReturnValue(super.hurt(source,amount));
    }
}
