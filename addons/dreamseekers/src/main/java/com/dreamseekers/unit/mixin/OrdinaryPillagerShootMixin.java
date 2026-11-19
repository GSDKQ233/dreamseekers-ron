// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value=PillagerUnit.class,remap=false)
public abstract class OrdinaryPillagerShootMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="shootCrossbowProjectile",remap=true,
            at=@At("HEAD"),require=1)
    private void dreamseekers$actualAim(LivingEntity user,LivingEntity target,net.minecraft.world.entity.projectile.Projectile projectile,
                                     float angle,float velocity,org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if(user==(Object)this)OrdinarySkillHit.confirmSporeCrossbowAim(user,target,projectile);
    }
    @Redirect(method="shootCrossbowProjectile",remap=true,at=@At(value="INVOKE",
            target="Lcom/solegendary/reignofnether/unit/units/villagers/PillagerUnit;getProjectileShotVector(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;F)Lorg/joml/Vector3f;",remap=true),require=1)
    private org.joml.Vector3f dreamseekers$authorizedSporeAim(PillagerUnit source,LivingEntity user,
            net.minecraft.world.phys.Vec3 legacy,float angle,LivingEntity actualUser,LivingEntity actualTarget,
            net.minecraft.world.entity.projectile.Projectile projectile,float projectileAngle,float velocity) {
        var aim=source==(Object)this&&user==actualUser
                ?OrdinarySkillHit.sporeCrossbowAim(user,actualTarget,projectile,legacy):legacy;
        return source.getProjectileShotVector(user,aim,angle);
    }
    @Redirect(method="performCrossbowAttack",remap=true,at=@At(value="INVOKE",target="Lnet/minecraft/world/item/CrossbowItem;performShooting(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;FF)V",remap=true),require=1)
    private void dreamseekers$ordinaryCrossbow(Level level,LivingEntity shooter,InteractionHand hand,ItemStack stack,float velocity,float inaccuracy) {
        if(shooter!=(Object)this) { CrossbowItem.performShooting(level,shooter,hand,stack,velocity,inaccuracy);return; }
        OrdinarySkillHit.crossbow(shooter,()->CrossbowItem.performShooting(level,shooter,hand,stack,velocity,inaccuracy));
    }
}
