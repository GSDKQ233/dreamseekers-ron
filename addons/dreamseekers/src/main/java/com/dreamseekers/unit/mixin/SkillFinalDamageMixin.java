// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Vanilla damage path only. RoN's separate injected override calls the core bridge itself. */
@Mixin(LivingEntity.class)
public abstract class SkillFinalDamageMixin {
    @Redirect(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraftforge/common/ForgeHooks;onLivingHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",remap=false))
    private float dreamseekers$validateEnhancedFang(LivingEntity victim,DamageSource source,float amount) {
        float result=ForgeHooks.onLivingHurt(victim,source,amount);
        result=com.dreamseekers.skill.AaroncosCombat.validateAfterHurt(victim,source,result);
        result=com.dreamseekers.skill.ShadowGolemStrike.validateAfterHurt(victim,source,result);
        result=com.dreamseekers.skill.AaroncosVortex.validateAfterHurt(victim,source,result);
        result=com.dreamseekers.skill.AshEmberLanding.validateAfterHurt(victim,source,result);
        result=com.dreamseekers.skill.WindFangDamage.validateAfterHurt(victim,source,result);
        return com.dreamseekers.spore.SporeCombat.validateAfterHurt(victim,source,result);
    }
    @Redirect(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"),require=1)
    private float dreamseekers$actualMagicAbsorb(LivingEntity victim,DamageSource source,float amount) {
        return FinalUnitDamageHooks.magicAbsorb(victim,source,amount);
    }
    @Redirect(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",remap=false))
    private float dreamseekers$afterFinalDamage(LivingEntity victim,DamageSource source,float amount) {
        var ticket=FinalUnitDamageHooks.begin(victim,source);
        var write=com.dreamseekers.skill.FriendlyDebtContract.captureVanillaTicket(victim,source,ticket);
        var vortexWrite=com.dreamseekers.skill.AaroncosVortex.captureVanillaTicket(victim,source,ticket);
        var reactiveWrite=com.dreamseekers.skill.DamageTriggeredDefense.captureVanillaTicket(victim,source,ticket);
        float finalAmount=ForgeHooks.onLivingDamage(victim,source,amount);
        float result=ticket.resolve(finalAmount,finalAmount);
        com.dreamseekers.skill.FriendlyDebtContract.resolvedVanillaTicket(write,result);
        com.dreamseekers.skill.AaroncosVortex.resolvedVanillaTicket(vortexWrite,result);
        com.dreamseekers.skill.DamageTriggeredDefense.resolvedVanillaTicket(reactiveWrite,result);
        return result;
    }
    @Redirect(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"),require=1)
    private void dreamseekers$ordinaryFangHealthWrite(LivingEntity victim,float health,DamageSource source,float amount) {
        com.solegendary.reignofnether.api.HealthDamageWriters.withReceipt(victim,source,()->
        com.dreamseekers.skill.FriendlyDebtContract.vanillaHealthWrite(victim,source,()->
                com.dreamseekers.skill.AaroncosVortex.vanillaHealthWrite(victim,source,()->
                com.dreamseekers.skill.DamageTriggeredDefense.vanillaHealthWrite(victim,source,()->
                com.solegendary.reignofnether.api.HealthWriteObservers.write(victim,source,health,
                        ()->com.solegendary.reignofnether.api.HealthDamageWriters.write(victim,source,health))))));
    }
}
