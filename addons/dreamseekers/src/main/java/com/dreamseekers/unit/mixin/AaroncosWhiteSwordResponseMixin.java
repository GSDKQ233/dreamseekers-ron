// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** No blanket status immunity: only this original weapon's parent-class boss-silence effect is skipped. */
@Mixin(value=net.pasterdream.procedures.WhiteSwordRainPr1Procedure.class,remap=false)
public abstract class AaroncosWhiteSwordResponseMixin {
    @Redirect(method="execute",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z",remap=true),remap=false,require=1)
    private static boolean dreamseekers$noImplicitBossWeakness(LivingEntity target,MobEffectInstance effect){
        if(target instanceof ControlledAaroncosHand&&effect.getEffect()==net.pasterdream.init.PasterdreamModMobEffects.SHADOW_SILENCE_BUFF.get())return false;
        return target.addEffect(effect); // Ordinary Bind and all wild behavior retained.
    }
}
