// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.dreamseekers.spore.SporeCombat;
import com.solegendary.reignofnether.unit.units.neutral.KillerRabbitUnit;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Only this proven pre-hurt callback window; no-frame sounds and every unrelated target stay legacy. */
@Mixin(value=KillerRabbitUnit.class,remap=false)
public abstract class OrdinaryRabbitSoundMixin {
    @Redirect(method="doHurtTarget",remap=true,at=@At(value="INVOKE",
            target="Lcom/solegendary/reignofnether/unit/units/neutral/KillerRabbitUnit;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",remap=true),require=1)
    private void dreamseekers$excludeSoundCallback(KillerRabbitUnit source,SoundEvent sound,float volume,float pitch) {
        OrdinarySkillHit.rabbitSound(source,()->SporeCombat.rabbitSound(source,()->source.playSound(sound,volume,pitch)));
    }
    @Redirect(method="doHurtTarget",remap=true,at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",remap=true),require=1)
    private boolean dreamseekers$lastOrdinaryHurt(net.minecraft.world.entity.Entity target,net.minecraft.world.damagesource.DamageSource damage,float amount) {
        return com.dreamseekers.skill.OrdinaryAttackGate.finalMelee((KillerRabbitUnit)(Object)this,target,damage,()->target.hurt(damage,amount));
    }
}
