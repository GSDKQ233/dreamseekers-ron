// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
/** New shadow-hand-only final building impact guard; native entity melee guards do not cover block attacks. */
public final class ShadowHandBuildingAttackGoal extends TeamSafeBuildingAttackGoal {
    public ShadowHandBuildingAttackGoal(Mob mob){super(mob);}
    @Override protected void doBuildingAttack(){
        if(!(mob instanceof ShadowHandUnit ghost)||!(mob.level() instanceof ServerLevel level)
                ||!level.getServer().isSameThread()||!ghost.isAlive()||level.getEntity(ghost.getUUID())!=ghost
                ||level.getEntity(ghost.getId())!=ghost||ghost.hasEffect(MobEffectRegistrar.STUN.get())
                ||ghost.hasEffect(MobEffectRegistrar.FREEZE.get())||buildingTarget==null
                ||!valid(buildingTarget)||!isAttacking())return;
        // At least one actual target surface must be visible. Hitting another building or a terrain
        // wall first is not permission to damage this target. No ray creates or destroys terrain.
        for(var block:buildingTarget.getBlocks()){
            var pos=block.getBlockPos();if(!buildingTarget.isPosPartOfBuilding(pos,true))continue;
            var hit=level.clip(new ClipContext(ghost.getEyePosition(),pos.getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,ghost));
            if(hit.getType()==HitResult.Type.BLOCK&&buildingTarget.isPosPartOfBuilding(hit.getBlockPos(),true)){
                super.doBuildingAttack();return;
            }
        }
    }
}
