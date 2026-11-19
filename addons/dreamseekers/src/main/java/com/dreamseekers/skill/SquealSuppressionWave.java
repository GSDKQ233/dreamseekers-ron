// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.shadow.ShadowSquealGhostBalance;
import com.dreamseekers.shadow.SuppressionWaveProjectile;
import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.unit.ShadowSquealGhostUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** One actual paid physical skill shot, bounded independent hits, and transient managed slows.
 * No ordinary provenance, vanilla potion mutation, serialized lease or competing damage provider.
 */
public final class SquealSuppressionWave {
    public static final int RANGE=10, SECONDARY_LIMIT=3, DEBUFF_TICKS=80;
    public static final double SPLASH_RADIUS=2;
    public static final float DAMAGE_MULTIPLIER=.8f, ATTACK_INTERVAL_FACTOR=1.25f;
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","squeal_suppression_wave");
    private static final Map<ShadowSquealGhostUnit,Construction> BUSY=new IdentityHashMap<>();
    private static final Map<SuppressionWaveProjectile,Launch> FLIGHTS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Debuff> DEBUFFS=new IdentityHashMap<>();
    private static final ArrayDeque<Impact> HITS=new ArrayDeque<>();
    private SquealSuppressionWave() { }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp!=null&&stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean disabled(ShadowSquealGhostUnit source) {
        return source.isPassenger()||source.hasEffect(MobEffectRegistrar.STUN.get())
                ||source.hasEffect(MobEffectRegistrar.FREEZE.get())||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get())
                ||source.hasEffect(MobEffectRegistrar.DISARM.get());
    }
    private static boolean enemy(ShadowSquealGhostUnit source,LivingEntity target) {
        return target!=source&&target instanceof Unit&&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)
                &&target.isAlive()&&!target.isRemoved()&&!target.isSpectator()&&target.isAddedToWorld()
                &&source.level()==target.level()&&source.level() instanceof ServerLevel level
                &&level.getEntity(target.getUUID())==target&&level.getEntity(target.getId())==target
                &&level.getWorldBorder().isWithinBounds(target.blockPosition())&&CoordinatedMark.enemy(source,target);
    }
    private static final class Construction {
        final ShadowSquealGhostUnit source;
        final ServerPlayer viewer;
        final String owner;
        final long commandRevision;
        SkillIdentity.Stamp caster,aim;
        boolean revoked;
        Construction(ServerPlayer viewer,ShadowSquealGhostUnit source) {
            this.viewer=viewer;this.source=source;owner=source.getOwnerName();commandRevision=source.rts().commandRevision;
        }
        boolean valid() {
            return !revoked&&BUSY.get(source)==this&&loaded(caster)&&loaded(aim)
                    &&source.rts().commandRevision==commandRevision&&!UnitServerEvents.hasPendingImmediateAction(source.getId())
                    &&owner.equals(caster.owner())&&viewer.isAlive()&&!viewer.isRemoved()&&viewer.serverLevel()==caster.level()
                    &&viewer.getGameProfile().getName().equals(owner)&&!disabled(source)&&enemy(source,aim.entity())
                    &&source.distanceToSqr(aim.entity())<=RANGE*RANGE&&source.hasLineOfSight(aim.entity())
                    &&CoordinatedMark.visible(viewer,aim.entity());
        }
    }
    private static final class Launch {
        final SuppressionWaveProjectile projectile;
        final ShadowSquealGhostUnit source;
        final SkillIdentity.Stamp caster;
        final UUID projectileId;
        final float baseDamage;
        boolean published,committed,spent,revoked;
        Launch(SuppressionWaveProjectile projectile,Construction construction,float damage) {
            this.projectile=projectile;source=construction.source;caster=construction.caster;
            projectileId=projectile.getUUID();baseDamage=damage;
        }
        boolean publishedValid() {
            return !revoked&&published&&FLIGHTS.get(projectile)==this&&loaded(caster)
                    &&projectile.level()==caster.level()&&!projectile.isRemoved()&&projectile.isAddedToWorld()
                    &&projectile.getUUID().equals(projectileId)&&projectile.getOwner()==source
                    &&caster.level().getEntity(projectileId)==projectile&&caster.level().getEntity(projectile.getId())==projectile;
        }
        boolean valid() { return committed&&publishedValid(); }
    }
    /** Busy precedes constructor/Join callbacks and survives invalidation until this exact finally.
     * Only the authenticated service supplies commit, after actual accepted publication and revalidation.
     */
    static SkillResult cast(ServerPlayer viewer,ShadowSquealGhostUnit source,LivingEntity target,BooleanSupplier commit) {
        if(viewer==null||commit==null||!server(source)||!source.isAlive()||source.isRemoved()||!source.isAddedToWorld()
                ||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(BUSY.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        if(disabled(source))return SkillResult.DISABLED;
        if(!enemy(source,target))return SkillResult.INVALID_TARGET;
        if(source.distanceToSqr(target)>RANGE*RANGE)return SkillResult.RANGE;
        if(!CoordinatedMark.visible(viewer,target))return SkillResult.HIDDEN;
        if(!source.hasLineOfSight(target))return SkillResult.INVALID_TARGET;
        Construction construction=new Construction(viewer,source);BUSY.put(source,construction);
        try {
            construction.caster=SkillIdentity.capture(source);construction.aim=SkillIdentity.capture(target);
            if(!construction.valid())return SkillResult.INVALID_TARGET;
            SuppressionWaveProjectile projectile=new SuppressionWaveProjectile(DreamseekersMod.SUPPRESSION_WAVE_PROJECTILE.get(),source.level());
            if(!construction.valid()) { projectile.discard();return SkillResult.INVALID_SOURCE; }
            float snapshot=source.getUnitAttackDamage(); // one legal launch snapshot AFTER real constructor callbacks
            if(!Float.isFinite(snapshot)||snapshot<=0||!construction.valid()) { projectile.discard();return SkillResult.INVALID_SOURCE; }
            Launch launch=new Launch(projectile,construction,snapshot);FLIGHTS.put(projectile,launch);
            try {
                projectile.setOwner(source);projectile.setPos(source.getX(),source.getEyeY(),source.getZ());
                Vec3 delta=target.getBoundingBox().getCenter().subtract(projectile.position());
                projectile.shoot(delta.x,delta.y,delta.z,ShadowSquealGhostBalance.PROJECTILE_SPEED,0);
                if(launch.revoked||projectile.isRemoved()||projectile.getOwner()!=source||!construction.valid())return SkillResult.SPAWN_FAILED;
                launch.published=construction.caster.level().addFreshEntity(projectile);
                if(!launch.publishedValid()||!construction.valid())return SkillResult.SPAWN_FAILED;
                if(!commit.getAsBoolean())return SkillResult.REPLAY;
                launch.committed=true; // no uncommitted Join callback can authorize an impact
                com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket.revealRangedUnit(construction.aim.owner(),source.getId());
                source.swing(InteractionHand.MAIN_HAND);
                return SkillResult.SUCCESS;
            } finally {
                if(!launch.committed) { launch.revoked=true;FLIGHTS.remove(projectile,launch);projectile.discard(); }
            }
        } finally { BUSY.remove(source,construction); }
    }
    public static boolean flightValid(SuppressionWaveProjectile projectile) {
        if(!server(projectile))return false;
        Launch launch=FLIGHTS.get(projectile);return launch!=null&&!launch.spent&&launch.valid();
    }
    public static boolean canHit(SuppressionWaveProjectile projectile,Entity victim) {
        Launch launch=FLIGHTS.get(projectile);
        return flightValid(projectile)&&victim instanceof LivingEntity target&&enemy(launch.source,target);
    }
    public static void ownerChanged(SuppressionWaveProjectile projectile) {
        if(!server(projectile))return;
        Launch launch=FLIGHTS.get(projectile);
        if(launch!=null&&projectile.getOwner()!=launch.source)launch.revoked=true;
    }
    public static void projectileLeft(SuppressionWaveProjectile projectile) {
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
        boolean targetLegal() {
            return loaded(target)&&enemy(launch.source,target.entity())&&impactLos(launch,origin,target.entity())
                    &&(primary||target.entity().getBoundingBox().getCenter().distanceToSqr(origin)<=SPLASH_RADIUS*SPLASH_RADIUS);
        }
        boolean legal() { return !revoked&&!reported&&entered&&launch.valid()&&targetLegal(); }
        void applied(float healthLoss,float shieldLoss) {
            if(reported)return;reported=true;
            // Own lethal primary may authorize the burst; it never receives a debuff after death.
            receipt=!revoked&&(healthLoss>0||shieldLoss>0)&&launch.valid()&&target.identityValid()
                    &&target.entity().getUUID().equals(target.uuid())
                    &&target.entity().isAddedToWorld()&&target.level().getEntity(target.entity().getId())==target.entity()
                    &&launch.source.canHarmOwner(target.owner());
        }
    }
    private static Impact matching(LivingEntity target,DamageSource damage) {
        Impact impact=HITS.peek();return impact!=null&&impact.target.entity()==target&&impact.damage==damage?impact:null;
    }
    public static boolean rejectRepeatedHurt(LivingEntity target,DamageSource damage) {
        if(!server(target))return false;
        Impact impact=matching(target,damage);
        if(impact==null)return damage.getDirectEntity() instanceof SuppressionWaveProjectile;
        if(impact.entered) { impact.revoked=true;return true; }
        impact.entered=true;return !impact.legal();
    }
    private static final FinalUnitDamageHooks.OrdinaryAttack DENIED=new FinalUnitDamageHooks.OrdinaryAttack() {
        @Override public float beforeArmor(float amount) { return 0; }
        @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return amount->0; }
    };
    static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity target,DamageSource damage) {
        Impact impact=matching(target,damage);
        if(impact==null)return damage.getDirectEntity() instanceof SuppressionWaveProjectile?DENIED:null;
        if(!impact.entered||impact.claimed)return DENIED;
        impact.claimed=true;
        return new FinalUnitDamageHooks.OrdinaryAttack() {
            @Override public float beforeArmor(float ignoredDynamicBase) { return impact.legal()?impact.launch.baseDamage*DAMAGE_MULTIPLIER:0; }
            @Override public float validateAfterHurt(float amount) { return impact.legal()?amount:0; }
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return SkillEffects.prepareSuppressionFinal(target,damage,impact); }
        };
    }
    private static void hurt(Impact impact,PrimaryUnitHitHooks.Impact primary) {
        HITS.push(impact);
        try {
            if(primary!=null)primary.hurt(impact.damage,impact.launch.baseDamage*DAMAGE_MULTIPLIER);
            else com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(impact.launch.caster.entity(),impact.target.entity(),impact.target.entity()::hurt).hurt(impact.damage,impact.launch.baseDamage*DAMAGE_MULTIPLIER);
        } finally { HITS.removeFirstOccurrence(impact); }
        installDebuff(impact); // receipt only inside health write; actual state installation AFTER normal hurt return
    }
    public static void collide(SuppressionWaveProjectile projectile,EntityHitResult hit) {
        if(!server(projectile))return;
        Launch launch=FLIGHTS.get(projectile);
        if(launch==null||launch.spent||!launch.valid())return;
        launch.spent=true;
        try {
            if(!(hit.getEntity() instanceof LivingEntity target)||!enemy(launch.source,target))return;
            Vec3 origin=hit.getLocation();
            if(!Double.isFinite(origin.x)||!Double.isFinite(origin.y)||!Double.isFinite(origin.z))return;
            Impact primary=new Impact(launch,target,origin,true);
            hurt(primary,PrimaryUnitHitHooks.capture(projectile,target));
            if(!primary.receipt||!launch.valid()||!primary.target.identityValid()
                    ||!primary.target.entity().getUUID().equals(primary.target.uuid())||!launch.source.canHarmOwner(primary.target.owner()))return;
            List<SkillIdentity.Stamp> candidates=launch.caster.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(origin,origin).inflate(SPLASH_RADIUS),candidate->candidate!=target&&enemy(launch.source,candidate)
                            &&candidate.getBoundingBox().getCenter().distanceToSqr(origin)<=SPLASH_RADIUS*SPLASH_RADIUS&&impactLos(launch,origin,candidate))
                    .stream().sorted(Comparator.comparingDouble((LivingEntity candidate)->candidate.getBoundingBox().getCenter().distanceToSqr(origin))
                            .thenComparing(candidate->candidate.getUUID().toString())).limit(SECONDARY_LIMIT).map(SkillIdentity::capture).toList();
            for(SkillIdentity.Stamp candidate:candidates) {
                if(!launch.valid())break;
                if(!loaded(candidate)||!enemy(launch.source,candidate.entity())
                        ||candidate.entity().getBoundingBox().getCenter().distanceToSqr(origin)>SPLASH_RADIUS*SPLASH_RADIUS
                        ||!impactLos(launch,origin,candidate.entity()))continue;
                hurt(new Impact(launch,candidate.entity(),origin,false),null);
            }
        } finally { launch.revoked=true;FLIGHTS.remove(projectile,launch);projectile.discard(); }
    }
    private static final class Debuff implements SporeEffects.ManagedNegative {
        final SkillIdentity.Stamp caster,target;
        final long expiresAt;
        Debuff(Impact impact,int ticks) { caster=impact.launch.caster;target=impact.target;expiresAt=target.level().getGameTime()+ticks; }
        @Override public ResourceLocation id() { return EFFECT_ID; }
        @Override public boolean current() {
            return DEBUFFS.get(target.entity())==this&&loaded(caster)&&loaded(target)&&target.level().getGameTime()<expiresAt
                    &&enemy((ShadowSquealGhostUnit)caster.entity(),target.entity());
        }
        @Override public boolean remove() { return current()&&DEBUFFS.remove(target.entity(),this); }
    }
    private static void installDebuff(Impact impact) {
        if(!impact.receipt||impact.revoked||!impact.launch.valid()||!impact.targetLegal())return;
        LivingEntity target=impact.target.entity();Debuff previous=DEBUFFS.get(target);
        if(previous!=null&&previous.current())return; // global same-name occupancy: no stack, steal or refresh
        if(previous!=null)DEBUFFS.remove(target,previous);
        int duration=SporeEffects.incomingManagedDuration(target,DEBUFF_TICKS);
        if(duration>0&&impact.launch.valid()&&impact.targetLegal())DEBUFFS.put(target,new Debuff(impact,duration));
    }
    public static boolean hasDebuff(LivingEntity target) { return remainingDebuffTicks(target)>0; }
    public static int remainingDebuffTicks(LivingEntity target) {
        if(!server(target))return 0;
        Debuff effect=DEBUFFS.get(target);return effect!=null&&effect.current()?(int)(effect.expiresAt-target.level().getGameTime()):0;
    }
    public static SporeEffects.ManagedNegative negative(LivingEntity target) {
        if(!server(target))return null;
        Debuff effect=DEBUFFS.get(target);return effect!=null&&effect.current()?effect:null;
    }
    /** Core default remains identity. Only a live exact managed lease participates in the native max. */
    public static float attackSlowdownFactor(LivingEntity target,float nativeFactor) {
        return hasDebuff(target)&&Float.isFinite(nativeFactor)&&nativeFactor>0?Math.max(nativeFactor,ATTACK_INTERVAL_FACTOR):nativeFactor;
    }
    /** Cached crossbow/slime intervals lack the normal multiplier call; opt in only while this lease lives. */
    static float cachedSlowdownFactor(LivingEntity target) {
        if(!hasDebuff(target))return 1;
        var nativeSlow=target.getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());
        float nativeFactor=nativeSlow==null?1:1+(nativeSlow.getAmplifier()+1)*.05f;
        return Math.max(ATTACK_INTERVAL_FACTOR,nativeFactor);
    }
    static void tick(ServerLevel level) {
        DEBUFFS.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().current());
        for(Launch launch:List.copyOf(FLIGHTS.values()))if(launch.projectile.level()==level&&!launch.valid()) {
            launch.revoked=true;FLIGHTS.remove(launch.projectile,launch);launch.projectile.discard();
        }
    }
    static void clear(LivingEntity entity) {
        for(Construction construction:BUSY.values())if(construction.source==entity||construction.aim!=null&&construction.aim.entity()==entity)construction.revoked=true;
        for(Launch launch:FLIGHTS.values())if(launch.source==entity)launch.revoked=true;
        DEBUFFS.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().caster.entity()==entity);
    }
    static void relationshipsChanged() { DEBUFFS.entrySet().removeIf(entry->!entry.getValue().current()); }
    static void stop() {
        for(Construction construction:BUSY.values())construction.revoked=true;
        for(Launch launch:FLIGHTS.values())launch.revoked=true;
        FLIGHTS.clear();DEBUFFS.clear(); // active Java hit/birth frames are released only by their owning finally
    }
}
