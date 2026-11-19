// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinaryFangOrigin;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value=EvokerUnit.class,remap=false)
public abstract class OrdinaryFangLaunchMixin {
    @Redirect(method="createEvokerFang",at=@At(value="NEW",
            target="(Lnet/minecraft/world/level/Level;DDDFILnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/entity/projectile/EvokerFangs;",remap=true),require=1)
    private net.minecraft.world.entity.projectile.EvokerFangs dreamseekers$freezeBeforeFang(Level level,double x,double y,double z,
                        float angle,int warmup,net.minecraft.world.entity.LivingEntity owner) {
        return OrdinaryFangOrigin.constructSporeFang(owner,()->new net.minecraft.world.entity.projectile.EvokerFangs(level,x,y,z,angle,warmup,owner));
    }
    @Redirect(method="createEvokerFang",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",remap=true),require=1)
    private boolean dreamseekers$ordinaryFang(Level level,Entity fang) {
        return OrdinaryFangOrigin.spawn(level,fang,(EvokerUnit)(Object)this);
    }
}
