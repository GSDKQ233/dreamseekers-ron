// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value=SkeletonUnit.class,remap=false)
public abstract class SporeSkeletonArrowFactoryMixin {
    @Redirect(method="performUnitRangedAttack",at=@At(value="INVOKE",
            target="Lcom/solegendary/reignofnether/unit/units/monsters/SkeletonUnit;getArrow(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/projectile/AbstractArrow;",remap=true),require=1)
    private AbstractArrow dreamseekers$freezeBeforeFactory(SkeletonUnit source,ItemStack ammunition,float power,
                                                         LivingEntity actualTarget,float velocity) {
        return OrdinarySkillHit.constructSporeShot(source,actualTarget,()->((SporeSkeletonArrowAccess)source).dreamseekers$sporeArrow(ammunition,power));
    }
}
