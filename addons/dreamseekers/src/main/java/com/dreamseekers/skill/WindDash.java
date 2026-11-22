// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.TeamSafeMeleeAttackGoal;
import com.dreamseekers.unit.WindKnightUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Server-only ephemeral dash/impact state. Paths move through normal navigation/collision;
 * neither paths, hit tokens nor knockback records survive save/load. Cooldown is owned by SkillState.
 */
public final class WindDash {
    private WindDash() { }
    // Movement and command parameters; no building-specific amplification is applied here.
    public static final double NAVIGATION_SPEED=2.5, MAX_PATH_DISTANCE=12, TETHER_RANGE=12, STRIKE_RANGE=2;
    public static final int MAX_TICKS=60, REPATH_TICKS=5, STALLED_TICKS=12;
    public static final float DAMAGE_MULTIPLIER=1.5f;
    public static final String COMMAND_KEY="wind_command";
    public static final int COMMAND_RANGE=3, COMMAND_TARGETS=4, COMMAND_TICKS=100, KNOCKBACK_TICKS=5;
    public static final double COMMAND_MOVEMENT=.20, COMMAND_DAMAGE=.10, KNOCKBACK_DISTANCE=1.5;
    private static final Map<WindKnightUnit,Dash> DASHES=new HashMap<>();
    private static final Map<WindKnightUnit,Strike> STRIKES=new HashMap<>();
    private static final Map<LivingEntity,Knockback> KNOCKBACKS=new HashMap<>();

