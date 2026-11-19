// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value=SlimeUnit.class,remap=false)
public abstract class OrdinarySlimePushHitMixin {
    @Redirect(method="push",remap=true,at=@At(value="INVOKE",target="Lcom/solegendary/reignofnether/unit/units/monsters/SlimeUnit;doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",remap=true),require=1)
    private boolean dreamseekers$ordinaryPrimary(SlimeUnit attacker,Entity target) { return OrdinarySkillHit.melee(attacker,target); }
}
