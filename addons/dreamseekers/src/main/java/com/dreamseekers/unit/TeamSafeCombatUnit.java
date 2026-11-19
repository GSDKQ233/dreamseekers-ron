// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
/** Opt-in stricter paid-unit combat boundary; existing/wild units are not globally altered. */
public interface TeamSafeCombatUnit extends DreamRtsUnit {
    @Override default void setUnitAttackTarget(LivingEntity target) {
        DreamRtsUnit.super.setUnitAttackTarget(canHarm(target) ? target : null);
        getTargetGoal().forced=false;
    }
    @Override default void setUnitAttackTargetForced(LivingEntity target) {
        setUnitAttackTarget(target);
        getTargetGoal().forced=canHarm(target)&&getTargetGoal().getTarget()==target;
        // Core may skip fullReset for an already selected target. A valid explicit
        // ATTACK still supersedes HOLD; rejected/automatic guard selection does not.
        if(getTargetGoal().forced)setHoldPosition(false);
    }
    @Override default void setAttackBuildingTarget(net.minecraft.core.BlockPos pos, boolean forced) {
        if(getAttackBuildingGoal() instanceof TeamSafeBuildingAttackGoal goal) {
            goal.setBuildingTarget(pos);goal.forced=goal.getBuildingTarget()!=null&&forced;
        }
    }
    default boolean canHarmOwner(String targetOwner) {
        String owner = getOwnerName();
        if (owner.isBlank() || targetOwner == null || targetOwner.isBlank() || owner.equals(targetOwner)) return false;
        return !(mob().level().isClientSide() ? AlliancesClient.isAllied(owner, targetOwner)
                : AlliancesServerEvents.isAllied(owner, targetOwner));
    }
    /** No faction equality shortcut: two opposing players using Dreamseekers remain enemies. */
    default boolean canHarm(Entity target) {
        if(target instanceof com.dreamseekers.spore.JungleSporeCarrier carrier)
            return com.dreamseekers.spore.SporeCombat.canHarm(mob(),carrier);
        return mob().isAlive() && target instanceof LivingEntity living && living.isAlive() && !target.isRemoved()
                && target.level() == mob().level() && target instanceof Unit unit && canHarmOwner(unit.getOwnerName());
    }
}