    private static final class Dash {
        final WindKnightUnit source;
        final ServerPlayer commander;
        final SkillIdentity.Stamp caster,target;
        final long commandRevision,startedAt;
        Path plannedPath,ownedPath;
        Vec3 previous,standpoint;
        long nextRepath;
        double traveled;
        int stalled;
        boolean cancelled;
        Dash(ServerPlayer commander,WindKnightUnit source,LivingEntity target,Path path) {
            this.commander=commander;this.source=source;caster=SkillIdentity.capture(source);this.target=SkillIdentity.capture(target);
            commandRevision=source.rts().commandRevision;startedAt=source.level().getGameTime();
            plannedPath=path;previous=source.position();nextRepath=startedAt+REPATH_TICKS;
            standpoint=path==null?source.position():path.getEntityPosAtNode(source,path.getNodeCount()-1);
        }
        boolean valid() {
            return !cancelled&&DASHES.get(source)==this&&caster.valid()&&target.valid()
                    &&source.rts().commandRevision==commandRevision&&!disabled(source)
                    &&!UnitServerEvents.hasPendingImmediateAction(source.getId())
                    &&source.canHarm(target.entity())&&visibleToCommander(commander,source,target.entity())
                    &&source.distanceToSqr(target.entity())<=TETHER_RANGE*TETHER_RANGE
                    &&source.level().getGameTime()-startedAt<=MAX_TICKS;
        }
        boolean reach() { return source.distanceToSqr(target.entity())<=STRIKE_RANGE*STRIKE_RANGE
                &&source.hasLineOfSight(target.entity()); }
    }
    private static final class Strike {
        final Dash dash;
        boolean callEntered,frameBound,frameActive,hurtEntered,claimed,success;
        DamageSource damageSource;
        Strike(Dash dash) { this.dash=dash; }
        boolean matches(LivingEntity target,DamageSource source) {
            return frameActive&&damageSource==source&&dash.target.entity()==target;
        }
    }
    private static final class Knockback {
        final SkillIdentity.Stamp caster,target;
        final Vec3 step;
        int remaining=KNOCKBACK_TICKS;
        long lastTick=Long.MIN_VALUE;
        Knockback(SkillIdentity.Stamp caster,SkillIdentity.Stamp target,Vec3 step) {
            this.caster=caster;this.target=target;this.step=step;
        }
        boolean valid() { return caster.valid()&&target.valid()
                &&((WindKnightUnit)caster.entity()).canHarm(target.entity()); }
    }
    private static boolean disabled(WindKnightUnit unit) {
        return unit.hasEffect(MobEffectRegistrar.STUN.get())||unit.hasEffect(MobEffectRegistrar.FREEZE.get())
                ||unit.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get())||unit.isPassenger();
    }
    public static boolean isActive(WindKnightUnit source) {
        return DASHES.containsKey(source)&&!UnitServerEvents.hasPendingImmediateAction(source.getId());
    }
    public static int ticksRemaining(WindKnightUnit source) {
        Dash dash=DASHES.get(source);return dash==null||!isActive(source)?0:(int)Math.max(0,MAX_TICKS-(source.level().getGameTime()-dash.startedAt));
    }
    public static int knockbackTicks(LivingEntity target) {
        Knockback effect=KNOCKBACKS.get(target);return effect!=null&&effect.valid()?effect.remaining:0;
    }
    private static boolean validStandpoint(WindKnightUnit source,LivingEntity target,Vec3 feet) {
        var level=source.level();BlockPos pos=BlockPos.containing(feet);
        return level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)
                &&feet.y>=level.getMinBuildHeight()&&feet.y+source.getBbHeight()<level.getMaxBuildHeight()
                &&feet.distanceToSqr(target.position())<=STRIKE_RANGE*STRIKE_RANGE
                &&level.noCollision(source,source.getBoundingBox().move(feet.subtract(source.position())))
                &&level.clip(new ClipContext(feet.add(0,source.getEyeHeight(),0),target.getEyePosition(),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source)).getType()==HitResult.Type.MISS;
    }
    /** A separate vanilla planner cannot mutate/reuse the current order's path if the cast is rejected.
     * The enemy's occupied cell is not the destination: a wide knight needs a real, clear melee stance.
     */
    private static Path plan(WindKnightUnit source,LivingEntity target,double remainingDistance) {
        if(!(source.level() instanceof ServerLevel)||!(source.getNavigation() instanceof GroundPathNavigation))return null;
        GroundPathNavigation planner=new GroundPathNavigation(source,source.level());
        planner.setCanOpenDoors(false);planner.setCanPassDoors(true);planner.setCanFloat(true);
        Set<BlockPos> endpoints=new LinkedHashSet<>();BlockPos center=target.blockPosition();
        // Path.getEntityPosAtNode uses this width-dependent center (1.0 for this 1.6-wide unit), not +0.5.
        double offset=(int)(source.getBbWidth()+1f)*.5;
        for(int y=-1;y<=1;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            BlockPos node=center.offset(x,y,z);
            if(validStandpoint(source,target,new Vec3(node.getX()+offset,node.getY(),node.getZ()+offset)))endpoints.add(node);
        }
        if(endpoints.isEmpty())return null;
        Path path=planner.createPath(endpoints,0);
        if(path==null||!path.canReach()||path.getEndNode()==null||path.getNodeCount()==0
                ||!validStandpoint(source,target,path.getEntityPosAtNode(source,path.getNodeCount()-1)))return null;
        Vec3 previous=source.position();double distance=0;
        for(int i=0;i<path.getNodeCount();i++) {
            Vec3 point=path.getEntityPosAtNode(source,i);
            distance+=previous.distanceTo(point);previous=point;
        }
        return Double.isFinite(distance)&&distance<=remainingDistance?path:null;
    }
    /** The exact issuing player's fog column, not the caster's physical line of sight or a
     * ranged reveal. Kept separate from target.isAlive so a just-committed lethal strike can
     * recheck visibility for its receipt without consulting a replacement body or viewer. */
    private static boolean visibleToCommander(ServerPlayer viewer,WindKnightUnit source,LivingEntity target) {
        if(viewer==null||!viewer.isAlive()||viewer.isRemoved()||viewer.serverLevel()!=source.level()
                ||!viewer.getGameProfile().getName().equals(source.getOwnerName())
                ||target.level()!=viewer.serverLevel()||target.isRemoved()||target.isInvisible()||target.isSpectator())return false;
        if(FogOfWarServerEvents.isVisionTableRestrictedFor(viewer)&&viewer.serverLevel().dimension()!=Level.OVERWORLD)return false;
        BlockPos pos=target.blockPosition();
        return FogOfWarServerEvents.isBlockVisibleFor(viewer,pos.getX(),pos.getZ());
    }
    static SkillResult start(ServerPlayer viewer,WindKnightUnit source,LivingEntity target) {
        if(isActive(source)||STRIKES.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        if(disabled(source)||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.DISABLED;
        if(!(target instanceof Unit)||!source.canHarm(target)||target.isSpectator()||!(source.level() instanceof ServerLevel level)
                ||level.getEntity(target.getUUID())!=target||level.getEntity(target.getId())!=target)return SkillResult.INVALID_TARGET;
        if(source.distanceToSqr(target)>(double)UnitSkill.WIND_DASH.range()*UnitSkill.WIND_DASH.range())return SkillResult.RANGE;
        if(!visibleToCommander(viewer,source,target))return SkillResult.HIDDEN;
        SkillIdentity.Stamp caster=SkillIdentity.capture(source),victim=SkillIdentity.capture(target);
        long command=source.rts().commandRevision,revision=source.rts().skills().revision();
        boolean alreadyInReach=source.onGround()&&source.hasLineOfSight(target)
                &&validStandpoint(source,target,source.position());
        Path path=alreadyInReach?null:plan(source,target,MAX_PATH_DISTANCE);
        if(path==null&&!alreadyInReach)return SkillResult.NO_PATH;
        if(!caster.valid())return SkillResult.INVALID_SOURCE;
        if(source.rts().commandRevision!=command||source.rts().skills().revision()!=revision
                ||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.REPLAY;
        if(!victim.valid()||!source.canHarm(target))return SkillResult.INVALID_TARGET;
        if(!visibleToCommander(viewer,source,target))return SkillResult.HIDDEN;
        if(source.distanceToSqr(target)>(double)UnitSkill.WIND_DASH.range()*UnitSkill.WIND_DASH.range())return SkillResult.RANGE;
        // Discard the executed order and its async callbacks, not explicitly shift-queued future commands.
        Unit.fullResetBehaviours(source);
        if(!caster.valid())return SkillResult.INVALID_SOURCE;
        if(source.rts().commandRevision!=command+1||source.rts().skills().revision()!=revision
                ||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.REPLAY;
        if(!victim.valid()||!source.canHarm(target)||disabled(source))return SkillResult.INVALID_TARGET;
        if(!visibleToCommander(viewer,source,target))return SkillResult.HIDDEN;
        Dash dash=new Dash(viewer,source,target,path);DASHES.put(source,dash);return SkillResult.SUCCESS;
    }
    public static Goal goal(WindKnightUnit source) {
        return new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP)); }
            @Override public boolean canUse() { return isActive(source); }
            @Override public boolean canContinueToUse() { return isActive(source); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void start() { navigate(source,true); }
            @Override public void tick() { navigate(source,false); }
            // A replacement command may already own navigation. Never stop it from an old goal's stop().
            @Override public void stop() { }
        };
    }
    private static void navigate(WindKnightUnit source,boolean starting) {
        Dash dash=DASHES.get(source);if(dash==null)return;
        if(!dash.valid()) { cancel(source);return; }
        source.getLookControl().setLookAt(dash.target.entity(),30,30);
        if(dash.reach()) { stopOwnedNavigation(dash);return; }
        long now=source.level().getGameTime();
        // Vanilla's waypoint tolerance grows with body width. Finish only the validated final short
        // approach through its ordinary MoveControl/collision, instead of declaring an out-of-reach hit.
        if(!starting&&dash.ownedPath!=null&&source.getNavigation().isDone()&&dash.standpoint!=null
                &&source.position().distanceToSqr(dash.standpoint)<=2
                &&validStandpoint(source,dash.target.entity(),dash.standpoint)) {
            source.getMoveControl().setWantedPosition(dash.standpoint.x,dash.standpoint.y,dash.standpoint.z,NAVIGATION_SPEED);
            return;
        }
        if(!starting&&now<dash.nextRepath)return;
        Path path=starting?dash.plannedPath:plan(source,dash.target.entity(),MAX_PATH_DISTANCE-dash.traveled);
        dash.nextRepath=now+REPATH_TICKS;
        if(path==null||!source.getNavigation().moveTo(path,NAVIGATION_SPEED)) { cancel(source);return; }
        dash.ownedPath=source.getNavigation().getPath();
        dash.standpoint=path.getEntityPosAtNode(source,path.getNodeCount()-1);
    }
    /** Host calls before ordinary AI, so a revoked command/identity cannot move another tick. */
    public static void beforeTick(WindKnightUnit source) {
        Dash dash=DASHES.get(source);if(dash!=null&&!dash.valid())cancel(source);
    }
    /** Host calls after physical navigation movement, never teleports to manufacture a hit. */
    public static void afterTick(WindKnightUnit source) {
        Dash dash=DASHES.get(source);if(dash==null)return;
        if(!dash.valid()) { cancel(source);return; }
        double moved=source.position().distanceTo(dash.previous);dash.previous=source.position();dash.traveled+=moved;
        if(!Double.isFinite(dash.traveled)||dash.traveled>MAX_PATH_DISTANCE) { cancel(source);return; }
        dash.stalled=moved>.01?0:dash.stalled+1;
        if(dash.reach()) { strike(dash);return; }
        if(dash.stalled>=STALLED_TICKS)cancel(source);
    }
    private static void stopOwnedNavigation(Dash dash) {
        WindKnightUnit source=dash.source;
        if(source.rts().commandRevision==dash.commandRevision
                ||(dash.ownedPath!=null&&source.getNavigation().getPath()==dash.ownedPath)) {
            source.getNavigation().stop();source.getMoveControl().operation=MoveControl.Operation.WAIT;
            source.setSpeed(0);Vec3 velocity=source.getDeltaMovement();
            source.setDeltaMovement(new Vec3(0,velocity.y,0));
        }
    }
    public static void cancel(WindKnightUnit source) {
        Dash dash=DASHES.remove(source);if(dash==null)return;
        dash.cancelled=true;stopOwnedNavigation(dash);
    }
    /** Called only for a genuine replacement command, not a queued Shift intent. */
    public static void commandChanged(WindKnightUnit source) {
        if(DASHES.containsKey(source)) { source.rts().commandRevision++;cancel(source); }
    }
    private static void strike(Dash dash) {
        if(!dash.valid()||!dash.reach()) { cancel(dash.source);return; }
        WindKnightUnit source=dash.source;
        Strike strike=new Strike(dash);STRIKES.put(source,strike);
        ((TeamSafeMeleeAttackGoal)source.getAttackGoal()).spendAttackCooldown();
        source.swing(InteractionHand.MAIN_HAND);
        try {
            source.doHurtTarget(dash.target.entity());
        } finally {
            STRIKES.remove(source,strike);
            if(DASHES.get(source)==dash)cancel(source); // miss, evasion, cancellation and zero never rally
        }
        // The normal hurt() knockback consumes RoN's one-use ignore marker before the special event.
        if(strike.success)addKnockback(dash);
    }
    /** The host forbids ordinary/reentrant melee during the charge; one exact skill attempt may enter. */
    public static boolean allowMelee(WindKnightUnit source,Entity target) {
        Strike strike=STRIKES.get(source);
        if(strike!=null) {
            if(strike.callEntered||target!=strike.dash.target.entity()||!strike.dash.valid())return false;
            strike.callEntered=true;return true;
        }
        return !DASHES.containsKey(source);
    }
    /** Mob.doHurtTarget redirect encloses the real Entity.hurt call, preserving evasion/enchantments. */
    public static boolean hurtFromMob(Mob source,Entity target,DamageSource damage,float amount) {
        Strike strike=source instanceof WindKnightUnit knight?STRIKES.get(knight):null;
        if(strike==null||!strike.callEntered||strike.frameBound||target!=strike.dash.target.entity())
            return target.hurt(damage,amount);
        strike.frameBound=true;strike.frameActive=true;strike.damageSource=damage;
        try { return target.hurt(damage,amount); }
        finally { strike.frameActive=false;strike.damageSource=null; }
    }
    private static Strike matching(LivingEntity target,DamageSource damage) {
        if(!(damage.getEntity() instanceof WindKnightUnit source))return null;
        Strike strike=STRIKES.get(source);return strike!=null&&strike.matches(target,damage)?strike:null;
    }
    /** First entry is the real call; any same-DS/same-victim recursive hurt within it is rejected. */
    public static boolean rejectRepeatedHurt(LivingEntity target,DamageSource damage) {
        Strike strike=matching(target,damage);if(strike==null)return false;
        if(strike.hurtEntered)return true;
        strike.hurtEntered=true;return !strike.dash.valid();
    }
    static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity target,DamageSource damage) {
        Strike strike=matching(target,damage);
        if(strike==null||!strike.hurtEntered)return null;
        // A matching but revoked frame remains managed and returns zero; never leak back into legacy damage.
        if(strike.claimed)return new FinalUnitDamageHooks.OrdinaryAttack() {
            public float beforeArmor(float amount) { return 0; }
            public FinalUnitDamageHooks.Finalizer prepareFinal() { return amount->0; }
        };
        strike.claimed=true;
        Impact impact=new Impact(strike);
        return new FinalUnitDamageHooks.OrdinaryAttack() {
            @Override public float beforeArmor(float amount) { return impact.validBeforeWrite()?amount*DAMAGE_MULTIPLIER:0; }
            @Override public float validateAfterHurt(float amount) { return impact.validBeforeWrite()?amount:0; }
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return SkillEffects.prepareDashFinal(target,damage,impact); }
        };
    }
    static final class Impact {
        private final Strike strike;
        private boolean reported;
        Impact(Strike strike) { this.strike=strike; }
        boolean validBeforeWrite() { return !reported&&strike.dash.valid()&&strike.dash.reach(); }
        void applied(float healthLoss,float shieldLoss) {
            if(reported)return;reported=true;
            Dash dash=strike.dash;
            if(!(healthLoss>0||shieldLoss>0)||dash.cancelled||DASHES.get(dash.source)!=dash
                    ||!dash.caster.valid()||!dash.target.identityValid()
                    ||dash.source.rts().commandRevision!=dash.commandRevision||disabled(dash.source)
                    ||UnitServerEvents.hasPendingImmediateAction(dash.source.getId())
                    ||!dash.source.canHarmOwner(dash.target.owner())
                    ||!visibleToCommander(dash.commander,dash.source,dash.target.entity()))return;
            // A real lethal write is accepted despite !isAlive; removal/transfer/replacement is not.
            strike.success=true;DASHES.remove(dash.source,dash);stopOwnedNavigation(dash);
            rally(dash.source);
        }
    }
    private static void rally(WindKnightUnit source) {
        var level=(ServerLevel)source.level();
        var candidates=level.getEntitiesOfClass(LivingEntity.class,source.getBoundingBox().inflate(COMMAND_RANGE),
                target->target!=source&&target instanceof Unit&&!(target instanceof WorkerUnit)&&!target.isSpectator()
                        &&SkillEffects.friendly(source,target)&&level.getEntity(target.getUUID())==target
                        &&source.distanceToSqr(target)<=(double)COMMAND_RANGE*COMMAND_RANGE);
        candidates.sort(Comparator.comparingDouble((LivingEntity target)->source.distanceToSqr(target))
                .thenComparing(target->target.getUUID().toString()));
        int added=0;
        for(LivingEntity target:candidates) {
            if(rallyOne(source,target)&&++added>=COMMAND_TARGETS)break;
        }
    }
    private static boolean rallyOne(WindKnightUnit source,LivingEntity target) {
        boolean applied=SkillEffects.addBuff(source,target,COMMAND_KEY,BuffKind.MOVEMENT_SPEED,COMMAND_MOVEMENT,COMMAND_TICKS);
        if(SkillService.blessingCombatUnit(target))applied|=SkillEffects.addBuff(source,target,COMMAND_KEY,
                BuffKind.ATTACK_DAMAGE,COMMAND_DAMAGE,COMMAND_TICKS);
        return applied;
    }
    /** Called ONLY by the exact native ordinary melee receipt after verified positive HP/Gel loss.
     * The enemy must still be the same living focus. No new attack, movement, or attack interval. */
    static void passiveReceipt(WindKnightUnit source,LivingEntity victim,ServerPlayer owner) {
        if(owner==null || !(source.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || source.getTarget()!=victim || !source.canHarm(victim)
                || !SkillAutocastPolicy.observable(owner,victim) || !SkillAutocastPolicy.sees(source,victim)
                || !SkillAutocastPolicy.attackReach(source,source,victim)
                || source.getFollowTarget()!=null || source.getMoveGoal().getMoveTarget()!=null
                || AttackerUnit.isAttackingBuilding(source) || SkillAutocastPolicy.disabledFighter(source)
                || DASHES.containsKey(source) || STRIKES.containsKey(source)
                || SkillService.passiveCommander(source,UnitSkill.WIND_DASH)!=owner)return;
        // A lone knight never burns the manual positioning skill for an empty rally.
        // Drop an overloaded local query rather than picking an arbitrary iteration-order prefix.
        List<LivingEntity> candidates=new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class),source.getBoundingBox().inflate(COMMAND_RANGE),
                t->t!=source && t instanceof Unit && !(t instanceof WorkerUnit) && SkillEffects.friendly(source,t)
                        && source.distanceToSqr(t)<=(double)COMMAND_RANGE*COMMAND_RANGE,candidates,65);
        if(candidates.size()>64)return;
        candidates.removeIf(t->!canReceiveRally(source,owner,t));
        if(candidates.isEmpty())return;
        candidates.sort(Comparator.comparingDouble((LivingEntity t)->source.distanceToSqr(t))
                .thenComparing(t->t.getUUID().toString()));
        if(!SkillService.commitPassive(source,UnitSkill.WIND_DASH,owner,false))return;
        int added=0;
        for(LivingEntity t:candidates)
            if(canReceiveRally(source,owner,t)&&rallyOne(source,t)&&++added>=COMMAND_TARGETS)break;
    }
    private static boolean canReceiveRally(WindKnightUnit source,ServerPlayer owner,LivingEntity t) {
        return source.distanceToSqr(t)<=(double)COMMAND_RANGE*COMMAND_RANGE
                && SkillEffects.friendly(source,t) && SkillAutocastPolicy.observable(owner,t)
                && SkillAutocastPolicy.sees(source,t) && t.getAttribute(Attributes.MOVEMENT_SPEED)!=null
                && !SkillEffects.hasNamedBuff(t,COMMAND_KEY,BuffKind.MOVEMENT_SPEED);
    }
    private static void addKnockback(Dash dash) {
        if(!dash.caster.valid()||!dash.target.valid()||!dash.source.canHarm(dash.target.entity())
                ||!visibleToCommander(dash.commander,dash.source,dash.target.entity()))return;
        LivingEntity target=dash.target.entity();
        Vec3 away=target.position().subtract(dash.source.position()).multiply(1,0,1);
        if(away.lengthSqr()<1e-8)return;
        // Forge/other-mod vetoes (including freeze/shields) remain honored, but never allow an oversized proc.
        var event=ForgeHooks.onLivingKnockBack(target,(float)KNOCKBACK_DISTANCE,-away.x,-away.z);
        if(event.isCanceled()||!dash.caster.valid()||!dash.target.valid()||!dash.source.canHarm(target)
                ||!visibleToCommander(dash.commander,dash.source,target)
                ||!Float.isFinite(event.getStrength())||event.getStrength()<=0
                ||!Double.isFinite(event.getRatioX())||!Double.isFinite(event.getRatioZ()))return;
        Vec3 direction=new Vec3(-event.getRatioX(),0,-event.getRatioZ());
        if(direction.lengthSqr()<1e-8)return;
        double resistance=target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if(!Double.isFinite(resistance))return;
        double distance=Math.min(KNOCKBACK_DISTANCE,event.getStrength())*(1-Math.max(0,Math.min(1,resistance)));
        if(distance<=0)return;
        KNOCKBACKS.put(target,new Knockback(dash.caster,dash.target,direction.normalize().scale(distance/KNOCKBACK_TICKS)));
    }
    static void tick(ServerLevel level) {
        for(Dash dash:List.copyOf(DASHES.values()))if(dash.source.level()==level&&!dash.valid())cancel(dash.source);
        for(var entry:List.copyOf(KNOCKBACKS.entrySet())) {
            Knockback effect=entry.getValue();LivingEntity target=entry.getKey();
            if(target.level()!=level||KNOCKBACKS.get(target)!=effect)continue;
            if(!effect.valid()) { KNOCKBACKS.remove(target,effect);continue; }
            if(effect.lastTick==level.getGameTime())continue;
            effect.lastTick=level.getGameTime();
            // Consume the scheduled segment before collision callbacks. A wall blocks it; no later catch-up teleport.
            effect.remaining--;target.move(MoverType.SELF,effect.step);
            if(effect.remaining<=0||!effect.valid())KNOCKBACKS.remove(target,effect);
        }
    }
    static void clear(LivingEntity entity) {
        for(Dash dash:List.copyOf(DASHES.values()))if(dash.source==entity||dash.target.entity()==entity)cancel(dash.source);
        KNOCKBACKS.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().caster.entity()==entity);
    }
    static void playerLeft(ServerPlayer viewer) {
        for(Dash dash:List.copyOf(DASHES.values()))if(dash.commander==viewer)cancel(dash.source);
    }
    static void relationshipsChanged() {
        for(Dash dash:List.copyOf(DASHES.values()))if(!dash.valid())cancel(dash.source);
        KNOCKBACKS.entrySet().removeIf(entry->!entry.getValue().valid());
    }
    static void stop() {
        for(WindKnightUnit source:List.copyOf(DASHES.keySet()))cancel(source);
        KNOCKBACKS.clear();STRIKES.clear();
    }
}
