// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinaryAttackGate;
import com.solegendary.reignofnether.unit.units.piglins.BlazeUnit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;
import com.solegendary.reignofnether.unit.units.piglins.WildfireUnit;
import com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit;
import com.solegendary.reignofnether.unit.units.monsters.NecromancerUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Exact ordinary factories, only while their actual ordinary goal scope is active.
 * Firewall/Molten Bomb/summons are separate methods and remain unchanged; no new Bone provenance.
 */
@Mixin(value={BlazeUnit.class,GhastUnit.class,WildfireUnit.class,WindcallerUnit.class,NecromancerUnit.class},remap=false)
public abstract class DreadRoarNativeLaunchMixin {
    @Redirect(method="performUnitRangedAttack*",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",remap=true),require=1)
    private boolean dreamseekers$beforeOrdinaryPublication(Level level,Entity projectile) {
        return OrdinaryAttackGate.publish((LivingEntity)(Object)this,projectile,()->level.addFreshEntity(projectile));
    }
}
