// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.BasaltSnailUnit;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Stationary, temporary projectile protection. No order, path or effect is saved/restored. */
public final class SnailShellFormation {
    public static final double RANGE=3, REDUCTION=.20;
    private static final Map<BasaltSnailUnit,Formation> ACTIVE=new IdentityHashMap<>();
    private static final ArrayDeque<Plan> HITS=new ArrayDeque<>();
    private SnailShellFormation() { }
    private static final class Formation {
        final BasaltSnailUnit source;
        final SkillIdentity.Stamp caster;
        final long commandRevision,expiresAt;
        final boolean onExplicitHold;
        Formation(BasaltSnailUnit source,boolean onExplicitHold) {
            this.onExplicitHold=onExplicitHold;
            this.source=source;caster=SkillIdentity.capture(source);commandRevision=source.rts().commandRevision;
            expiresAt=source.level().getGameTime()+UnitSkill.SNAIL_SHELL_FORMATION.durationTicks();
        }
        boolean valid() {
            return BeetleAdvance.loaded(caster)&&(!onExplicitHold||source.isHoldingExplicitOrder())
                    &&source.rts().commandRevision==commandRevision
                    &&source.level().getGameTime()<expiresAt&&!disabled(source)
                    &&!UnitServerEvents.hasPendingImmediateAction(source.getId());
        }
    }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean disabled(BasaltSnailUnit source) {
        return source.hasEffect(MobEffectRegistrar.STUN.get())||source.hasEffect(MobEffectRegistrar.FREEZE.get())
                ||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    public static boolean active(LivingEntity entity) {
        if(!(entity instanceof BasaltSnailUnit source)||!server(source))return false;
        Formation formation=ACTIVE.get(source);return formation!=null&&formation.valid();
    }
    /** The automatic version is a defensive overlay on a real HOLD, never an order of its own.
     * Pathing, anchor returns, melee, idle state and future Shift dispatch remain entirely native. */
    public static boolean occupiesOrders(BasaltSnailUnit source) {
        if(!server(source))return false;
        Formation formation=ACTIVE.get(source);
        return formation!=null&&!formation.onExplicitHold&&formation.valid();
    }
    public static int remainingTicks(BasaltSnailUnit source) {
        return active(source)?(int)Math.max(0,ACTIVE.get(source).expiresAt-source.level().getGameTime()):0;
    }
    static SkillResult start(BasaltSnailUnit source) { return start(source,false); }
    static SkillResult startHolding(BasaltSnailUnit source) { return start(source,true); }
    private static SkillResult start(BasaltSnailUnit source,boolean onExplicitHold) {
        if(!server(source)||!source.isAlive()||source.isRemoved()||!source.isAddedToWorld()
                ||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(active(source))return SkillResult.ALREADY_ACTIVE;
        ACTIVE.remove(source); // retire an expired/invalid lease before the one reset below
        if(disabled(source)||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.DISABLED;
        SkillIdentity.Stamp caster=SkillIdentity.capture(source);
        if(!BeetleAdvance.loaded(caster))return SkillResult.INVALID_SOURCE;
        long command=source.rts().commandRevision,revision=source.rts().skills().revision();
        SnailShellNavigation navigation=!onExplicitHold&&source.getNavigation() instanceof SnailShellNavigation n?n:null;
        Vec3 oldCoast=navigation==null?null:navigation.captureOrderCoast();
        if(onExplicitHold) {
            if(!source.isHoldingExplicitOrder())return SkillResult.DISABLED;
        } else {
            // A manual deployment discards the executed order, not Shift-queued future commands.
            Unit.fullResetBehaviours(source);
        }
        if(!BeetleAdvance.loaded(caster))return SkillResult.INVALID_SOURCE;
        if(source.rts().commandRevision!=(onExplicitHold?command:command+1)||source.rts().skills().revision()!=revision
                ||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.REPLAY;
        if(disabled(source))return SkillResult.DISABLED;
        Formation formation=new Formation(source,onExplicitHold);
        if(!formation.valid())return SkillResult.DISABLED;
        ACTIVE.put(source,formation);
        if(navigation!=null) {
            navigation.stopForFormation();
            navigation.brakeOrderCoast(oldCoast);
        }
        return SkillResult.SUCCESS;
    }
    /** New commands own all subsequent movement. Removing this record never stops their new path. */
    public static void commandChanged(BasaltSnailUnit source) {
        if(!server(source))return;
        Formation removed=ACTIVE.remove(source);
        if(removed!=null&&!removed.onExplicitHold)source.rts().commandRevision++;
    }
    private static boolean inMeleeReach(BasaltSnailUnit source,LivingEntity target) {
        double reach=OrdinaryMeleeReach.rawSquared(source,source,target);
        return Double.isFinite(reach)&&reach>=0&&source.distanceToSqr(target)<=reach;
    }
    private static boolean maySelectInPlace(BasaltSnailUnit source,LivingEntity target) {
        if(target==null||target==source||!target.isAlive()||target.isRemoved()||target.isSpectator()
                ||target.isInvisible()||target.level()!=source.level()||!target.isAddedToWorld()
                ||((ServerLevel)source.level()).getEntity(target.getUUID())!=target
                ||((ServerLevel)source.level()).getEntity(target.getId())!=target
                ||!source.level().getWorldBorder().isWithinBounds(target.blockPosition())
                ||!inMeleeReach(source,target)||!source.hasLineOfSight(target)
                ||(target instanceof Unit unit&&unit.isGarrisoned()))return false;
        float range=source.getAggroRange();
        if(!Float.isFinite(range)||range<=0||source.distanceToSqr(target)>(double)range*range)return false;
        boolean neutralAggro=source.level().getGameRules().getRule(
                com.solegendary.reignofnether.registrars.GameRuleRegistrar.NEUTRAL_AGGRO).get();
        return com.dreamseekers.unit.mixin.SnailAutomaticTargetAccess.dreamseekers$ordinaryAutomaticTarget(
                source,target,neutralAggro);
    }
    private static boolean mayActInPlace(BasaltSnailUnit source) {
        return occupiesOrders(source)&&!source.isNoAi()&&!source.isCasting()&&source.getAggressiveWhenIdle()
                &&!source.hasEffect(MobEffectRegistrar.DISARM.get());
    }
    /** Natural paid-snail tick only. No direct attack/goal tick, target snapshot or order restoration.
     * isIdle remains false for queued commands; this narrow stationary acquisition replaces only
     * the ordinary idle acquisition that the queue guard intentionally suppresses.
     */
    public static void tickCombat(BasaltSnailUnit source) {
        if(!server(source)||!occupiesOrders(source))return;
        if(source.getTargetGoal().forced)return; // explicit orders own their target, never retarget them
        LivingEntity current=source.getTargetGoal().getTarget();
        if(current!=null&&(!mayActInPlace(source)||!maySelectInPlace(source,current))) {
            source.setUnitAttackTarget(null);current=null;
        }
        if(current!=null||!mayActInPlace(source)||source.tickCount%4!=0)return;
        float range=source.getAggroRange();
        if(!Float.isFinite(range)||range<=0)return;
        LivingEntity nearest=source.level().getEntitiesOfClass(LivingEntity.class,source.getBoundingBox().inflate(range),
                        candidate->maySelectInPlace(source,candidate)).stream()
                .min(java.util.Comparator.comparingDouble((LivingEntity candidate)->source.distanceToSqr(candidate))
                        .thenComparing(candidate->candidate.getUUID().toString())).orElse(null);
        if(nearest!=null&&mayActInPlace(source)&&!source.getTargetGoal().forced
                &&source.getTargetGoal().getTarget()==null&&maySelectInPlace(source,nearest))
            source.setUnitAttackTarget(nearest);
    }
    /** The original goal still spends its own cooldown; only its active-formation hit is revalidated. */
    public static boolean permitsMelee(BasaltSnailUnit source,Entity target) {
        if(!occupiesOrders(source))return true;
        return mayActInPlace(source)&&target instanceof LivingEntity living&&maySelectInPlace(source,living);
    }
    private static boolean protects(Formation formation,LivingEntity target) {
        return formation.valid()&&ACTIVE.get(formation.source)==formation&&target instanceof Unit
                &&target.isAddedToWorld()&&!target.isSpectator()&&target.isAlive()&&!target.isRemoved()
                &&target.level()==formation.caster.level()
                &&formation.caster.level().getEntity(target.getUUID())==target
                &&formation.caster.level().getEntity(target.getId())==target
                &&formation.source.distanceToSqr(target)<=RANGE*RANGE
                &&SkillEffects.friendly(formation.source,target);
    }
    private static boolean excluded(DamageSource damage) {
        return !damage.is(DamageTypeTags.IS_PROJECTILE)||damage.is(DamageTypeTags.IS_EXPLOSION)
                ||damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||damage.is(DamageTypeTags.BYPASSES_EFFECTS)
                ||damage.is(DamageTypeTags.BYPASSES_RESISTANCE)||damage.is(DamageTypes.GENERIC_KILL)
                ||damage.is(DamageTypes.FELL_OUT_OF_WORLD);
    }
    static final class Plan {
        final SkillIdentity.Stamp target,actor;
        final List<Formation> formations;
        final BeetleGuard.ProjectileProof projectile;
        DamageSource damage;
        boolean entered,revoked,nativeResistance;
        Plan(LivingEntity target,LivingEntity actor,List<Formation> formations,BeetleGuard.ProjectileProof projectile) {
            this.target=SkillIdentity.capture(target);this.actor=SkillIdentity.capture(actor);
            this.formations=formations;this.projectile=projectile;
        }
        boolean valid(DamageSource source) {
            return entered&&!revoked&&damage==source&&!excluded(source)&&source.getEntity()==actor.entity()
                    &&BeetleAdvance.loaded(target)&&BeetleAdvance.loaded(actor)&&projectile.valid()
                    &&CoordinatedMark.enemy(actor.entity(),target.entity())
                    &&formations.stream().anyMatch(formation->protects(formation,target.entity()));
        }
    }
    /** Compose with the existing provider; never steal Bone/Spore/Beetle's first matching integration. */
    static PrimaryUnitHitHooks.Impact wrapProjectile(Projectile projectile,Entity victim,PrimaryUnitHitHooks.Impact original) {
        if(!(victim instanceof LivingEntity target)||!(target instanceof Unit)||!server(projectile)
                ||projectile.isRemoved()||!projectile.isAddedToWorld()||projectile.level()!=target.level()
                ||!(projectile.getOwner() instanceof LivingEntity actor)||!(actor instanceof Unit)
                ||!CoordinatedMark.enemy(actor,target)
                ||((ServerLevel)projectile.level()).getEntity(projectile.getUUID())!=projectile
                ||((ServerLevel)projectile.level()).getEntity(projectile.getId())!=projectile)return original;
        List<Formation> formations=ACTIVE.values().stream().filter(formation->protects(formation,target)).toList();
        if(formations.isEmpty())return original;
        Plan plan=new Plan(target,actor,formations,new BeetleGuard.ProjectileProof(projectile,actor));
        PrimaryUnitHitHooks.Impact delegate=original==null?victim::hurt:original;
        return new PrimaryUnitHitHooks.Impact() {
            private boolean used;
            @Override public boolean hurt(DamageSource source,float amount) {
                if(used)return false;
                used=true;
                if(excluded(source)||source.getEntity()!=actor)return delegate.hurt(source,amount);
                plan.damage=source;HITS.push(plan);
                try { return delegate.hurt(source,amount); }
                finally { HITS.removeFirstOccurrence(plan); }
            }
        };
    }
    /** A recursive same-source invocation revokes only optional protection, never either base hurt. */
    public static void observeHurt(LivingEntity target,DamageSource source) {
        if(!server(target))return;
        Plan plan=HITS.peek();
        if(plan==null||plan.target.entity()!=target||plan.damage!=source)return;
        if(plan.entered)plan.revoked=true;
        plan.entered=true;
    }
    static Plan matching(LivingEntity target,DamageSource source) {
        if(!server(target))return null;
        Plan plan=HITS.peek();
        return plan!=null&&plan.entered&&plan.target.entity()==target&&plan.damage==source?plan:null;
    }
    static double reduction(Plan plan,DamageSource source) { return plan!=null&&plan.valid(source)?REDUCTION:0; }
    static void tick(ServerLevel level) {
        if(level.getServer().isSameThread())ACTIVE.entrySet().removeIf(entry->
                entry.getKey().level()==level&&!entry.getValue().valid());
    }
    static void clear(LivingEntity entity) {
        if(entity instanceof BasaltSnailUnit source&&server(source))ACTIVE.remove(source);
    }
    static void stop() { ACTIVE.clear();HITS.clear(); }
}
