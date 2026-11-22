// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.unit.ShadowHandUnit;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** A short paid reservation and a removable autonomous-input root, never a stun or position lock.
 * All leases/protection/passive hit tokens are transient; no movement snapshot is restored.
 */
public final class ShadowHandGrip {
    public static final int RANGE=6,WINDUP_TICKS=10,ROOT_TICKS=20,RECONTROL_TICKS=100;
    public static final double CONDITIONAL_BONUS=.20;
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","shadow_hand_grip");
    private static final Map<ShadowHandUnit,Charge> CHARGES=new IdentityHashMap<>();
    private static final Map<LivingEntity,Root> ROOTS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Protection> PROTECTIONS=new IdentityHashMap<>();
    private static final ArrayDeque<Passive> HITS=new ArrayDeque<>();
    private ShadowHandGrip() { }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp!=null&&stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean body(LivingEntity entity) {
        return server(entity)&&entity.isAlive()&&!entity.isRemoved()&&entity.isAddedToWorld()
                &&entity.level().getEntity(entity.getId())==entity
                &&((ServerLevel)entity.level()).getEntity(entity.getUUID())==entity;
    }
    /** Fixed current registrar snapshot, not an open admission rule for arbitrary future mods/players. */
    private static boolean supported(LivingEntity target) {
        return target instanceof Unit&&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)
                &&Supported.TYPES.contains(target.getType());
    }
    private static final class Supported {
        static final Set<EntityType<?>> TYPES=Set.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BAT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.HUSK_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.DROWNED_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOGLIN_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SKELETON_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.STRAY_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BOGGED_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.CREEPER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SPIDER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.POISON_SPIDER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WRAITH_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SCOUT_DOG_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SCOUT_CAT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.MILITIA_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.TEMPORARY_MILITIA_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WINDCALLER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_GOLEM_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WITCH_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.EVOKER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ENDERMAN_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.RAVAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WARDEN_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SILVERFISH_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.STRIDER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.HEADHUNTER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.MARAUDER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.HOGLIN_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ARMOURED_HOGLIN_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BLAZE_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WITHER_SKELETON_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GHAST_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.MAGMA_CUBE_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SLIME_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_GUARD_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ENCHANTER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.NECROMANCER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WRETCHED_WRAITH_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.PIGLIN_MERCHANT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WILDFIRE_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.POLAR_BEAR_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRIZZLY_BEAR_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.PANDA_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WOLF_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.LLAMA_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BEE_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.PHANTOM_SUMMON.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.HERO_EXPERIENCE_ORB.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.KILLER_RABBIT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.THROWABLE_TNT_PROJECTILE.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.THROWN_HERO_EXPERIENCE_BOTTLE.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ADJUSTABLE_PRIMED_TNT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.NECROMANCER_PROJECTILE.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WINDCALLER_PROJECTILE.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WRAITH_SNOWBALL.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.MOLTEN_BOMB_PROJECTILE.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.TOTEM_OF_REGENERATION.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.TOTEM_OF_CASTING.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.TOTEM_OF_PROTECTION.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.TOTEM_OF_SHIELDING.get(),
            com.dreamseekers.DreamseekersMod.FIREFLY_WORKER.get(),
            com.dreamseekers.DreamseekersMod.PINK_CHICKEN_SCOUT.get(),
            com.dreamseekers.DreamseekersMod.GOLDEN_FOX_UNIT.get(),
            com.dreamseekers.DreamseekersMod.PINK_SLIME_UNIT.get(),
            com.dreamseekers.DreamseekersMod.JELLYFISH_UNIT.get(),
            com.dreamseekers.DreamseekersMod.BONE_WING_UNIT.get(),
            com.dreamseekers.DreamseekersMod.BONE_WING_BOLT.get(),
            com.dreamseekers.DreamseekersMod.WIND_KNIGHT_UNIT.get(),
            com.dreamseekers.DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),
            com.dreamseekers.DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),
            com.dreamseekers.DreamseekersMod.STONE_HEAVY_PROJECTILE.get(),
            com.dreamseekers.DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get(),
            com.dreamseekers.DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get(),
            com.dreamseekers.DreamseekersMod.WEEPING_SQUEAL_WAVE_BOLT.get(),
            com.dreamseekers.DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get(),
            com.dreamseekers.DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get(),
            com.dreamseekers.DreamseekersMod.SHADOW_GOLEM_UNIT.get(),
            com.dreamseekers.DreamseekersMod.TERRORBEAK_UNIT.get(),
            com.dreamseekers.DreamseekersMod.BEETLE_MOTHER_UNIT.get(),
            com.dreamseekers.DreamseekersMod.SHADOW_GHOST_UNIT.get(),
            com.dreamseekers.DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get(),
            com.dreamseekers.DreamseekersMod.SUPPRESSION_WAVE_PROJECTILE.get(),
            com.dreamseekers.DreamseekersMod.SQUEAL_WAVE_BOLT.get(),
            com.dreamseekers.DreamseekersMod.FRIENDLY_GHOST_UNIT.get(),
            com.dreamseekers.DreamseekersMod.FRIENDLY_GHOST_BOLT.get(),
            com.dreamseekers.DreamseekersMod.ASH_BONE_WING_UNIT.get(),
            com.dreamseekers.DreamseekersMod.ASH_BONE_WING_BOLT.get(),
            com.dreamseekers.DreamseekersMod.SHADOW_HAND_UNIT.get(),
            com.dreamseekers.DreamseekersMod.JUNGLE_SPORE_CARRIER.get(),
            com.dreamseekers.DreamseekersMod.STONE_SPIRIT_WORKER.get(),
            com.dreamseekers.DreamseekersMod.BEETLE.get(),
            com.dreamseekers.DreamseekersMod.BASALT_SNAIL.get()
        );
    }
    private static boolean enemy(ShadowHandUnit source,LivingEntity target) {
        return target!=source&&body(target)&&supported(target)&&!target.isSpectator()
                &&target.level()==source.level()&&CoordinatedMark.enemy(source,target);
    }
    private static boolean disabled(ShadowHandUnit source) {
        return source.isPassenger()||source.hasEffect(MobEffectRegistrar.STUN.get())
                ||source.hasEffect(MobEffectRegistrar.FREEZE.get())||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static final class Charge {
        final ShadowHandUnit source;
        final SkillIdentity.Stamp caster,target;
        final UUID viewer;
        final long commandRevision,releaseAt;
        Charge(ServerPlayer viewer,ShadowHandUnit source,LivingEntity target) {
            this.source=source;caster=SkillIdentity.capture(source);this.target=SkillIdentity.capture(target);
            this.viewer=viewer.getUUID();commandRevision=source.rts().commandRevision;
            releaseAt=source.level().getGameTime()+WINDUP_TICKS;
        }
        boolean valid() { return valid(false); }
        boolean valid(boolean loadedOnly) {
            ServerPlayer player=caster.level().getServer().getPlayerList().getPlayer(viewer);
            return CHARGES.get(source)==this&&loaded(caster)&&loaded(target)&&!disabled(source)
                    &&source.rts().commandRevision==commandRevision&&!UnitServerEvents.hasPendingImmediateAction(source.getId())
                    &&player!=null&&player.isAlive()&&!player.isRemoved()&&player.serverLevel()==caster.level()
                    &&player.getGameProfile().getName().equals(caster.owner())&&enemy(source,target.entity())
                    &&source.distanceToSqr(target.entity())<=RANGE*RANGE
                    &&(loadedOnly?BeetleMotherCommandAura.hasLoadedLineOfSight(caster.level(),source,target.entity())
                                 :source.hasLineOfSight(target.entity()))
                    &&CoordinatedMark.visible(player,target.entity());
        }
    }
    /** Same loaded body protection survives owner ABA and removal of the short root. */
    private record Protection(LivingEntity target,ServerLevel level,UUID uuid,long until) {
        boolean current() {
            return body(target)&&target.level()==level&&target.getUUID().equals(uuid)
                    &&level.getEntity(uuid)==target&&level.getGameTime()<until;
        }
    }
    private static final class Root implements SporeEffects.ManagedNegative {
        final SkillIdentity.Stamp caster,target;
        final long expiresAt;
        Root(SkillIdentity.Stamp caster,SkillIdentity.Stamp target,int duration) {
            this.caster=caster;this.target=target;expiresAt=target.level().getGameTime()+duration;
        }
        @Override public ResourceLocation id() { return EFFECT_ID; }
        @Override public boolean current() {
            return ROOTS.get(target.entity())==this&&loaded(caster)&&loaded(target)
                    &&target.level().getGameTime()<expiresAt&&CoordinatedMark.enemy(caster.entity(),target.entity());
        }
        @Override public boolean remove() { return current()&&ROOTS.remove(target.entity(),this); }
    }
    public static boolean active(ShadowHandUnit source) {
        if(!server(source))return false;
        Charge charge=CHARGES.get(source);return charge!=null&&charge.valid();
    }
    public static int remainingWindupTicks(ShadowHandUnit source) {
        return active(source)?(int)Math.max(0,CHARGES.get(source).releaseAt-source.level().getGameTime()):0;
    }
    /** Read an already visible, same-owner caster only; the release tick itself can still apply the root. */
    static boolean hasLiveWindupOn(ShadowHandUnit source,LivingEntity target) {
        if(!server(source))return false;
        Charge charge=CHARGES.get(source);
        return charge!=null&&charge.target.entity()==target&&source.level().getGameTime()<=charge.releaseAt
                &&charge.valid(true);
    }
    public static boolean hasRoot(LivingEntity target) { return remainingRootTicks(target)>0; }
    public static int remainingRootTicks(LivingEntity target) {
        if(!server(target))return 0;
        Root root=ROOTS.get(target);return root!=null&&root.current()?(int)(root.expiresAt-target.level().getGameTime()):0;
    }
    public static int remainingRecontrolTicks(LivingEntity target) {
        if(!server(target))return 0;
        Protection protection=PROTECTIONS.get(target);return protection!=null&&protection.current()?(int)(protection.until-target.level().getGameTime()):0;
    }
    public static SporeEffects.ManagedNegative negative(LivingEntity target) {
        if(!server(target))return null;
        Root root=ROOTS.get(target);return root!=null&&root.current()?root:null;
    }
    /** Read only by three exact autonomous-input sites. Never reads/rewrites velocity or target orders. */
    public static boolean blocksAutonomousMovement(LivingEntity target) { return hasRoot(target); }
    static SkillResult start(ServerPlayer viewer,ShadowHandUnit source,LivingEntity target,BooleanSupplier commit) {
        if(viewer==null||commit==null||!body(source)||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(disabled(source)||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.DISABLED;
        if(CHARGES.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        if(!enemy(source,target))return SkillResult.INVALID_TARGET;
        if(source.distanceToSqr(target)>RANGE*RANGE)return SkillResult.RANGE;
        if(!source.hasLineOfSight(target)||!CoordinatedMark.visible(viewer,target))return SkillResult.HIDDEN;
        if(hasRoot(target)||remainingRecontrolTicks(target)>0)return SkillResult.ALREADY_ACTIVE;
        Charge charge=new Charge(viewer,source,target);CHARGES.put(source,charge);
        boolean committed=false;
        try {
            if(!charge.valid()||!commit.getAsBoolean())return SkillResult.REPLAY;
            committed=true;return SkillResult.SUCCESS;
        } finally { if(!committed)CHARGES.remove(source,charge); }
    }
    public static void commandChanged(ShadowHandUnit source) { if(server(source))CHARGES.remove(source); }
    public static void beforeTick(ShadowHandUnit source) {
        if(!server(source))return;
        Charge charge=CHARGES.get(source);if(charge!=null&&!charge.valid())CHARGES.remove(source,charge);
    }
    public static void afterTick(ShadowHandUnit source) {
        if(!server(source))return;
        Charge charge=CHARGES.get(source);if(charge==null)return;
        if(!charge.valid()) { CHARGES.remove(source,charge);return; }
        if(source.level().getGameTime()<charge.releaseAt)return;
        // No animation/sound/damage callback can manufacture another release from this reservation.
        LivingEntity target=charge.target.entity();
        if(hasRoot(target)||remainingRecontrolTicks(target)>0) { CHARGES.remove(source,charge);return; }
        int duration=SporeEffects.incomingManagedDuration(target,ROOT_TICKS);
        if(duration<=0||!charge.valid()||hasRoot(target)||remainingRecontrolTicks(target)>0) {
            CHARGES.remove(source,charge);return;
        }
        CHARGES.remove(source,charge);
        ROOTS.put(target,new Root(charge.caster,charge.target,duration));
        PROTECTIONS.put(target,new Protection(target,charge.target.level(),charge.target.uuid(),target.level().getGameTime()+RECONTROL_TICKS));
    }

    /** Capture the original known movement-slow instances, not a final-time effect lookup. */
    private static boolean positive(MobEffectInstance effect) {
        return effect!=null&&effect.getAmplifier()>=0&&(effect.isInfiniteDuration()||effect.getDuration()>0);
    }
    static final class Passive {
        final SkillIdentity.Stamp caster,target;
        final MobEffectInstance slow,minor;
        final Root root;
        DamageSource damage;
        boolean entered,revoked;
        Passive(ShadowHandUnit source,LivingEntity target) {
            caster=SkillIdentity.capture(source);this.target=SkillIdentity.capture(target);
            MobEffectInstance a=target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            MobEffectInstance b=target.getEffect(MobEffectRegistrar.MINOR_MOVEMENT_SLOWDOWN.get());
            slow=positive(a)?a:null;minor=positive(b)?b:null;
            Root candidate=ROOTS.get(target);root=candidate!=null&&candidate.current()?candidate:null;
        }
        boolean qualifying() {
            return slow!=null&&target.entity().getEffect(MobEffects.MOVEMENT_SLOWDOWN)==slow&&positive(slow)
                    ||minor!=null&&target.entity().getEffect(MobEffectRegistrar.MINOR_MOVEMENT_SLOWDOWN.get())==minor&&positive(minor)
                    ||root!=null&&root.current();
        }
        boolean valid() {
            return !revoked&&loaded(caster)&&loaded(target)&&CoordinatedMark.enemy(caster.entity(),target.entity())&&qualifying();
        }
    }
    /** Only OrdinaryAttackGate's exact final ordinary melee branch enters; neither tags nor skill hits grant it. */
    static boolean commitMelee(Mob body,Entity victim,BooleanSupplier original) {
        if(!(body instanceof ShadowHandUnit source)||!server(source)||!HITS.isEmpty()
                ||!(victim instanceof LivingEntity target)||!enemy(source,target))return original.getAsBoolean();
        Passive frame=new Passive(source,target);if(!frame.valid())return original.getAsBoolean();
        HITS.push(frame);
        try { return original.getAsBoolean(); }
        finally { HITS.removeFirstOccurrence(frame); }
    }
    public static void observeHurt(LivingEntity target,DamageSource damage) {
        Passive frame=HITS.peek();
        if(frame==null||frame.target.entity()!=target||damage.getEntity()!=frame.caster.entity()
                ||damage.getDirectEntity()!=frame.caster.entity()||!damage.is(DamageTypes.MOB_ATTACK)
                ||damage.is(DamageTypeTags.IS_PROJECTILE))return;
        if(frame.damage==null)frame.damage=damage;
        if(frame.damage!=damage)return;
        if(frame.entered)frame.revoked=true;
        frame.entered=true;
    }
    static Passive capturePassive(LivingEntity target,DamageSource damage) {
        Passive frame=HITS.peek();return frame!=null&&frame.entered&&frame.damage==damage&&frame.target.entity()==target?frame:null;
    }
    static float applyPassive(Passive frame,float amount) {
        return frame!=null&&frame.valid()&&Float.isFinite(amount)&&amount>0?(float)(amount*(1+CONDITIONAL_BONUS)):amount;
    }
    static void tick(ServerLevel level) {
        CHARGES.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().valid());
        ROOTS.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().current());
        PROTECTIONS.entrySet().removeIf(entry->entry.getValue().level==level&&!entry.getValue().current());
    }
    static void clearEffects(LivingEntity entity) {
        CHARGES.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().target.entity()==entity);
        ROOTS.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().caster.entity()==entity);
    }
    static void clear(LivingEntity entity,boolean ownerChanged) {
        clearEffects(entity);if(!ownerChanged)PROTECTIONS.remove(entity);
        // Active Java hit frames retain their finally owners; identity validity revokes only this bonus.
    }
    static void relationshipsChanged() {
        CHARGES.entrySet().removeIf(entry->!entry.getValue().valid());
        ROOTS.entrySet().removeIf(entry->!entry.getValue().current());
    }
    static void stop() { CHARGES.clear();ROOTS.clear();PROTECTIONS.clear(); }
}
