// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.spore;

import com.dreamseekers.skill.FoxDreamSeedAutocast;
import com.dreamseekers.skill.SkillEffects;
import com.dreamseekers.skill.SkillIdentity;
import com.dreamseekers.unit.mixin.SporeEffectAccess;
import com.dreamseekers.unit.mixin.SporeLivingEffectAccess;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nullable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/** Activation-only cleansing and one-time incoming-effect duration transformation. Never ticks old durations. */
public final class SporeEffects {
    private SporeEffects() { }
    private static final Set<ResourceLocation> WHITELIST=Set.of(
            id("minecraft:slowness"),id("minecraft:weakness"),id("minecraft:poison"),id("minecraft:wither"),
            id("reignofnether:stun"),id("reignofnether:disarm"),id("reignofnether:minor_slowdown"),
            id("reignofnether:attack_slowdown"),id("reignofnether:cold"),id("reignofnether:damage_taken_increase"));
    private static ResourceLocation id(String value) { return java.util.Objects.requireNonNull(ResourceLocation.tryParse(value)); }
    private static final Map<LivingEntity,Protection> PROTECTIONS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Cleanse> CLEANSES=new IdentityHashMap<>();
    /** Throttle follows this loaded body, not its owner. Pending cleanse tickets still use strict epochs. */
    private record Protection(LivingEntity target,ServerLevel level,UUID uuid,long until) {
        boolean valid() { return target.isAlive()&&!target.isRemoved()&&target.level()==level
                &&target.getUUID().equals(uuid)&&level.getEntity(uuid)==target&&level.getGameTime()<until; }
    }
    /** Bounded self-managed negative contract. Only explicitly selected providers join the pool. */
    public interface ManagedNegative {
        ResourceLocation id();
        /** Only equal-ID multi-owner managed instances need a secondary deterministic key. */
        default String tieBreakKey() { return ""; }
        boolean current();
        boolean remove();
    }
    private record Candidate(SkillIdentity.Stamp target,ResourceLocation id,MobEffectInstance effect,ManagedNegative managed) {
        boolean current() {
            return effect!=null?target.entity().getEffect(effect.getEffect())==effect&&eligible(effect):managed!=null&&managed.current();
        }
    }
    /** Ephemeral proof for the original recipient and actual removable instance. A replacement of the
     * same potion, ownership ABA, an already-started cleanse or protection invalidates this selection.
     * A manual cast is still allowed on open ground with no such proof. */
    public static final class Need {
        private final Candidate candidate;
        private Need(Candidate candidate) { this.candidate=candidate; }
        public LivingEntity ally() { return candidate.target().entity(); }
        public boolean current() {
            return candidate.target().valid()&&!CLEANSES.containsKey(ally())
                    &&cleanseProtectionTicks(ally())==0&&candidate.current();
        }
    }
    /** Only the fox's actually observed foe is consulted for multi-owner debt. All other managed
     * providers are keyed by this exact target. Do not enumerate the world's open contracts to
     * decide whether a fox should spend its cooldown. Actual activation retains full native order. */
    @Nullable public static Need need(LivingEntity target,String enemyOwner) {
        if(!(target instanceof Unit)||!server(target)||CLEANSES.containsKey(target)
                ||cleanseProtectionTicks(target)!=0)return null;
        Candidate choice=choice(target,enemyOwner);
        return choice!=null?new Need(choice):null;
    }
    @Nullable private static Candidate choice(LivingEntity target,@Nullable String observedOwner) {
        var choices=new ArrayList<Candidate>();
        SkillIdentity.Stamp stamp=SkillIdentity.capture(target);
        for(MobEffectInstance effect:target.getActiveEffects())if(eligible(effect))
            choices.add(new Candidate(stamp,ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()),effect,null));
        addManaged(choices,stamp,com.dreamseekers.skill.ShadowCorrosionTouch.negative(target));
        addManaged(choices,stamp,com.dreamseekers.skill.SquealSuppressionWave.negative(target));
        addManaged(choices,stamp,com.dreamseekers.skill.AaroncosVortex.negative(target));
        addManaged(choices,stamp,com.dreamseekers.skill.AaroncosShockSuppression.negative(target));
        addManaged(choices,stamp,com.dreamseekers.skill.TerrorbeakDreadRoar.negative(target));
        addManaged(choices,stamp,com.dreamseekers.skill.ShadowHandGrip.negative(target));
        if(observedOwner==null) {
            for(var debt:com.dreamseekers.skill.FriendlyDebtContract.negatives(target))addManaged(choices,stamp,debt);
        } else {
            addManaged(choices,stamp,com.dreamseekers.skill.FriendlyDebtContract.negative(target,observedOwner));
        }
        return choices.stream().min(Comparator.comparing((Candidate c)->c.id().toString())
                .thenComparing(c->c.managed()==null?"":c.managed().tieBreakKey())).orElse(null);
    }
    private static void addManaged(List<Candidate> choices,SkillIdentity.Stamp stamp,@Nullable ManagedNegative effect) {
        if(effect!=null)choices.add(new Candidate(stamp,effect.id(),null,effect));
    }
    private static final class Cleanse {
        final SporeLease.Lease lease;
        final Candidate candidate;
        Cleanse(SporeLease.Lease lease,Candidate candidate) { this.lease=lease;this.candidate=candidate; }
        boolean valid() {
            LivingEntity target=candidate.target().entity();
            return CLEANSES.get(target)==this&&candidate.target().valid()&&inRange(lease,target)
                    &&candidate.current()&&cleanseProtectionTicks(target)==0;
        }
    }
    private static boolean server(LivingEntity entity) { return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread(); }
    private static boolean eligible(MobEffectInstance effect) {
        if(effect==null||effect.isInfiniteDuration()||effect.getDuration()<=0||effect.getDuration()==Integer.MAX_VALUE
                ||((SporeEffectAccess)effect).dreamseekers$hiddenEffect()!=null||effect.getFactorData().isPresent())return false;
        ResourceLocation key=ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());
        return key!=null&&WHITELIST.contains(key);
    }
    private static boolean inRange(SporeLease.Lease lease,LivingEntity target) {
        return lease.active()&&target instanceof Unit&&target.level()==lease.caster.level()
                &&lease.caster.level().getEntity(target.getUUID())==target
                &&SkillEffects.friendly(lease.caster.entity(),target)
                &&lease.carrier.distanceToSqr(target)<=SporeLease.RADIUS*SporeLease.RADIUS
                &&(lease.autoViewer==null?lease.carrier.hasLineOfSight(target)
                :FoxDreamSeedAutocast.observableRecipient(lease.autoViewer,lease.carrier,lease.caster,target));
    }
    /** Called exactly once only after published birth + cooldown/revision commit. */
    static void activate(SporeLease.Lease lease) {
        if(!lease.active()||lease.activated)return;
        lease.activated=true; // commit before any event callback
        List<Candidate> candidates=new ArrayList<>();
        for(LivingEntity target:lease.caster.level().getEntitiesOfClass(LivingEntity.class,
                lease.carrier.getBoundingBox().inflate(SporeLease.RADIUS),entity->inRange(lease,entity))) {
            if(cleanseProtectionTicks(target)>0)continue;
            // One deterministic candidate across potions and all native managed providers.
            Candidate choice=choice(target,null);
            if(choice!=null)candidates.add(choice);
        }
        candidates.sort(Comparator.comparing(candidate->candidate.target().uuid().toString()));
        for(Candidate candidate:candidates)cleanse(lease,candidate);
    }
    private static void cleanse(SporeLease.Lease lease,Candidate candidate) {
        LivingEntity target=candidate.target().entity();
        if(CLEANSES.containsKey(target))return;
        Cleanse ticket=new Cleanse(lease,candidate);CLEANSES.put(target,ticket);
        try {
            if(!ticket.valid())return;
            if(candidate.managed()!=null) {
                if(candidate.managed().remove())PROTECTIONS.put(target,new Protection(target,candidate.target().level(),candidate.target().uuid(),candidate.target().level().getGameTime()+SporeLease.CLEANSE_PROTECTION_TICKS));
                // No fake MobEffect removal event or potion packet for an attribute-only lease.
                return;
            }
            // Same Forge event, map removal and protected callback as LivingEntity.removeEffect.
            // The extra identity/authority check is between event callbacks and the actual map write.
            if(MinecraftForge.EVENT_BUS.post(new MobEffectEvent.Remove(target,candidate.effect().getEffect()))||!ticket.valid())return;
            MobEffectInstance removed=target.removeEffectNoUpdate(candidate.effect().getEffect());
            if(removed!=candidate.effect())return;
            PROTECTIONS.put(target,new Protection(target,candidate.target().level(),candidate.target().uuid(),candidate.target().level().getGameTime()+SporeLease.CLEANSE_PROTECTION_TICKS));
            // Send the successful deletion before onEffectRemoved callbacks can install a new effect.
            // The final integration uses only tracking + current-fog recipients, never core's ALL helper.
            com.dreamseekers.skill.SkillNetwork.sendCleansed(target,removed.getEffect());
            ((SporeLivingEffectAccess)target).dreamseekers$effectRemoved(removed);
        } finally { CLEANSES.remove(target,ticket); }
    }
    public static int cleanseProtectionTicks(LivingEntity target) {
        if(!server(target))return 0;
        Protection protection=PROTECTIONS.get(target);
        if(protection==null)return 0;
        if(!protection.valid()) { PROTECTIONS.remove(target,protection);return 0; }
        return (int)Math.max(0,protection.until()-protection.level().getGameTime());
    }
    /** A newly created explicit managed negative asks once, never on refresh or each tick.
     * Cleanse protection throttles removals; it does not grant immunity to new negatives.
     */
    public static int incomingManagedDuration(LivingEntity target,int duration) {
        if(duration<=0||!server(target)||!(target instanceof Unit))return duration;
        if(SporeLease.activeLeases((ServerLevel)target.level()).stream().noneMatch(lease->inRange(lease,target)))return duration;
        return Math.max(1,(int)Math.ceil(duration*SporeLease.NEW_NEGATIVE_DURATION_FACTOR));
    }
    /** Eligibility is snapshotted once at entry; original Applicable/immunity/Added/merge remain downstream. */
    public static MobEffectInstance incoming(LivingEntity target,MobEffectInstance incoming) {
        if(!server(target)||!(target instanceof Unit)||!eligible(incoming)||transformed(incoming))return incoming;
        if(SporeLease.activeLeases((ServerLevel)target.level()).stream().noneMatch(lease->inRange(lease,target)))return incoming;
        int duration=Math.max(1,(int)Math.ceil(incoming.getDuration()*SporeLease.NEW_NEGATIVE_DURATION_FACTOR));
        MobEffectInstance copy=new MobEffectInstance(incoming.getEffect(),duration,incoming.getAmplifier(),incoming.isAmbient(),
                incoming.isVisible(),incoming.showIcon(),null,Optional.empty());
        // Access without invoking getCurativeItems(): that method lazily mutates the caller's object.
        List<ItemStack> curatives=((SporeEffectAccess)incoming).dreamseekers$curativeItems();
        if(curatives!=null) {
            List<ItemStack> copied=new ArrayList<>();for(ItemStack stack:curatives)copied.add(stack.copy());
            copy.setCurativeItems(copied); // Forge addCurativeItem must retain a mutable list
        }
        TRANSFORMED.add(new IdentityRef(copy,QUEUE));
        return copy;
    }
    private static final ReferenceQueue<MobEffectInstance> QUEUE=new ReferenceQueue<>();
    private static final Set<IdentityRef> TRANSFORMED=new HashSet<>();
    private static final class IdentityRef extends WeakReference<MobEffectInstance> {
        final int hash;
        IdentityRef(MobEffectInstance effect,ReferenceQueue<MobEffectInstance> queue) { super(effect,queue);hash=System.identityHashCode(effect); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) { return this==other||other instanceof IdentityRef ref&&get()!=null&&get()==ref.get(); }
    }
    private static boolean transformed(MobEffectInstance effect) {
        java.lang.ref.Reference<? extends MobEffectInstance> expired;
        while((expired=QUEUE.poll())!=null)TRANSFORMED.remove(expired);
        return TRANSFORMED.contains(new IdentityRef(effect,null));
    }
    static void tick(ServerLevel level) {
        PROTECTIONS.entrySet().removeIf(entry->entry.getValue().level()==level&&!entry.getValue().valid());
    }
    static void clear(LivingEntity target) { if(server(target))PROTECTIONS.remove(target); }
    static void stop() { PROTECTIONS.clear();CLEANSES.clear();TRANSFORMED.clear();while(QUEUE.poll()!=null) { } }
}
