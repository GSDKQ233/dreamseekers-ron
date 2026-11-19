// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.stone.HeavyStoneProjectile;
import com.dreamseekers.stone.StoneThrowerBalance;
import com.dreamseekers.unit.StoneSpiritThrowerUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One paid windup, one physical skill shot, then at most one bounded secondary burst.
 * None of these short-lived authorization objects is saved. Ordinary stones are unchanged.
 */
public final class StoneHeavyShot {
    public static final int WINDUP_TICKS=20, RANGE=10, SECONDARY_LIMIT=3;
    public static final double SPLASH_RADIUS=1.5;
    public static final float PRIMARY_MULTIPLIER=1.5f, SECONDARY_MULTIPLIER=.3f;
    private static final Map<StoneSpiritThrowerUnit,Charge> CHARGES=new IdentityHashMap<>();
    private static final Map<HeavyStoneProjectile,Launch> FLIGHTS=new IdentityHashMap<>();
    private static final ArrayDeque<Impact> HITS=new ArrayDeque<>();
    private StoneHeavyShot() { }
    public static int range(StoneSpiritThrowerUnit source) {
        return com.dreamseekers.unitprogression.AdvancedUnitResearch.unlocked(source.getOwnerName(),
                com.dreamseekers.unitprogression.AdvancedUnitResearch.STONE_RANGE_ID,source.level().isClientSide())?12:RANGE;
    }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp.valid()&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean disabled(StoneSpiritThrowerUnit source) {
        return source.isPassenger()||source.hasEffect(MobEffectRegistrar.STUN.get())
                ||source.hasEffect(MobEffectRegistrar.FREEZE.get())||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get())
                ||source.hasEffect(MobEffectRegistrar.DISARM.get());
    }
    private static boolean enemy(StoneSpiritThrowerUnit source,LivingEntity target) {
        return target!=source&&target instanceof Unit&&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)
                &&!target.isSpectator()&&target.isAddedToWorld()&&target.isAlive()&&!target.isRemoved()
                &&target.level()==source.level()&&source.level() instanceof ServerLevel level
                &&level.getEntity(target.getUUID())==target&&level.getEntity(target.getId())==target
                &&level.getWorldBorder().isWithinBounds(target.blockPosition())&&CoordinatedMark.enemy(source,target);
    }
    private static boolean viewer(ServerPlayer viewer,SkillIdentity.Stamp caster) {
        return viewer!=null&&!viewer.isRemoved()&&viewer.isAlive()&&viewer.serverLevel()==caster.level()
                &&viewer.getGameProfile().getName().equals(caster.owner());
    }
    private static final class Charge {
        final StoneSpiritThrowerUnit source;
        final SkillIdentity.Stamp caster,aim;
        final ServerPlayer viewer;
        final long commandRevision,releaseAt;
        final int range;
        boolean releasing;
        Charge(StoneSpiritThrowerUnit source,LivingEntity target,ServerPlayer viewer) {
            this.source=source;range=range(source);caster=SkillIdentity.capture(source);aim=SkillIdentity.capture(target);this.viewer=viewer;
            commandRevision=source.rts().commandRevision;releaseAt=source.level().getGameTime()+WINDUP_TICKS;
        }
        boolean valid() {
            return CHARGES.get(source)==this&&loaded(caster)&&loaded(aim)&&viewer(viewer,caster)
                    &&source.rts().commandRevision==commandRevision&&!disabled(source)
                    &&!UnitServerEvents.hasPendingImmediateAction(source.getId())&&enemy(source,aim.entity())
                    &&source.distanceToSqr(aim.entity())<=(double)range*range&&source.hasLineOfSight(aim.entity())
                    &&CoordinatedMark.visible(viewer,aim.entity());
        }
    }
    private static final class Launch {
        final HeavyStoneProjectile projectile;
        final StoneSpiritThrowerUnit source;
        final SkillIdentity.Stamp caster;
        final UUID projectileId;
        final float baseDamage;
        boolean published,spent,revoked;
        Launch(HeavyStoneProjectile projectile,Charge charge,float damage) {
            this.projectile=projectile;source=charge.source;caster=charge.caster;
            projectileId=projectile.getUUID();baseDamage=damage;
        }
        boolean valid() {
            return !revoked&&published&&FLIGHTS.get(projectile)==this&&loaded(caster)
                    &&projectile.level()==caster.level()&&!projectile.isRemoved()&&projectile.isAddedToWorld()
                    &&projectile.getUUID().equals(projectileId)&&projectile.getOwner()==source
                    &&caster.level().getEntity(projectileId)==projectile
                    &&caster.level().getEntity(projectile.getId())==projectile;
        }
    }
    public static boolean active(StoneSpiritThrowerUnit source) {
        if(!server(source))return false;
        Charge charge=CHARGES.get(source);return charge!=null&&charge.valid();
    }
    public static int remainingTicks(StoneSpiritThrowerUnit source) {
        return active(source)?(int)Math.max(0,CHARGES.get(source).releaseAt-source.level().getGameTime()):0;
    }
    static SkillResult start(ServerPlayer sender,StoneSpiritThrowerUnit source,LivingEntity target) {
        if(!server(source)||!source.isAlive()||!source.isAddedToWorld()||source.isRemoved()
                ||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(active(source))return SkillResult.ALREADY_ACTIVE;
        if(disabled(source)||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.DISABLED;
        if(!enemy(source,target))return SkillResult.INVALID_TARGET;
        if(source.distanceToSqr(target)>(double)range(source)*range(source))return SkillResult.RANGE;
        if(!CoordinatedMark.visible(sender,target))return SkillResult.HIDDEN;
        if(!source.hasLineOfSight(target))return SkillResult.INVALID_TARGET;
        SkillIdentity.Stamp caster=SkillIdentity.capture(source),aim=SkillIdentity.capture(target);
        if(!loaded(caster)||!loaded(aim)||!viewer(sender,caster))return SkillResult.INVALID_SOURCE;
        long command=source.rts().commandRevision,revision=source.rts().skills().revision();
        CHARGES.remove(source);
        Unit.fullResetBehaviours(source); // clears old ranged windup but preserves its cooldown and Shift queue
        if(!loaded(caster))return SkillResult.INVALID_SOURCE;
        if(source.rts().commandRevision!=command+1||source.rts().skills().revision()!=revision
                ||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.REPLAY;
        if(!loaded(aim)||disabled(source)||!enemy(source,target)||!viewer(sender,caster)
                ||source.distanceToSqr(target)>(double)range(source)*range(source)||!source.hasLineOfSight(target)
                ||!CoordinatedMark.visible(sender,target))return SkillResult.INVALID_TARGET;
        Charge charge=new Charge(source,target,sender);CHARGES.put(source,charge);stopMovement(source);
        return SkillResult.SUCCESS; // SkillService commits CD exactly once, before any future release callback
    }
    private static void stopMovement(StoneSpiritThrowerUnit source) {
        source.getNavigation().stop();
        source.getMoveControl().setWantedPosition(source.getX(),source.getY(),source.getZ(),0);
        source.setSpeed(0);source.setZza(0);source.setXxa(0); // preserve gravity, water and external impulses
    }
    public static Goal goal(StoneSpiritThrowerUnit source) {
        return new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
            @Override public boolean canUse() { return active(source); }
            @Override public boolean canContinueToUse() { return active(source); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void tick() {
                Charge charge=CHARGES.get(source);
                if(charge!=null&&charge.valid()) {
                    stopMovement(source);source.getLookControl().setLookAt(charge.aim.entity(),30,30);
                }
            }
            @Override public void stop() { } // a newly accepted command may already own navigation
        };
    }
    public static void beforeTick(StoneSpiritThrowerUnit source) {
        if(!server(source))return;
        Charge charge=CHARGES.get(source);
        if(charge!=null&&!charge.valid())CHARGES.remove(source,charge);
        else if(charge!=null)stopMovement(source);
    }
    public static void afterTick(StoneSpiritThrowerUnit source) {
        if(!server(source))return;
        Charge charge=CHARGES.get(source);if(charge==null||charge.releasing)return;
        if(!charge.valid()) { CHARGES.remove(source,charge);return; }
        if(source.level().getGameTime()<charge.releaseAt)return;
        // Keep the exact charge installed through constructor callbacks. Its private reservation
        // prevents nested ticks from firing; new explicit commands still revoke the original lease.
        charge.releasing=true;
        try {
            ServerLevel level=(ServerLevel)source.level();
            HeavyStoneProjectile projectile=new HeavyStoneProjectile(DreamseekersMod.STONE_HEAVY_PROJECTILE.get(),level);
            if(!charge.valid()) { projectile.discard();return; }
            float snapshot=source.getUnitAttackDamage(); // one read after actual constructor callbacks
            if(!Float.isFinite(snapshot)||snapshot<=0||!charge.valid()) { projectile.discard();return; }
            Launch launch=new Launch(projectile,charge,snapshot);FLIGHTS.put(projectile,launch);
            boolean accepted=false;
            try {
                projectile.setOwner(source);projectile.setPos(source.getX(),source.getEyeY(),source.getZ());
                Vec3 delta=charge.aim.entity().getBoundingBox().getCenter().subtract(projectile.position());
                projectile.shoot(delta.x,delta.y,delta.z,StoneThrowerBalance.PROJECTILE_SPEED,0);
                if(launch.revoked||!charge.valid()||projectile.isRemoved()||projectile.getOwner()!=source)return;
                // Launch commit: all original aim/command/view checks passed, immediately before
                // the actual publication call. Ordinary commands during/after Join do not recall it.
                CHARGES.remove(source,charge);
                accepted=level.addFreshEntity(projectile);
                launch.published=accepted;
                if(!launch.valid())return; // death/leave/owner ABA during Join still revoke the shot
                com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket.revealRangedUnit(charge.aim.owner(),source.getId());
                source.swing(InteractionHand.MAIN_HAND);
            } finally {
                if(!accepted||!launch.valid()) { launch.revoked=true;FLIGHTS.remove(projectile,launch);projectile.discard(); }
            }
        } finally { CHARGES.remove(source,charge); }
    }
    /** Explicit replacement only; never a Shift intent. It cannot retract an already released shot. */
    public static void commandChanged(StoneSpiritThrowerUnit source) {
        if(server(source)&&CHARGES.remove(source)!=null)source.rts().commandRevision++;
    }
    public static boolean allowOrdinaryShot(StoneSpiritThrowerUnit source) { return !active(source); }
    public static boolean flightValid(HeavyStoneProjectile projectile) {
        if(!server(projectile))return false;
        Launch launch=FLIGHTS.get(projectile);return launch!=null&&!launch.spent&&launch.valid();
    }
    public static boolean canHit(HeavyStoneProjectile projectile,Entity victim) {
        Launch launch=FLIGHTS.get(projectile);
        return flightValid(projectile)&&victim instanceof LivingEntity target&&enemy(launch.source,target);
    }
    /** Dedicated subtype observes owner mutation before Join too; A->B->A never restores authority. */
    public static void ownerChanged(HeavyStoneProjectile projectile) {
        if(!server(projectile))return;
        Launch launch=FLIGHTS.get(projectile);
        if(launch!=null&&projectile.getOwner()!=launch.source)launch.revoked=true;
    }
    public static void projectileLeft(HeavyStoneProjectile projectile) {
        Launch launch=FLIGHTS.remove(projectile);if(launch!=null)launch.revoked=true;
    }
    private static boolean impactLos(Launch launch,Vec3 origin,LivingEntity target) {
        return launch.caster.level().clip(new ClipContext(origin,target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,launch.source)).getType()==HitResult.Type.MISS;
    }
    static final class Impact {
        final Launch launch;
        final SkillIdentity.Stamp target;
        final DamageSource damage;
        final Vec3 origin;
        final boolean primary;
        boolean entered,claimed,revoked,reported,receipt;
        Impact(Launch launch,LivingEntity target,Vec3 origin,boolean primary) {
            this.launch=launch;this.target=SkillIdentity.capture(target);this.origin=origin;this.primary=primary;
            damage=target.damageSources().thrown(launch.projectile,launch.source);
        }
        boolean legal() {
            return !revoked&&!reported&&entered&&launch.valid()&&loaded(target)
                    &&enemy(launch.source,target.entity())&&impactLos(launch,origin,target.entity())
                    &&(primary||target.entity().getBoundingBox().getCenter().distanceToSqr(origin)<=SPLASH_RADIUS*SPLASH_RADIUS);
        }
        void applied(float healthLoss,float shieldLoss) {
            if(reported)return;reported=true;
            // Own lethal writes count, but source death/removal/epoch changes and target replacement never do.
            receipt=primary&&!revoked&&(healthLoss>0||shieldLoss>0)&&launch.valid()
                    &&target.identityValid()&&target.entity().isAddedToWorld()
                    &&target.level().getEntity(target.entity().getId())==target.entity()
                    &&launch.source.canHarmOwner(target.owner());
        }
    }
    private static Impact matching(LivingEntity target,DamageSource source) {
        Impact impact=HITS.peek();
        return impact!=null&&impact.target.entity()==target&&impact.damage==source?impact:null;
    }
    public static boolean rejectRepeatedHurt(LivingEntity target,DamageSource damage) {
        if(!server(target))return false;
        Impact impact=matching(target,damage);
        if(impact==null)return damage.getDirectEntity() instanceof HeavyStoneProjectile;
        if(impact.entered) { impact.revoked=true;return true; }
        impact.entered=true;return !impact.legal();
    }
    private static final FinalUnitDamageHooks.OrdinaryAttack DENIED=new FinalUnitDamageHooks.OrdinaryAttack() {
        @Override public float beforeArmor(float amount) { return 0; }
        @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return amount->0; }
    };
    static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity target,DamageSource damage) {
        Impact impact=matching(target,damage);
        if(impact==null)return damage.getDirectEntity() instanceof HeavyStoneProjectile?DENIED:null;
        if(!impact.entered||impact.claimed)return DENIED;
        impact.claimed=true;
        return new FinalUnitDamageHooks.OrdinaryAttack() {
            @Override public float beforeArmor(float ignoredDynamicBase) {
                return impact.legal()?impact.launch.baseDamage*(impact.primary?PRIMARY_MULTIPLIER:SECONDARY_MULTIPLIER):0;
            }
            @Override public float validateAfterHurt(float amount) { return impact.legal()?amount:0; }
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal() {
                return SkillEffects.prepareHeavyFinal(target,damage,impact);
            }
        };
    }
    private static void hurt(Impact impact,PrimaryUnitHitHooks.Impact primary) {
        HITS.push(impact);
        try {
            float amount=impact.launch.baseDamage*(impact.primary?PRIMARY_MULTIPLIER:SECONDARY_MULTIPLIER);
            if(primary!=null)primary.hurt(impact.damage,amount);
            else com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(impact.launch.caster.entity(),impact.target.entity(),impact.target.entity()::hurt).hurt(impact.damage,amount); // secondary: never Primary capture, never ordinary provenance
        } finally { HITS.removeFirstOccurrence(impact); }
    }
    /** Called only by the actual physical collision. The entire resolution retains a loaded, spent shot. */
    public static void collide(HeavyStoneProjectile projectile,EntityHitResult hit) {
        if(!server(projectile))return;
        Launch launch=FLIGHTS.get(projectile);
        if(launch==null||launch.spent||!launch.valid())return;
        launch.spent=true; // reserve before capture or ANY hurt callback; no physical/reentrant second impact
        try {
            if(!(hit.getEntity() instanceof LivingEntity target)||!enemy(launch.source,target))return;
            Vec3 origin=hit.getLocation();
            if(!Double.isFinite(origin.x)||!Double.isFinite(origin.y)||!Double.isFinite(origin.z))return;
            Impact primary=new Impact(launch,target,origin,true);
            PrimaryUnitHitHooks.Impact actual=PrimaryUnitHitHooks.capture(projectile,target);
            hurt(primary,actual); // this returns only after existing Snail/ordinary primary wrappers have popped
            if(!primary.receipt||!launch.valid()||!primary.target.identityValid()
                    ||!launch.source.canHarmOwner(primary.target.owner()))return;
            List<SkillIdentity.Stamp> candidates=launch.caster.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(origin,origin).inflate(SPLASH_RADIUS),candidate->candidate!=target&&enemy(launch.source,candidate)
                            &&candidate.getBoundingBox().getCenter().distanceToSqr(origin)<=SPLASH_RADIUS*SPLASH_RADIUS
                            &&impactLos(launch,origin,candidate)).stream()
                    .sorted(Comparator.comparingDouble((LivingEntity candidate)->candidate.getBoundingBox().getCenter().distanceToSqr(origin))
                            .thenComparing(candidate->candidate.getUUID().toString()))
                    .limit(SECONDARY_LIMIT).map(SkillIdentity::capture).toList();
            for(SkillIdentity.Stamp candidate:candidates) {
                if(!launch.valid())break;
                if(!loaded(candidate)||!enemy(launch.source,candidate.entity())
                        ||candidate.entity().getBoundingBox().getCenter().distanceToSqr(origin)>SPLASH_RADIUS*SPLASH_RADIUS
                        ||!impactLos(launch,origin,candidate.entity()))continue;
                hurt(new Impact(launch,candidate.entity(),origin,false),null);
            }
        } finally { launch.revoked=true;FLIGHTS.remove(projectile,launch);projectile.discard(); }
    }
    static void tick(ServerLevel level) {
        CHARGES.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().valid());
        for(Launch launch:List.copyOf(FLIGHTS.values()))if(launch.projectile.level()==level&&!launch.valid()) {
            launch.revoked=true;FLIGHTS.remove(launch.projectile,launch);launch.projectile.discard();
        }
    }
    static void clear(LivingEntity entity) {
        CHARGES.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().aim.entity()==entity);
        for(Launch launch:List.copyOf(FLIGHTS.values()))if(launch.source==entity)launch.revoked=true;
    }
    static void stop() {
        CHARGES.clear();for(Launch launch:FLIGHTS.values())launch.revoked=true;
        FLIGHTS.clear();HITS.clear();
    }
}
