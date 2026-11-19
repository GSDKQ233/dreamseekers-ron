// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.StrayUnit;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import com.solegendary.reignofnether.unit.units.piglins.HeadhunterUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** The exact factory argument, not any arbitrary entity spawned by a Join-event callback. */
@Mixin(value={SkeletonUnit.class,StrayUnit.class,MilitiaUnit.class,HeadhunterUnit.class},remap=false)
public abstract class OrdinaryRangedLaunchMixin {
    @Redirect(method="performUnitRangedAttack",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",remap=true),require=1)
    private boolean dreamseekers$ordinaryShot(Level level,Entity projectile,LivingEntity actualTarget,float velocity) {
        return OrdinarySkillHit.spawn(level,projectile,(LivingEntity)(Object)this,actualTarget);
    }
}
