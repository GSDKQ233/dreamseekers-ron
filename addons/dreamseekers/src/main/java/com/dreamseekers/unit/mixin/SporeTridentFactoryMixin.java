// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.solegendary.reignofnether.unit.units.piglins.HeadhunterUnit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value=HeadhunterUnit.class,remap=false)
public abstract class SporeTridentFactoryMixin {
    @Redirect(method="performUnitRangedAttack",at=@At(value="NEW",
            target="(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/projectile/ThrownTrident;",remap=true),require=1)
    private ThrownTrident dreamseekers$freezeBeforeFactory(Level level,LivingEntity source,ItemStack stack,
                                                         LivingEntity actualTarget,float velocity) {
        return OrdinarySkillHit.constructSporeShot(source,actualTarget,()->new ThrownTrident(level,source,stack));
    }
}
