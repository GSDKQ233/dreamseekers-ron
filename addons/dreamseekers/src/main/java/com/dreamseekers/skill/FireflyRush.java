// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.FireflyWorker;
import com.dreamseekers.unit.flight.FlyingReturnResourcesGoal;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Personal speed exception, not a worker/economy upgrade. Manual Rush pauses work; the
 * separate passive lease is valid only on a real, already running native delivery path.
 * Neither mode owns a resource credit, orders or cargo. */
public final class FireflyRush {
    public static final double MOVEMENT_BONUS=.35;
    private static final Map<FireflyWorker,Rush> ACTIVE=new IdentityHashMap<>();
    private static final Map<FireflyWorker,ReturnBurst> RETURNS=new IdentityHashMap<>();
    private static final Map<FireflyWorker,WorkPath> WORK_PATHS=new IdentityHashMap<>();
    private FireflyRush() { }
    private record Rush(SkillIdentity.Stamp source,long expiresAt) {
        boolean valid() {
            var body=source.entity();
            return source.valid()&&body.isAddedToWorld()&&body.getUUID().equals(source.uuid())
                    &&source.level().getEntity(body.getId())==body&&source.level().getGameTime()<expiresAt;
        }
    }
    private record WorkPath(SkillIdentity.Stamp source,MoveToTargetBlockGoal goal,Path path,long revision) {
        boolean current(FireflyWorker worker) {
            return source.valid()&&worker.isAddedToWorld()&&worker.getUUID().equals(source.uuid())
                    &&source.level().getEntity(worker.getId())==worker
                    &&worker.rts().commandRevision==revision&&worker.getNavigation().getPath()==path
                    &&workIntent(worker,goal);
        }
    }
    private record ReturnBurst(SkillIdentity.Stamp source,FlyingReturnResourcesGoal goal,
                               BuildingPlacement depot,long revision,long expiresAt) {
        boolean current(FireflyWorker worker) {
            return source.level().getGameTime()<expiresAt && source.valid()
                    && worker.rts().commandRevision==revision && goal.getBuildingTarget()==depot
                    && returnLeg(worker,goal);
        }
    }
    private static boolean server(LivingEntity body) {
        return body.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    /** Work suspension is exclusively manual. In particular, a return boost may never block depositItems. */
    public static boolean active(LivingEntity body) {
        if(!(body instanceof FireflyWorker worker)||!server(body))return false;
        Rush rush=ACTIVE.get(worker);return rush!=null&&rush.valid();
    }
    public static int remainingTicks(FireflyWorker worker) {
        if(!active(worker))return 0;
        return (int)Math.max(0,ACTIVE.get(worker).expiresAt-worker.level().getGameTime());
    }
    public static boolean returningBoost(FireflyWorker worker) {
        if(worker==null||!server(worker))return false;
        ReturnBurst burst=RETURNS.get(worker);
        return burst!=null && burst.current(worker);
    }
    static double movementBonus(LivingEntity target) {
        return active(target)||target instanceof FireflyWorker worker&&returningBoost(worker)?MOVEMENT_BONUS:0;
    }
    private static boolean returnLeg(FireflyWorker worker,FlyingReturnResourcesGoal goal) {
        WorkPath work=WORK_PATHS.get(worker);
        return work!=null && work.goal==goal && worker.getReturnResourcesGoal()==goal && work.current(worker)
                && worker.getFollowTarget()==null && worker.getMoveGoal().getMoveTarget()==null
                && !worker.getHoldPosition() && worker.getAttackMoveTarget()==null && worker.getTarget()==null
                && worker.getUsePortalGoal().getBuildingTarget()==null
                && !workIntent(worker,worker.rts().build) && !workIntent(worker,worker.rts().explore)
                && !workIntent(worker,worker.rts().gather) && goal.travellingToDepot()
                // This is the actual native "take this load home" threshold, including research.
                // Waiting for absolute inventory capacity would miss almost every ordinary harvest.
                && Unit.atThresholdResources(worker);
    }
    /** Called only after the native return goal's tick. Never searches for a depot, dispatches a
     * return, stops/replaces a path, alters cargo, or blocks the native deposit/resume/Shift step. */
    public static boolean afterReturnStep(Mob body,FlyingReturnResourcesGoal goal) {
        if(!(body instanceof FireflyWorker worker)||!server(worker)||active(worker)||RETURNS.containsKey(worker))return false;
        if(!worker.rts().skills().autocastEnabled(UnitSkill.FIREFLY_RUSH)
                ||worker.rts().skills().cooldownTicks(UnitSkill.FIREFLY_RUSH)>0||!returnLeg(worker,goal)
                ||worker.goalSelector.getRunningGoals().noneMatch(running->running.getGoal()==goal))return false;
        var owner=SkillService.passiveCommander(worker,UnitSkill.FIREFLY_RUSH);
        if(owner==null)return false;
        var burst=new ReturnBurst(SkillIdentity.capture(worker),goal,goal.getBuildingTarget(),
                worker.rts().commandRevision,worker.level().getGameTime()+UnitSkill.FIREFLY_RUSH.durationTicks());
        if(!burst.current(worker)||!SkillService.commitPassive(worker,UnitSkill.FIREFLY_RUSH,owner,false))return false;
        RETURNS.put(worker,burst);
        SkillEffects.refresh(worker); // same strongest-only +35% ground/flight exception as manual
        return true;
    }
    /** Goal preemption, depot arrival or an explicit replacement command immediately retires only
     * this speed contribution. Do not stop navigation or restore a snapshot of an earlier order. */
    public static void endReturn(Mob body) {
        if(body instanceof FireflyWorker worker && server(worker) && RETURNS.remove(worker)!=null)
            SkillEffects.refresh(worker);
    }
    /** Direct/setter-only commands may change a running path without calling the old goal's stop. */
    public static void beforeNavigation(Mob body) {
        if(body instanceof FireflyWorker worker && server(worker)) {
            var burst=RETURNS.get(worker);
            if(burst!=null&&!burst.current(worker))endReturn(worker);
        }
    }
    static SkillResult start(FireflyWorker worker) {
        if(!server(worker)||!worker.isAlive()||worker.isRemoved()||!worker.isAddedToWorld()
                ||worker.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        var level=(ServerLevel)worker.level();
        if(level.getEntity(worker.getId())!=worker||level.getEntity(worker.getUUID())!=worker)return SkillResult.INVALID_SOURCE;
        if(active(worker))return SkillResult.ALREADY_ACTIVE;
        var stamp=SkillIdentity.capture(worker);
        if(!stamp.valid())return SkillResult.INVALID_SOURCE;
        ACTIVE.put(worker,new Rush(stamp,level.getGameTime()+UnitSkill.FIREFLY_RUSH.durationTicks()));
        // Only stop the still-owned work path, once. A MOVE/FOLLOW path is not a retreat destination.
        WorkPath work=WORK_PATHS.get(worker);
        if(work!=null&&work.current(worker)
                &&worker.getNavigation() instanceof com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation navigation)
            navigation.stopWorkForRush(work.path);
        SkillEffects.refresh(worker);
        return SkillResult.SUCCESS;
    }
    private static boolean workIntent(FireflyWorker worker,MoveToTargetBlockGoal goal) {
        var state=worker.rts();
        if(goal==state.gather)return state.gather.getTargetResourceName()!=ResourceName.NONE
                ||state.gather.getGatherTarget()!=null||state.gather.getTargetFarm()!=null
                ||!state.gather.data.todoGatherTargets.isEmpty();
        if(goal==state.build)return state.build.getBuildingTarget()!=null||!state.build.queuedBuildings.isEmpty();
        if(goal==state.returning)return state.returning.hasReturnOrder();
        return goal==state.explore&&(state.explore.getMoveTarget()!=null||!state.explore.getFogQueuedBuildings().isEmpty());
    }
    /** Native no-target gathering tick only advances its idle counter; it must remain eligible for queued orders. */
    public static boolean pausesGatherTick(Mob body) {
        return body instanceof FireflyWorker worker&&active(worker)&&workIntent(worker,worker.rts().gather);
    }
    /** Only current live work intent delays Shift dispatch; idle casts and completed MOVE legs do not. */
    public static boolean hasPausedWork(FireflyWorker worker) {
        if(!active(worker))return false;
        var state=worker.rts();
        return workIntent(worker,state.gather)||workIntent(worker,state.build)
                ||workIntent(worker,state.returning)||workIntent(worker,state.explore);
    }
    /** Called after a real work start, never from a target setter alone or from a speed query. */
    public static void workPathStarted(Mob body,MoveToTargetBlockGoal goal) {
        if(!(body instanceof FireflyWorker worker)||!server(body)||!worker.isAddedToWorld()
                ||worker.getOwnerName().isBlank()||active(worker)||!workIntent(worker,goal))return;
        Path path=worker.getNavigation().getPath();
        if(path!=null)WORK_PATHS.put(worker,new WorkPath(SkillIdentity.capture(worker),goal,path,worker.rts().commandRevision));
    }
    /** Native block updates can replace a path without invoking the work goal's start(). */
    public static Object beforeRecompute(Mob body) {
        if(!(body instanceof FireflyWorker worker)||!server(body))return null;
        WorkPath work=WORK_PATHS.get(worker);return work!=null&&work.current(worker)?work:null;
    }
    public static void afterRecompute(Mob body,Object token) {
        if(!(body instanceof FireflyWorker worker)||!server(body)||!(token instanceof WorkPath work)
                ||WORK_PATHS.get(worker)!=work||!work.source.valid()||!worker.isAddedToWorld()
                ||worker.rts().commandRevision!=work.revision||!workIntent(worker,work.goal))return;
        Path path=worker.getNavigation().getPath();
        if(path==null)WORK_PATHS.remove(worker,work);
        else WORK_PATHS.put(worker,new WorkPath(work.source,work.goal,path,work.revision));
    }
    static void tick(ServerLevel level) {
        if(!level.getServer().isSameThread())return;
        for(var entry:List.copyOf(ACTIVE.entrySet()))if(entry.getKey().level()==level) {
            if(!entry.getValue().valid())ACTIVE.remove(entry.getKey(),entry.getValue());
            SkillEffects.refresh(entry.getKey());
        }
        for(var worker:List.copyOf(RETURNS.keySet()))if(worker.level()==level)beforeNavigation(worker);
        WORK_PATHS.entrySet().removeIf(entry->entry.getValue().source.level()==level
                &&(!entry.getValue().source.valid()||!entry.getKey().isAddedToWorld()
                    ||entry.getKey().rts().commandRevision!=entry.getValue().revision));
    }
    static void clear(LivingEntity body) {
        if(body instanceof FireflyWorker worker&&server(body)) { ACTIVE.remove(worker);RETURNS.remove(worker);WORK_PATHS.remove(worker); }
    }
    static void stop() {
        for(FireflyWorker worker:List.copyOf(ACTIVE.keySet()))SkillEffects.removeModifiers(worker);
        for(FireflyWorker worker:List.copyOf(RETURNS.keySet()))SkillEffects.removeModifiers(worker);
        ACTIVE.clear();RETURNS.clear();WORK_PATHS.clear();
    }
}
