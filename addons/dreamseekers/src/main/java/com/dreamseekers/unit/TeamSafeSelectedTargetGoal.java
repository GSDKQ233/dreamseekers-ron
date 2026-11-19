// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;
import com.solegendary.reignofnether.unit.goals.SelectedTargetGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
/** Filters even direct/forced target assignment, unlike the base game's deliberately friendly-fire target goal. */
public final class TeamSafeSelectedTargetGoal extends SelectedTargetGoal<LivingEntity> {
    private final TeamSafeCombatUnit unit;
    public TeamSafeSelectedTargetGoal(Mob mob) { super(mob, true, true); unit = (TeamSafeCombatUnit)mob; }
    @Override public void setTarget(LivingEntity target) { super.setTarget(unit.canHarm(target) ? target : null); }
    public void invalidateChangedRelationship() {
        if (super.getTarget() != null && !unit.canHarm(super.getTarget())) {
            super.setTarget(null); unit.mob().getNavigation().stop();
        }
    }
    @Override public LivingEntity getTarget() {
        return unit.canHarm(super.getTarget()) ? super.getTarget() : null;
    }
}
