// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.TerrorbeakDreadRoar;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Ordinary building impact only. Never stops the navigation goal or snapshots its remaining CD. */
@Mixin(value=MeleeAttackBuildingGoal.class,remap=false)
public abstract class DreadRoarBuildingAttackMixin extends MoveToTargetBlockGoal {
    protected DreadRoarBuildingAttackMixin(Mob mob) { super(mob,false,0); }
    @Inject(method="doBuildingAttack",at=@At("HEAD"),cancellable=true,require=1)
    private void dreamseekers$ordinaryBuildingStart(CallbackInfo callback) {
        if(TerrorbeakDreadRoar.hasDisarm(mob))callback.cancel();
    }
    @Redirect(method="doBuildingAttack",at=@At(value="INVOKE",target="Lcom/solegendary/reignofnether/building/BuildingPlacement;destroyRandomBlocks(D)V"),require=1)
    private void dreamseekers$ordinaryBuildingCommit(BuildingPlacement building,double amount) {
        if(!TerrorbeakDreadRoar.hasDisarm(mob))building.destroyRandomBlocks(amount);
    }
}
