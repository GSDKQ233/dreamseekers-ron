// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.unit.ShadowGhostUnit;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Optional post-hit corrosion on one real ordinary melee. Never replaces ordinary/Bone damage,
 * rewrites a base attribute, or serializes an armed token or negative-effect lease.
 */
public final class ShadowCorrosionTouch {
    public static final int DEBUFF_TICKS=120;
    public static final double ARMOR_ADDITION=-2;
    public static final UUID MODIFIER_ID=UUID.fromString("bd25af86-5288-44ca-a4a1-ccf16b50a683");
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","shadow_corrosion_touch");
    private static final Map<ShadowGhostUnit,Arm> ARMS=new IdentityHashMap<>();
    private static final Map<ShadowGhostUnit,Frame> ATTEMPTS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Debuff> DEBUFFS=new IdentityHashMap<>();
    private ShadowCorrosionTouch() { }

    private static boolean server(LivingEntity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        LivingEntity entity=stamp.entity();
        return stamp.valid()&&entity.isAddedToWorld()&&entity.getUUID().equals(stamp.uuid())
                &&stamp.level().getEntity(entity.getId())==entity;
    }
    private static boolean enemy(LivingEntity source,LivingEntity target) {
        return !(target instanceof com.dreamseekers.spore.JungleSporeCarrier)&&CoordinatedMark.enemy(source,target);
    }
    private static boolean disabled(ShadowGhostUnit source) {
        return source.hasEffect(MobEffectRegistrar.STUN.get())||source.hasEffect(MobEffectRegistrar.FREEZE.get())
                ||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get())||source.hasEffect(MobEffectRegistrar.DISARM.get());
    }
    private static boolean inReach(ShadowGhostUnit source,LivingEntity target) {
        double reach=OrdinaryMeleeReach.rawSquared(source,source,target);
        return source.distanceToSqr(target)<=reach;
    }
    private static final class Arm {
        final SkillIdentity.Stamp caster;
        final long expiresAt;
        boolean spent;
        Arm(ShadowGhostUnit source) { caster=SkillIdentity.capture(source);expiresAt=source.level().getGameTime()+UnitSkill.SHADOW_CORROSION_TOUCH.durationTicks(); }
        boolean valid() { return !spent&&loaded(caster)&&caster.level().getGameTime()<expiresAt; }
    }
    private static final class Debuff implements SporeEffects.ManagedNegative {
        final SkillIdentity.Stamp caster,target;
        final AttributeInstance armor;
        final AttributeModifier modifier=new AttributeModifier(MODIFIER_ID,"dreamseekers_shadow_corrosion_touch",ARMOR_ADDITION,AttributeModifier.Operation.ADDITION);
        final long expiresAt;
        Debuff(Frame frame,AttributeInstance armor,int duration) {
            caster=frame.caster;target=frame.target;this.armor=armor;expiresAt=target.level().getGameTime()+duration;
        }
        @Override public ResourceLocation id() { return EFFECT_ID; }
        @Override public boolean current() {
            return DEBUFFS.get(target.entity())==this&&loaded(caster)&&loaded(target)
                    &&target.level().getGameTime()<expiresAt&&enemy(caster.entity(),target.entity())
                    &&target.entity().getAttribute(Attributes.ARMOR)==armor&&armor.getModifier(MODIFIER_ID)==modifier;
        }
        @Override public boolean remove() { return current()&&retire(this); }
    }
    private static final class Frame {
        final ShadowGhostUnit source;
        final Arm arm;
        final SkillIdentity.Stamp caster,target;
        final long commandRevision;
        DamageSource damage;
        int invocationDepth;
        boolean callStarted,bound,active,entered,revoked,receipt;
        Frame(ShadowGhostUnit source,LivingEntity target,Arm arm) {
            this.source=source;this.arm=arm;caster=arm.caster;this.target=SkillIdentity.capture(target);
            commandRevision=source.rts().commandRevision;
        }
        boolean eligible() {
            return !revoked&&ARMS.get(source)==arm&&arm.valid()&&loaded(target)&&enemy(source,target.entity())
                    &&source.rts().commandRevision==commandRevision&&!UnitServerEvents.hasPendingImmediateAction(source.getId())
                    &&!disabled(source)&&source.hasLineOfSight(target.entity())&&inReach(source,target.entity());
        }
    }
    /** Pure diagnostics: expired records are retired only by the normal lifecycle/tick or an actual application. */
    public static int remainingArmTicks(ShadowGhostUnit source) {
        if(!server(source))return 0;
        Arm arm=ARMS.get(source);return arm!=null&&arm.valid()?(int)Math.max(0,arm.expiresAt-source.level().getGameTime()):0;
    }
    public static boolean hasDebuff(LivingEntity target) { return remainingDebuffTicks(target)>0; }
    public static int remainingDebuffTicks(LivingEntity target) {
        if(!server(target))return 0;
        Debuff effect=DEBUFFS.get(target);return effect!=null&&effect.current()?(int)Math.max(0,effect.expiresAt-target.level().getGameTime()):0;
    }
    static SkillResult start(ShadowGhostUnit source) {
        if(!server(source)||!source.isAlive()||source.isRemoved()||!source.isAddedToWorld()||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(disabled(source))return SkillResult.DISABLED;
        if(remainingArmTicks(source)>0||ATTEMPTS.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        Arm arm=new Arm(source);if(!arm.valid())return SkillResult.INVALID_SOURCE;
        ARMS.put(source,arm);return SkillResult.SUCCESS;
    }
    /** Only the existing real goal bridge opens this optional frame; its ordinary/Bone path stays inside. */
    static boolean melee(Mob body,Entity victim,BooleanSupplier ordinary) {
        if(!(body instanceof ShadowGhostUnit source)||!server(source)||ATTEMPTS.containsKey(source)
                ||!(victim instanceof LivingEntity target)||!(target instanceof Unit)||!enemy(source,target))return ordinary.getAsBoolean();
        Arm arm=ARMS.get(source);if(arm==null||!arm.valid())return ordinary.getAsBoolean();
        Frame frame=new Frame(source,target,arm);ATTEMPTS.put(source,frame);
        try {
            boolean result=ordinary.getAsBoolean();
            // All native hurt/knockback/enchantment callbacks finish before armor changes. A receipt,
            // not the outer hurt boolean or an HP delta contaminated by nested hits, authorizes it.
            if(frame.receipt&&frame.eligible())install(frame);
            return result;
        } finally { ATTEMPTS.remove(source,frame); }
    }
    public static boolean invokeMelee(ShadowGhostUnit source,Entity target,BooleanSupplier ordinary) {
        if(!server(source))return ordinary.getAsBoolean();
        Frame frame=ATTEMPTS.get(source);if(frame==null)return ordinary.getAsBoolean();
        frame.invocationDepth++;
        try {
            if(frame.invocationDepth==1&&target==frame.target.entity())frame.callStarted=true;
            return ordinary.getAsBoolean();
        } finally { frame.invocationDepth--; }
    }
    /** Bind the native Mob.hurt invocation after ordinary evasion, never an invented damage source. */
    public static boolean hurtFromMob(Mob source,Entity target,DamageSource damage,float amount,BooleanSupplier ordinary) {
        Frame frame=source instanceof ShadowGhostUnit ghost&&server(source)?ATTEMPTS.get(ghost):null;
        if(frame==null||!frame.callStarted||frame.invocationDepth!=1||frame.bound||target!=frame.target.entity()
                ||damage.getEntity()!=source||damage.getDirectEntity()!=source||!damage.is(DamageTypes.MOB_ATTACK)
                ||damage.is(DamageTypeTags.IS_PROJECTILE))return ordinary.getAsBoolean();
        frame.bound=true;frame.active=true;frame.damage=damage;
        try { return ordinary.getAsBoolean(); }
        finally { frame.active=false;frame.damage=null; }
    }
    private static Frame matching(LivingEntity target,DamageSource damage) {
        if(!server(target)||!(damage.getEntity() instanceof ShadowGhostUnit source))return null;
        Frame frame=ATTEMPTS.get(source);
        return frame!=null&&frame.active&&frame.damage==damage&&frame.target.entity()==target?frame:null;
    }
    public static void observeHurt(LivingEntity target,DamageSource damage) {
        Frame frame=matching(target,damage);if(frame==null)return;
        if(frame.entered)frame.revoked=true;
        frame.entered=true; // Same-DS recursion forfeits only corrosion, never either ordinary base hit.
    }
    static Receipt captureReceipt(LivingEntity target,DamageSource damage) {
        Frame frame=matching(target,damage);return frame!=null&&frame.entered?new Receipt(frame):null;
    }
    static final class Receipt {
        private final Frame frame;
        private boolean reported;
        Receipt(Frame frame) { this.frame=frame; }
        void applied(float healthLoss,float gelLoss) {
            if(reported)return;reported=true;
            if((healthLoss>0||gelLoss>0)&&frame.eligible())frame.receipt=true;
        }
    }
    private static void install(Frame frame) {
        LivingEntity target=frame.target.entity();
        Debuff previous=DEBUFFS.get(target);
        if(previous!=null&&previous.current())return; // Never dismantle a valid named effect to probe armor.
        if(previous!=null)retire(previous);
        if(!frame.eligible())return;
        AttributeInstance armor=target.getAttribute(Attributes.ARMOR);
        if(armor==null||armor.getModifier(MODIFIER_ID)!=null)return;
        int before=target.getArmorValue();if(before<=0)return;
        int duration=SporeEffects.incomingManagedDuration(target,DEBUFF_TICKS);
        if(!frame.eligible())return;
        Debuff effect=new Debuff(frame,armor,duration);
        DEBUFFS.put(target,effect); // Exact reservation precedes dirty callbacks; no duplicate lease.
        boolean committed=false;
        try {
            armor.addTransientModifier(effect.modifier);
            if(!frame.eligible()||!effect.current()||target.getArmorValue()>=before)return;
            frame.arm.spent=true;committed=true;
        } finally { if(!committed)retire(effect); }
    }
    private static boolean retire(Debuff effect) {
        if(!DEBUFFS.remove(effect.target.entity(),effect))return false;
        boolean present=effect.armor.getModifier(MODIFIER_ID)==effect.modifier;
        if(present)effect.armor.removeModifier(MODIFIER_ID);
        return present;
    }
    /** One explicit Spore provider, not a registered/persistent global MobEffect. Pure capture only. */
    public static SporeEffects.ManagedNegative negative(LivingEntity target) {
        if(!server(target))return null;
        Debuff effect=DEBUFFS.get(target);return effect!=null&&effect.current()?effect:null;
    }
    /** Join/load cleanup strips only this skill's reserved modifier UUID. */
    static void removeModifier(LivingEntity target) {
        if(!server(target))return;
        DEBUFFS.remove(target);
        AttributeInstance armor=target.getAttribute(Attributes.ARMOR);
        if(armor!=null)armor.removeModifier(MODIFIER_ID);
    }
    static void tick(ServerLevel level) {
        if(!level.getServer().isSameThread())return;
        ARMS.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().valid());
        for(Debuff effect:List.copyOf(DEBUFFS.values()))if(effect.target.level()==level&&!effect.current())retire(effect);
    }
    static void clear(LivingEntity entity) {
        if(!server(entity))return;
        if(entity instanceof ShadowGhostUnit source)ARMS.remove(source);
        for(Debuff effect:List.copyOf(DEBUFFS.values()))if(effect.caster.entity()==entity||effect.target.entity()==entity)retire(effect);
        // Exact attempts remain owned by their original finally; lifecycle invalidity cannot turn
        // them into an untracked hit or make a later ordinary call inherit the old receipt.
    }
    static void relationshipsChanged() {
        for(Debuff effect:List.copyOf(DEBUFFS.values()))if(!effect.current())retire(effect);
    }
    static void stop() {
        for(Debuff effect:List.copyOf(DEBUFFS.values()))retire(effect);
        ARMS.clear();ATTEMPTS.clear();DEBUFFS.clear();
    }
}
