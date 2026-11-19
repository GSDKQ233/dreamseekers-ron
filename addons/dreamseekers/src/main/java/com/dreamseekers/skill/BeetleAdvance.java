// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.BeetleUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Self speed plus one actual enhanced primary. No target/order/path/cargo is stored or restored.
 * Unlike Wind's pre-armor strike, the optional 30% applies only to final remaining health damage.
 */
public final class BeetleAdvance {
    public static final double MOVEMENT_BONUS=.25;
    public static final float DAMAGE_MULTIPLIER=1.30f;
    public static final String MOVEMENT_KEY="beetle_advance";
    private static final Map<BeetleUnit,Advance> ACTIVE=new IdentityHashMap<>();
    private static final Map<BeetleUnit,Frame> ATTEMPTS=new IdentityHashMap<>();
    private BeetleAdvance() { }
    private static final class Advance {
        final SkillIdentity.Stamp caster;
        final long expiresAt;
        boolean spent;
        Advance(BeetleUnit source) {
            caster=SkillIdentity.capture(source);
            expiresAt=source.level().getGameTime()+UnitSkill.BEETLE_ADVANCE.durationTicks();
        }
        boolean valid() { return loaded(caster)&&caster.level().getGameTime()<expiresAt; }
    }
    static final class Frame {
        final BeetleUnit source;
        final Advance advance;
        final SkillIdentity.Stamp caster,target;
        final long commandRevision;
        int invocationDepth;
        boolean callStarted,bound,active,entered,revoked;
        DamageSource damage;
        Frame(BeetleUnit source,LivingEntity target,Advance advance) {
            this.source=source;this.advance=advance;caster=advance.caster;
            this.target=SkillIdentity.capture(target);commandRevision=source.rts().commandRevision;
        }
        boolean legal() { return loaded(caster)&&loaded(target)&&CoordinatedMark.enemy(source,target.entity()); }
        boolean enhanced() {
            return !revoked&&legal()&&ACTIVE.get(source)==advance&&advance.valid()&&!advance.spent
                    &&source.rts().commandRevision==commandRevision
                    &&!UnitServerEvents.hasPendingImmediateAction(source.getId())&&!disabled(source)
                    &&source.hasLineOfSight(target.entity())&&inReach(source,target.entity());
        }
    }
    private static boolean server(LivingEntity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    static boolean loaded(SkillIdentity.Stamp stamp) {
        LivingEntity entity=stamp.entity();
        return stamp.valid()&&entity.isAddedToWorld()&&entity.getUUID().equals(stamp.uuid())
                &&stamp.level().getEntity(entity.getId())==entity;
    }
    private static boolean disabled(BeetleUnit source) {
        return source.hasEffect(MobEffectRegistrar.STUN.get())||source.hasEffect(MobEffectRegistrar.FREEZE.get())
                ||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static boolean inReach(BeetleUnit source,LivingEntity target) {
        float width=source.getBbWidth()+source.getBonusMeleeRange();
        float targetWidth=target.getBbWidth()+(target instanceof Unit unit?unit.getBonusMeleeRangeForAttackers():0);
        return source.distanceToSqr(target)<=width*2f*width*2f+targetWidth;
    }
    public static boolean active(LivingEntity source) {
        if(!(source instanceof BeetleUnit beetle)||!server(source))return false;
        Advance advance=ACTIVE.get(beetle);return advance!=null&&advance.valid();
    }
    public static int remainingTicks(BeetleUnit source) {
        return active(source)?(int)Math.max(0,ACTIVE.get(source).expiresAt-source.level().getGameTime()):0;
    }
    public static boolean firstStrikeAvailable(BeetleUnit source) {
        return active(source)&&!ACTIVE.get(source).spent;
    }
    static SkillResult start(BeetleUnit source) {
        if(!server(source)||!source.isAlive()||source.isRemoved()||!source.isAddedToWorld()
                ||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(active(source)||ATTEMPTS.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        Advance advance=new Advance(source);
        if(!advance.valid())return SkillResult.INVALID_SOURCE;
        if(!SkillEffects.addBuff(source,source,MOVEMENT_KEY,BuffKind.MOVEMENT_SPEED,
                MOVEMENT_BONUS,UnitSkill.BEETLE_ADVANCE.durationTicks()))return SkillResult.ALREADY_ACTIVE;
        ACTIVE.put(source,advance);return SkillResult.SUCCESS;
    }
    public static boolean inAttempt(Mob source) {
        return source instanceof BeetleUnit beetle&&server(source)&&ATTEMPTS.containsKey(beetle);
    }
    static boolean canAttempt(Mob source,Entity victim) {
        return source instanceof BeetleUnit beetle&&firstStrikeAvailable(beetle)&&!ATTEMPTS.containsKey(beetle)
                &&victim instanceof LivingEntity target&&target instanceof Unit
                &&CoordinatedMark.enemy(source,target);
    }
    /** Invoked only by the real ordinary melee goal. That goal has already spent its own attack cooldown. */
    static boolean melee(BeetleUnit source,LivingEntity target) {
        Advance advance=ACTIVE.get(source);
        Frame frame=new Frame(source,target,advance);ATTEMPTS.put(source,frame);
        try { return source.doHurtTarget(target); }
        finally { ATTEMPTS.remove(source,frame); }
    }
    /** The paid subtype identifies the exact outer virtual call; callback melee may not steal its scope. */
    public static boolean invokeMelee(BeetleUnit source,Entity target,BooleanSupplier original) {
        if(!server(source))return original.getAsBoolean();
        Frame frame=ATTEMPTS.get(source);
        if(frame==null)return original.getAsBoolean();
        frame.invocationDepth++;
        try {
            if(frame.invocationDepth==1&&target==frame.target.entity())frame.callStarted=true;
            return original.getAsBoolean();
        } finally { frame.invocationDepth--; }
    }
    /** Existing Mob redirect binds the exact real DamageSource after native evasion/enchantment setup. */
    public static boolean hurtFromMob(Mob source,Entity target,DamageSource damage,float amount,BooleanSupplier original) {
        Frame frame=source instanceof BeetleUnit beetle&&server(source)?ATTEMPTS.get(beetle):null;
        if(frame==null||!frame.callStarted||frame.invocationDepth!=1||frame.bound||target!=frame.target.entity())
            return original.getAsBoolean();
        frame.bound=true;frame.active=true;frame.damage=damage;
        try { return original.getAsBoolean(); }
        finally { frame.active=false;frame.damage=null; }
    }
    static Frame matching(LivingEntity target,DamageSource damage) {
        if(!server(target)||!(damage.getEntity() instanceof BeetleUnit source))return null;
        Frame frame=ATTEMPTS.get(source);
        return frame!=null&&frame.active&&frame.damage==damage&&frame.target.entity()==target?frame:null;
    }
    /** Same-DS recursion forfeits this attempt's bonus, not either legitimate base hurt or the future token. */
    public static void observeHurt(LivingEntity target,DamageSource damage) {
        Frame frame=matching(target,damage);if(frame==null)return;
        if(frame.entered)frame.revoked=true;
        frame.entered=true;
    }
    static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity target,DamageSource damage) {
        Frame frame=matching(target,damage);if(frame==null||!frame.entered)return null;
        Impact impact=new Impact(frame);
        return new FinalUnitDamageHooks.OrdinaryAttack() {
            @Override public float beforeArmor(float amount) { return frame.legal()?amount:0; }
            @Override public float validateAfterHurt(float amount) { return frame.legal()?amount:0; }
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal() {
                return SkillEffects.prepareBeetleFinal(target,damage,impact);
            }
        };
    }
    static final class Impact {
        final Frame frame;
        boolean reported;
        Impact(Frame frame) { this.frame=frame; }
        boolean legal() { return !reported&&frame.legal(); }
        boolean enhanced() { return !reported&&frame.enhanced(); }
        void applied(boolean enhanced,float healthLoss,float shieldLoss) {
            if(reported)return;reported=true;
            if(!enhanced||!(healthLoss>0||shieldLoss>0)||frame.revoked
                    ||ACTIVE.get(frame.source)!=frame.advance||!frame.advance.valid()||frame.advance.spent
                    ||!frame.target.identityValid()||!frame.target.entity().isAddedToWorld()
                    ||!frame.target.entity().getUUID().equals(frame.target.uuid())
                    ||frame.source.rts().commandRevision!=frame.commandRevision
                    ||UnitServerEvents.hasPendingImmediateAction(frame.source.getId())
                    ||frame.caster.owner().equals(frame.target.owner())
                    ||com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(frame.caster.owner(),frame.target.owner()))return;
            frame.advance.spent=true;
        }
    }
    static void tick(ServerLevel level) {
        if(level.getServer().isSameThread())ACTIVE.entrySet().removeIf(e->e.getKey().level()==level&&!e.getValue().valid());
    }
    static void clear(LivingEntity entity) {
        if(entity instanceof BeetleUnit source&&server(source))ACTIVE.remove(source);
        // In-flight reservations remain until their original finally; invalid state cannot rearm or consume them.
    }
    static void stop() { ACTIVE.clear();ATTEMPTS.clear(); }
}
