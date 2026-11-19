// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;
import com.solegendary.reignofnether.unit.goals.MeleeAttackUnitGoal;
import net.minecraft.world.entity.Mob;
/** The legacy continue path repaths unconditionally; this opt-in goal keeps HOLD stationary. */
public final class TeamSafeMeleeAttackGoal extends MeleeAttackUnitGoal {
    private final TeamSafeCombatUnit unit;
    private final Mob body;
    public TeamSafeMeleeAttackGoal(Mob mob) { super(mob,true); unit=(TeamSafeCombatUnit)mob; body=mob; }
    /** A real skill melee attempt spends exactly the ordinary goal's own cooldown, without an APS mutation. */
    public void spendAttackCooldown() { ticksUntilNextAttack=this.adjustedTickDelay(getAttackInterval()); }
    private int transformationFloor;
    @Override public void tickAttackCooldown() {
        super.tickAttackCooldown();
        if(transformationFloor>0){transformationFloor--;ticksUntilNextAttack=Math.max(ticksUntilNextAttack,transformationFloor);}
    }
    public int transformationCooldown() { return Math.max(0,ticksUntilNextAttack); }
    public void inheritTransformationCooldown(int remaining) { transformationFloor=Math.max(transformationFloor,Math.max(0,remaining)); ticksUntilNextAttack=Math.max(ticksUntilNextAttack,transformationFloor); }
    @Override public boolean canUse() {
        return unit.getHoldPosition() ? unit.canHarm(body.getTarget()) : super.canUse();
    }
    @Override public boolean canContinueToUse() {
        if(unit.getHoldPosition()) { body.getNavigation().stop(); return unit.canHarm(body.getTarget()); }
        return super.canContinueToUse();
    }
}
