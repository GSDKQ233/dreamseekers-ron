// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.BeetleUnit;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Nearby combatants protect only this beetle. Each hurt captures original supporters before Forge callbacks.
 * This defensive authority is not Bone's offensive ordinary-factory grant and never grants an attack bonus.
 */
public final class BeetleGuard {
    public static final double RANGE=3, REDUCTION=.10;
    private static final ArrayDeque<Hit> HITS=new ArrayDeque<>();
    private static final ArrayDeque<ProjectileFrame> PROJECTILES=new ArrayDeque<>();
    private static final Map<Projectile,OwnerEpoch> OWNERS=new WeakHashMap<>();
    private BeetleGuard() { }
    private record Hit(BeetleUnit target,DamageSource damage,Plan plan) { }
    private static final class OwnerEpoch {
        Entity owner;
        long revision;
        OwnerEpoch(Projectile projectile) { owner=projectile.getOwner(); }
        void observe(Projectile projectile) {
            if(owner!=projectile.getOwner()) { owner=projectile.getOwner();revision++; }
        }
    }
    static final class ProjectileProof {
        final Projectile projectile;
        final SkillIdentity.Stamp caster;
        final java.util.UUID uuid;
        final OwnerEpoch owner;
        final long revision;
        final boolean allowsConsumed;
        ProjectileProof(Projectile projectile,LivingEntity caster) {
            this.projectile=projectile;this.caster=SkillIdentity.capture(caster);uuid=projectile.getUUID();
            owner=OWNERS.computeIfAbsent(projectile,OwnerEpoch::new);owner.observe(projectile);revision=owner.revision;
            allowsConsumed=OrdinarySkillHit.consumedProjectile(projectile);
        }
        boolean valid() {
            if(!BeetleAdvance.loaded(caster)||projectile.level()!=caster.level()||!projectile.getUUID().equals(uuid)
                    ||projectile.getOwner()!=caster.entity()||owner.revision!=revision)return false;
            if(allowsConsumed&&projectile.getRemovalReason()==Entity.RemovalReason.DISCARDED)return true;
            return !projectile.isRemoved()&&projectile.isAddedToWorld()
                    &&caster.level().getEntity(uuid)==projectile&&caster.level().getEntity(projectile.getId())==projectile;
        }
    }
    private static final class ProjectileFrame {
        final ProjectileProof proof;
        final Entity victim;
        final DamageSource damage;
        boolean entered;
        ProjectileFrame(ProjectileProof proof,Entity victim,DamageSource damage) {
            this.proof=proof;this.victim=victim;this.damage=damage;
        }
    }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean excluded(DamageSource damage) {
        return damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||damage.is(DamageTypeTags.BYPASSES_EFFECTS)
                ||damage.is(DamageTypeTags.BYPASSES_RESISTANCE)||damage.is(DamageTypes.GENERIC_KILL)
                ||damage.is(DamageTypes.FELL_OUT_OF_WORLD);
    }
    private static boolean supporter(BeetleUnit target,LivingEntity other) {
        return other!=target&&other instanceof Unit&&!other.isSpectator()&&other.isAddedToWorld()
                &&other.level()==target.level()&&other.isAlive()&&!other.isRemoved()
                &&other.getUUID()!=null&&((ServerLevel)target.level()).getEntity(other.getUUID())==other
                &&((ServerLevel)target.level()).getEntity(other.getId())==other
                &&target.distanceToSqr(other)<=RANGE*RANGE&&SkillEffects.friendly(target,other)
                &&SkillService.blessingCombatUnit(other);
    }
    /** Current presentation query; actual hits use captured original supporter identities instead. */
    public static double reduction(LivingEntity target) {
        if(!(target instanceof BeetleUnit beetle)||!server(target)||!target.isAlive()||!target.isAddedToWorld())return 0;
        return target.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(RANGE),
                candidate->supporter(beetle,candidate)).isEmpty()?0:REDUCTION;
    }
    /** Only this actual primary-call wrapper may authenticate RoN's unpublished proxy-arrow DamageSource. */
    static PrimaryUnitHitHooks.Impact wrapProjectile(Projectile projectile,Entity victim,PrimaryUnitHitHooks.Impact original) {
        if(!(victim instanceof BeetleUnit)||!server(projectile)||projectile.isRemoved()||!projectile.isAddedToWorld()
                ||!(projectile.getOwner() instanceof LivingEntity owner)||!(owner instanceof Unit)
                ||projectile.level()!=victim.level()||!CoordinatedMark.enemy(owner,(LivingEntity)victim)
                ||((ServerLevel)projectile.level()).getEntity(projectile.getUUID())!=projectile
                ||((ServerLevel)projectile.level()).getEntity(projectile.getId())!=projectile)return original;
        ProjectileProof proof=new ProjectileProof(projectile,owner);
        PrimaryUnitHitHooks.Impact delegate=original==null?victim::hurt:original;
        return new PrimaryUnitHitHooks.Impact() {
            boolean used;
            @Override public boolean hurt(DamageSource damage,float amount) {
                if(used)return false;
                used=true;
                ProjectileFrame frame=new ProjectileFrame(proof,victim,damage);PROJECTILES.push(frame);
                try { return delegate.hurt(damage,amount); }
                finally { PROJECTILES.removeFirstOccurrence(frame); }
            }
        };
    }
    public static void projectileOwnerChanged(Projectile projectile) {
        if(!server(projectile))return;
        OwnerEpoch owner=OWNERS.get(projectile);if(owner!=null)owner.observe(projectile);
    }
    static final class Plan {
        final SkillIdentity.Stamp target,actor;
        final List<SkillIdentity.Stamp> supporters;
        final ProjectileProof projectile;
        boolean nativeResistance;
        Plan(BeetleUnit target,LivingEntity actor,List<SkillIdentity.Stamp> supporters,ProjectileProof projectile) {
            this.target=SkillIdentity.capture(target);this.actor=SkillIdentity.capture(actor);
            this.supporters=supporters;this.projectile=projectile;
        }
        boolean valid(DamageSource damage) {
            if(excluded(damage)||!BeetleAdvance.loaded(target)||!BeetleAdvance.loaded(actor)
                    ||damage.getEntity()!=actor.entity()||!CoordinatedMark.enemy(actor.entity(),target.entity())
                    ||(projectile!=null&&!projectile.valid()))return false;
            return supporters.stream().anyMatch(stamp->BeetleAdvance.loaded(stamp)
                    &&supporter((BeetleUnit)target.entity(),stamp.entity()));
        }
        float apply(DamageSource damage,float amount) { return valid(damage)?(float)(amount*(1-REDUCTION)):amount; }
    }
    private static Plan capture(BeetleUnit target,DamageSource damage) {
        if(excluded(damage)||!(damage.getEntity() instanceof LivingEntity actor)||!(actor instanceof Unit)
                ||!CoordinatedMark.enemy(actor,target))return null;
        ProjectileProof projectile=null;
        Entity direct=damage.getDirectEntity();
        ProjectileFrame scoped=PROJECTILES.peek();
        if(scoped!=null&&!scoped.entered&&scoped.victim==target&&scoped.damage==damage
                &&scoped.proof.caster.entity()==actor) {
            scoped.entered=true;projectile=scoped.proof;
            if(!projectile.valid())return null;
        } else if(direct instanceof Projectile shot) {
            if(!server(shot)||shot.isRemoved()||!shot.isAddedToWorld()||shot.getOwner()!=actor
                    ||shot.level()!=target.level()||((ServerLevel)shot.level()).getEntity(shot.getUUID())!=shot
                    ||((ServerLevel)shot.level()).getEntity(shot.getId())!=shot)return null;
            projectile=new ProjectileProof(shot,actor);
        } else if(direct!=actor) return null; // no ownerless proxy, environment or anonymous indirect grant
        var supporters=new ArrayList<SkillIdentity.Stamp>();
        for(LivingEntity candidate:target.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(RANGE),entity->supporter(target,entity)))
            supporters.add(SkillIdentity.capture(candidate));
        if(supporters.isEmpty())return null;
        Plan plan=new Plan(target,actor,List.copyOf(supporters),projectile);
        return plan.valid(damage)?plan:null;
    }
    /** Called by the paid beetle before its native FALL gate and all LivingAttack/Hurt/Damage callbacks. */
    public static boolean hurt(BeetleUnit target,DamageSource damage,float amount,BooleanSupplier original) {
        if(!server(target))return original.getAsBoolean();
        Hit hit=new Hit(target,damage,capture(target,damage));HITS.push(hit);
        try { return original.getAsBoolean(); }
        finally { HITS.removeFirstOccurrence(hit); }
    }
    static Plan matching(LivingEntity target,DamageSource damage) {
        if(!server(target))return null;
        Hit hit=HITS.peek();return hit!=null&&hit.target==target&&hit.damage==damage?hit.plan:null;
    }
    static float apply(Plan plan,DamageSource damage,float amount) { return plan==null?amount:plan.apply(damage,amount); }
    static void stop() { HITS.clear();PROJECTILES.clear();OWNERS.clear(); }
}
