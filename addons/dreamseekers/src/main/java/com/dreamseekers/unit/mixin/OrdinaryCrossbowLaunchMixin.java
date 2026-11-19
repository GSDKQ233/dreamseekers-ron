// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(CrossbowItem.class)
public abstract class OrdinaryCrossbowLaunchMixin {
    @Shadow private static void shootProjectile(Level level,LivingEntity shooter,InteractionHand hand,
            ItemStack crossbow,ItemStack projectile,float pitch,boolean creative,float velocity,float inaccuracy,float angle) {
        throw new AssertionError();
    }
    @Shadow private static AbstractArrow getArrow(Level level,LivingEntity shooter,ItemStack crossbow,ItemStack ammunition) {
        throw new AssertionError();
    }
    @Redirect(method="performShooting",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/CrossbowItem;shootProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;FZFFF)V"),require=1)
    private static void dreamseekers$singleShot(Level level,LivingEntity shooter,InteractionHand hand,
            ItemStack crossbow,ItemStack projectile,float pitch,boolean creative,float velocity,float inaccuracy,float angle) {
        OrdinarySkillHit.crossbowProjectile(shooter,()->shootProjectile(level,shooter,hand,crossbow,projectile,pitch,creative,velocity,inaccuracy,angle));
    }
    @Redirect(method="shootProjectile",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/CrossbowItem;getArrow(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/projectile/AbstractArrow;"),require=1)
    private static AbstractArrow dreamseekers$actualArrow(Level level,LivingEntity shooter,ItemStack crossbow,ItemStack ammunition) {
        return OrdinarySkillHit.crossbowArrow(shooter,()->getArrow(level,shooter,crossbow,ammunition));
    }
    @Redirect(method="shootProjectile",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),require=1)
    private static boolean dreamseekers$ordinaryArrow(Level level,Entity projectile) {
        return OrdinarySkillHit.spawnCrossbow(level,projectile);
    }
}
