package com.dreamseekers.unit;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.CollisionFlyingWorker;
import com.dreamseekers.unit.flight.LowAltitudeMoveGoal;
import com.dreamseekers.unit.flight.FlyingReturnResourcesGoal;
import com.dreamseekers.unit.flight.FlyingBuildRepairGoal;
import com.dreamseekers.unit.flight.FlyingGatherResourcesGoal;
import com.dreamseekers.unit.flight.FlyingExploreBuildLocationGoal;
import com.dreamseekers.unit.flight.FlyingUsePortalGoal;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** RTS state composed into genuine PasterDream subclasses; no PasterDream source is copied. */
public final class RtsUnitState {
    public final MoveToTargetBlockGoal move;
    public final SelectedTargetGoal<LivingEntity> target;
    public final ReturnResourcesGoal returning;
    public final UsePortalGoal portal;
    public final net.minecraft.world.entity.ai.goal.Goal attack;
    public final MeleeAttackBuildingGoal attackBuilding;
    public final BuildRepairGoal build;
    public final GatherResourcesGoal gather;
    public final ExploreBuildLocationGoal explore;
    public final List<ItemStack> items = new ArrayList<>();
    public final ArrayList<Checkpoint> checkpoints = new ArrayList<>();
    public final Object2ObjectArrayMap<Ability, Float> cooldowns = Unit.createCooldownMap();
    public final Object2ObjectArrayMap<Ability, Integer> charges = new Object2ObjectArrayMap<>();
    public Abilities abilities = new Abilities();
    public Ability autocast;
    public BlockPos anchor = BlockPos.ZERO;
    public BlockPos attackMove;
    public LivingEntity follow;
    public boolean hold;
    /** Ephemeral authorization generation for a newly produced unit's one-shot rally; never persisted. */
    public long commandRevision;
    /** Changes only on ownership changes, not STOP; in-flight paid projectiles bind this epoch. */
    public long ownershipRevision;
    public int eatingTicks;
    public EnemySearchBehaviour search = EnemySearchBehaviour.NONE;
    private com.dreamseekers.skill.SkillState skills;
    public com.dreamseekers.skill.SkillState skills() {
        if (skills == null) skills = new com.dreamseekers.skill.SkillState((com.dreamseekers.skill.SkillUnit) mob);
        return skills;
    }
    private final Mob mob;
    private final boolean worker;
    private final boolean flying;
    private final boolean noncombat;

    public RtsUnitState(Mob mob, boolean worker) {
        this(mob, worker, mob instanceof CollisionFlyingWorker, false);
    }

    /** Isolated scout mode: real flying orders, no attack/worker goals or vanilla breeding. */
    public RtsUnitState(Mob mob, boolean worker, boolean flying, boolean noncombat) {
        this(mob,worker,flying,noncombat,-1);
    }
    /** Explicit ranged mode never inherits airborne melee building attacks. Existing units default unchanged. */
    public RtsUnitState(Mob mob,boolean worker,boolean flying,boolean noncombat,int rangedWindup) {
        this(mob,worker,flying,noncombat,rangedWindup,null);
    }
    /** Optional subtype-only building final-impact policy; legacy callers keep the original goal. */
    public RtsUnitState(Mob mob,boolean worker,boolean flying,boolean noncombat,int rangedWindup,
                        java.util.function.Function<Mob,MeleeAttackBuildingGoal> buildingFactory) {
        this.mob = mob;
        this.worker = worker;
        this.flying = flying;
        this.noncombat = noncombat;
        boolean flyingWorker = flying;
        move = flyingWorker ? new LowAltitudeMoveGoal(mob) : new MoveToTargetBlockGoal(mob, false, 0);
        target = mob instanceof TeamSafeCombatUnit ? new TeamSafeSelectedTargetGoal(mob) : new SelectedTargetGoal<>(mob, true, true);
        returning = flyingWorker ? new FlyingReturnResourcesGoal(mob) : new ReturnResourcesGoal(mob);
        portal = flyingWorker ? new FlyingUsePortalGoal(mob) : new UsePortalGoal(mob);
        attack = noncombat ? null : rangedWindup>=0 ? new UnitRangedAttackGoal<>(mob, rangedWindup) : (mob instanceof TeamSafeCombatUnit ? new TeamSafeMeleeAttackGoal(mob) : new MeleeAttackUnitGoal(mob, true));
        attackBuilding = worker || noncombat || rangedWindup>=0 ? null : buildingFactory!=null ? buildingFactory.apply(mob) : (mob instanceof TeamSafeCombatUnit ? new TeamSafeBuildingAttackGoal(mob) : new MeleeAttackBuildingGoal(mob));
        build = worker ? (flyingWorker ? new FlyingBuildRepairGoal(mob) : new BuildRepairGoal(mob)) : null;
        gather = worker ? (flyingWorker ? new FlyingGatherResourcesGoal(mob) : new GatherResourcesGoal(mob)) : null;
        explore = worker ? (flyingWorker ? new FlyingExploreBuildLocationGoal((WorkerUnit) mob) : new ExploreBuildLocationGoal((WorkerUnit) mob)) : null;
    }

    public void register(GoalSelector goals, GoalSelector targets) {
        if (!flying) goals.addGoal(1, new FloatGoal(mob));
        if (!noncombat) goals.addGoal(2, portal);
        if (attack != null) goals.addGoal(2, attack);
        if (!noncombat) goals.addGoal(2, returning);
        if (worker) {
            goals.addGoal(2, explore);
            goals.addGoal(2, build);
            goals.addGoal(2, gather);
        } else if (attackBuilding != null) {
            goals.addGoal(2, attackBuilding);
        }
        if (!noncombat) targets.addGoal(2, target);
        goals.addGoal(3, move);
        goals.addGoal(4, new RandomLookAroundUnitGoal(mob));
    }
}
