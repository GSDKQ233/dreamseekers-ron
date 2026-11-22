// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only, short-lived effects. No effect/trigger records are written to entity/world NBT.
 * Cooldowns are separate and remain spent after death/ownership changes/unload/restart.
 */
public final class SkillEffects {
    private SkillEffects() { }
    public static final FinalUnitDamageHooks.Handler HANDLER=new FinalUnitDamageHooks.Handler() {
        @Override public FinalUnitDamageHooks.Finalizer prepare(LivingEntity victim,DamageSource source) {
            return prepareFinalDamage(victim,source);
        }
        @Override public FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity victim,DamageSource source) {
            var bone=com.dreamseekers.bonewing.BoneWingBolt.prepareOrdinaryAttack(victim,source);if(bone!=null)return bone;
            var hand=AaroncosCombat.prepareOrdinaryAttack(victim,source);if(hand!=null)return hand;
            var strike=ShadowGolemStrike.prepareOrdinaryAttack(victim,source);
            if(strike!=null)return strike;
            var debt=FriendlyDebtContract.prepareOrdinaryAttack(victim,source);
            if(debt!=null)return debt;
            var suppression=SquealSuppressionWave.prepareOrdinaryAttack(victim,source);
            if(suppression!=null)return suppression;
            var heavy=StoneHeavyShot.prepareOrdinaryAttack(victim,source);
            if(heavy!=null)return heavy;
            var spore=com.dreamseekers.spore.SporeCombat.prepareOrdinary(victim,source);
            if(spore!=null)return spore;
            var beetle=BeetleAdvance.prepareOrdinaryAttack(victim,source);
            return beetle==null?WindDash.prepareOrdinaryAttack(victim,source):beetle;
        }
        @Override public FinalUnitDamageHooks.ResistanceObserver captureResistanceObserver(LivingEntity victim,DamageSource source) {
            BeetleGuard.Plan guard=BeetleGuard.matching(victim,source);
            SnailShellFormation.Plan candidate=SnailShellFormation.matching(victim,source);
            var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(victim,source);
            // Same-DS reentry revokes the outer projectile frame. Its inner native calculation
            // must not write an outer resistance receipt and suppress an independent beetle guard.
            SnailShellFormation.Plan formation=candidate!=null&&!candidate.revoked?candidate:null;
            if(guard==null&&formation==null&&ward==null)return null;
            // Capture exact plans now, not by looking up the current frame after nested callbacks.
            return reduction->{
                if(ward!=null)ward.nativeResistanceUsed(reduction);
                if(!Double.isFinite(reduction)||reduction<.2)return;
                if(guard!=null)guard.nativeResistance=true;
                if(formation!=null)formation.nativeResistance=true;
            };
        }
    };
    public static final float GEL_AMOUNT=12;
    public static final int REBOUND_TICKS=60;
    public static final double REBOUND_ATTACK_SPEED=.15;
    public static final int JELLY_PULSE_TICKS=20, JELLY_TETHER_RANGE=12, TIDE_TICKS=80;
    public static final float JELLY_PULSE_AMOUNT=2;
    public static final double TIDE_MOVEMENT_SPEED=.10;
    public static final String FOX_BLESSING_KEY="fox_blessing";
    public static final double FOX_ATTACK_SPEED=.20;
    public static final int FOX_MAX_TARGETS=3;
    private static final Map<ServerLevel, Runtime> WORLDS=new HashMap<>();
    private static final class Runtime {
        final Map<UUID,Shield> shields=new HashMap<>();
        final Map<UUID,List<Buff>> buffs=new HashMap<>();
        final Map<UUID,Heal> heals=new HashMap<>();
    }
    private static final class Shield {
        final SkillIdentity.Stamp caster, target;
        final long expiresAt;
        final SkillAmplification.Profile amplification;
        float remaining;
        Shield(SkillUnit source,LivingEntity target,SkillAmplification.Profile amplification) {
            this.amplification=amplification;remaining=amplification.scale(GEL_AMOUNT);
            caster=SkillIdentity.capture(source.mob());this.target=SkillIdentity.capture(target);
            expiresAt=target.level().getGameTime()+amplification.durationTicks();
        }
        boolean valid() { return remaining>0 && expiresAt>target.level().getGameTime()
                && caster.valid() && target.valid() && friendly(caster.entity(),target.entity()); }
    }
    private static final class Heal {
        final SkillIdentity.Stamp caster, target;
        final long expiresAt;
        long nextPulseAt;
        final SkillAmplification.Profile amplification;
        final float pulseAmount;
        float recovered;
        boolean processing;
        Heal(SkillUnit source,LivingEntity target,SkillAmplification.Profile amplification) {
            this.amplification=amplification;pulseAmount=amplification.scale(JELLY_PULSE_AMOUNT);
            caster=SkillIdentity.capture(source.mob());this.target=SkillIdentity.capture(target);
            long now=target.level().getGameTime();
            expiresAt=now+UnitSkill.JELLY_FLOW.durationTicks();nextPulseAt=now+JELLY_PULSE_TICKS;
        }
        // Inclusive deadline reserves the target until the sixth pulse has actually completed.
        boolean valid() { return expiresAt>=target.level().getGameTime() && caster.valid() && target.valid()
                && friendly(caster.entity(),target.entity())
                && caster.entity().distanceToSqr(target.entity())<=(double)JELLY_TETHER_RANGE*JELLY_TETHER_RANGE; }
    }
    private record Buff(SkillIdentity.Stamp caster,SkillIdentity.Stamp target,String key,
                        BuffKind kind,double amount,long expiresAt,boolean lateFox,java.util.function.BooleanSupplier liveGuard) {
        double permittedCap() { return lateFox&&kind==BuffKind.ATTACK_SPEED?.30:kind.cap; }
        boolean valid() { return expiresAt>target.level().getGameTime() && caster.valid()
                && target.valid() && friendly(caster.entity(),target.entity()) && guarded(); }
        private boolean guarded(){if(liveGuard==null)return true;try{return liveGuard.getAsBoolean();}catch(RuntimeException ignored){return false;}}

    }
    private static boolean server(LivingEntity target) {
        return target.level() instanceof ServerLevel level && level.getServer().isSameThread();
    }
    public static boolean friendly(LivingEntity source,LivingEntity target) {
        if (!(source instanceof Unit a) || !(target instanceof Unit b) || !source.isAlive()
                || !target.isAlive() || source.isRemoved() || target.isRemoved()
                || source.level()!=target.level() || a.getOwnerName().isBlank() || b.getOwnerName().isBlank()) return false;
        return a.getOwnerName().equals(b.getOwnerName()) || AlliancesServerEvents.isAllied(a.getOwnerName(),b.getOwnerName());
    }
    private static Runtime runtime(ServerLevel level) { return WORLDS.computeIfAbsent(level,l->new Runtime()); }
    /** Called only within the same authoritative server-thread cast transaction. */
    static boolean addShield(SkillUnit caster,LivingEntity target,SkillAmplification.Profile amplification) {
        if (amplification==null || !amplification.matches(caster,UnitSkill.GEL_GUARD) || !server(target) || caster.mob()==target || !friendly(caster.mob(),target)) return false;
        Runtime world=runtime((ServerLevel)target.level());
        Shield previous=world.shields.get(target.getUUID());
        if (previous!=null && previous.valid()) return false;
        world.shields.put(target.getUUID(),new Shield(caster,target,amplification));return true;
    }
    /** One named heal per target, regardless of which allied jellyfish casts it. */
    static boolean addHeal(SkillUnit caster,LivingEntity target,SkillAmplification.Profile amplification) {
        if(amplification==null || !amplification.matches(caster,UnitSkill.JELLY_FLOW) || !server(target)||caster.mob()==target||!friendly(caster.mob(),target)
                ||target.getHealth()>=target.getMaxHealth())return false;
        Runtime world=runtime((ServerLevel)target.level());
        Heal previous=world.heals.get(target.getUUID());
        if(previous!=null&&previous.valid())return false;
        world.heals.put(target.getUUID(),new Heal(caster,target,amplification));return true;
    }
    /** Occupancy includes the due final tick; an autocaster may never steal the final pulse. */
    public static boolean hasHeal(LivingEntity target) { return validHeal(target)!=null; }
    public static int healTicks(LivingEntity target) {
        Heal heal=validHeal(target);return heal==null?0:(int)Math.max(0,heal.expiresAt-target.level().getGameTime());
    }
    private static Heal validHeal(LivingEntity target) {
        if(!(target.level() instanceof ServerLevel level))return null;
        Runtime world=WORLDS.get(level);if(world==null)return null;
        Heal heal=world.heals.get(target.getUUID());
        if(heal!=null&&(!heal.valid()||heal.target.entity()!=target)) {
            world.heals.remove(target.getUUID(),heal);return null;
        }
        return heal;
    }
    /** Standard cancellable Forge heal hook, followed by an identity check before the HP write. */
    private static void tickHeal(Runtime world,Heal heal,long now) {
        UUID targetId=heal.target.uuid();
        if(world.heals.get(targetId)!=heal)return;
        if(!heal.valid()) { world.heals.remove(targetId,heal);return; }
        if(heal.processing)return; // nested alliance/heal callbacks cannot repeat or finish this pulse
        heal.processing=true;
        try {
            LivingEntity target=heal.target.entity();
            if(now>=heal.nextPulseAt&&heal.nextPulseAt<=heal.expiresAt) {
                heal.nextPulseAt+=JELLY_PULSE_TICKS; // consume the scheduled pulse BEFORE posting a callback
                float requested=Math.min(heal.pulseAmount,Math.max(0,target.getMaxHealth()-target.getHealth()));
                if(requested>0) {
                    float amount=ForgeEventFactory.onLivingHeal(target,requested);
                    if(world.heals.get(targetId)!=heal)return;
                    if(!heal.valid()) { world.heals.remove(targetId,heal);return; }
                    if(Float.isFinite(amount)&&amount>0) {
                        float before=target.getHealth();
                        // This cast's immutable per-pulse budget; canceled/reduced hooks remain honored.
                        target.setHealth(Math.min(target.getMaxHealth(),before+Math.min(heal.pulseAmount,amount)));
                        heal.recovered+=Math.max(0,target.getHealth()-before);
                    }
                }
            }
            if(now>=heal.expiresAt&&world.heals.get(targetId)==heal&&heal.valid()) {
                world.heals.remove(targetId,heal); // remove before derived rewards; no replay on reentry
                if(heal.recovered>0)addBuff((SkillUnit)heal.caster.entity(),target,"light_tide",
                        BuffKind.MOVEMENT_SPEED,heal.amplification.scale(TIDE_MOVEMENT_SPEED),heal.amplification.tideTicks());
            }
        } finally { heal.processing=false; }
    }
    public static float shieldRemaining(LivingEntity target) {
        Shield shield=validShield(target);return shield==null?0:shield.remaining;
    }
    public static int shieldTicks(LivingEntity target) {
        Shield shield=validShield(target);return shield==null?0:(int)Math.max(0,shield.expiresAt-target.level().getGameTime());
    }
    private static Shield validShield(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return null;
        Runtime world=WORLDS.get(level);if(world==null)return null;
        Shield shield=world.shields.get(target.getUUID());
        if(shield!=null && (!shield.valid() || shield.target.entity()!=target)) {
            world.shields.remove(target.getUUID());return null;
        }
        return shield;
    }
    /** Capture one hit without consuming anything; Forge callbacks may mutate/reenter before resolution. */
    public static FinalUnitDamageHooks.Finalizer prepareFinalDamage(LivingEntity target,DamageSource damage) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        if(!server(target))return null;
        if(AaroncosCombat.isStrike(damage))return amount->0;
        if(ShadowGolemStrike.isStrike(damage))return amount->0; // dedicated physical ticket only, never vanilla/unmanaged fallback
        if(AaroncosVortex.isPulse(damage))return AaroncosVortex.prepareFinal(target,damage);
        if(AshEmberLanding.isPulse(damage))return AshEmberLanding.prepareFinal(target,damage);
        if(FriendlyDebtContract.isSettlement(damage))return amount->0; // An unrecognized dedicated skill never becomes vanilla/base damage.
        Shield shield=validShield(target);
        FriendlyDebtContract.Receipt debt=FriendlyDebtContract.captureReceipt(target,damage);
        var reactive=DamageTriggeredDefense.capture(target,damage);
        ShadowHandGrip.Passive grip=ShadowHandGrip.capturePassive(target,damage);
        OrdinarySkillHit.Frame ordinary=OrdinarySkillHit.matching(target,damage);
        WindFangDamage.Impact fang=WindFangDamage.matching(target,damage);
        FinalUnitDamageHooks.Finalizer spore=com.dreamseekers.spore.SporeCombat.prepareFinal(target,damage);
        BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        SnailShellFormation.Plan formation=SnailShellFormation.matching(target,damage);
        ShadowCorrosionTouch.Receipt corrosion=ShadowCorrosionTouch.captureReceipt(target,damage);
        if(ordinary!=null||fang!=null||spore!=null||guard!=null||formation!=null||corrosion!=null||debt!=null||grip!=null||ward!=null||reactive!=null)return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;
            @Override public float resolve(float amount) {
                // Existing Wind/Spore authority and Bone/defensive arithmetic remain unchanged.
                if((fang!=null&&!fang.valid())||!Float.isFinite(amount)||amount<=0)return 0;
                float accepted=spore==null?amount:spore.resolve(amount);
                float enhanced=(float)(accepted*(ordinary==null?1:1+ordinary.vulnerability()));
                enhanced=defensiveDamage(guard,formation,ward,damage,enhanced);
                enhanced=ShadowHandGrip.applyPassive(grip,enhanced); // Ordinary final bonus, after defenses and before Gel.
                return shield==null?enhanced:finalDamage(target,damage,enhanced,shield,used->shieldLoss=(float)used);
            }
            @Override public void afterHealthDamage(float actualHealthLoss) {
                if(corrosion!=null)corrosion.applied(actualHealthLoss,shieldLoss);
                if(debt!=null)debt.applied(actualHealthLoss,shieldLoss);
                if(reactive!=null)reactive.applied(actualHealthLoss,shieldLoss);
            }
        };
        return shield==null?null:amount->finalDamage(target,damage,amount,shield);
    }
    /** Paid hand skills retain physical defenses/Gel but never obtain ordinary-mark/debt receipts. */
    static FinalUnitDamageHooks.Finalizer prepareAaroncosFinal(LivingEntity target,DamageSource damage,AaroncosCombat.Impact hit) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield shield=validShield(target);BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;
            private boolean accepted;
            @Override public float resolve(float amount) {
                if(!hit.legal()||!Float.isFinite(amount)||amount<=0)return 0;
                accepted=true;
                float guarded=defensiveDamage(guard,null,ward,damage,amount);
                float value=shield==null?guarded:finalDamage(target,damage,guarded,shield,used->shieldLoss=(float)used);
                return hit.legal()&&Float.isFinite(value)&&value>0?value:0;
            }
            @Override public void afterHealthDamage(float actualHealthLoss) {
                if(accepted)hit.applied(actualHealthLoss,shieldLoss);
            }
        };
    }
    /** Dedicated magic vortex: defenses and shield receipts, never ordinary attack bonuses. */
    static FinalUnitDamageHooks.Finalizer prepareVortexFinal(LivingEntity target,DamageSource damage,AaroncosVortex.Impact hit) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield shield=validShield(target);BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;private boolean accepted;
            @Override public float resolve(float amount) {
                if(!hit.legal()||!Float.isFinite(amount)||amount<=0)return 0;
                accepted=true;
                float guarded=defensiveDamage(guard,null,ward,damage,amount);
                float value=shield==null?guarded:finalDamage(target,damage,guarded,shield,used->shieldLoss=(float)used);
                return hit.legal()&&Float.isFinite(value)&&value>0?value:0;
            }
            @Override public void afterHealthDamage(float actualHealthLoss) {
                if(accepted&&hit.legal()&&((Float.isFinite(actualHealthLoss)&&actualHealthLoss>0)||shieldLoss>0))AaroncosVortex.applySlow(hit);
            }
        };
    }
    /** Own physical skill finalizer: no ordinary Bone/Debt/Corrosion/Grip receipts or primary projectile role. */
    static FinalUnitDamageHooks.Finalizer prepareGolemFinal(LivingEntity target,DamageSource damage,ShadowGolemStrike.Impact hit) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield shield=validShield(target);BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;
            @Override public float resolve(float amount) {
                if(!hit.legal()||!Float.isFinite(amount)||amount<=0)return 0;
                float guarded=defensiveDamage(guard,null,ward,damage,amount);
                float result=shield==null?guarded:finalDamage(target,damage,guarded,shield,used->shieldLoss=(float)used);
                hit.finalizedPositive=Float.isFinite(result)&&result>0&&hit.legal();
                return hit.finalizedPositive?result:0;
            }
            @Override public void afterHealthDamage(float actual) {
                // Core calls this once for this ticket's exact successful HP write, before native death.
                // A Forge callback's unrelated nested death cannot manufacture this receipt.
                hit.committedLethal=hit.finalizedPositive&&Float.isFinite(actual)&&actual>0
                        &&target.getHealth()<=0&&hit.target.identityValid();
                hit.applied(actual,shieldLoss); // Only the passive sweep uses the exact HP/Gel receipt.
            }
        };
    }
    /** Dedicated magic pulse: original native magic defenses already ran once; no Bone/ordinary role. */
    static FinalUnitDamageHooks.Finalizer prepareEmberFinal(LivingEntity target,DamageSource damage,AshEmberLanding.Impact hit) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield captured=validShield(target);
        BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        return amount->{
            if(!hit.legal()||!Float.isFinite(amount)||amount<=0)return 0;
            float guarded=defensiveDamage(guard,null,ward,damage,amount); // Non-primary area pulse never grants Snail formation.
            return captured==null?guarded:finalDamage(target,damage,guarded,captured);
        };
    }
    /** Dedicated non-primary contract settlement retains original physical/ranged defenses and this captured Gel. */
    static FinalUnitDamageHooks.Finalizer prepareDebtFinal(LivingEntity target,DamageSource damage,FriendlyDebtContract.Settlement hit) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield captured=validShield(target);
        BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        return amount->{
            if(!hit.legal()||!Float.isFinite(amount)||amount<=0)return 0;
            float guarded=defensiveDamage(guard,null,ward,damage,amount); // Never grants primary-only Snail formation.
            return captured==null?guarded:finalDamage(target,damage,guarded,captured);
        };
    }
    /** One local finalizer owns both this strike and its exact captured shield; no competing handlers. */
    static FinalUnitDamageHooks.Finalizer prepareDashFinal(LivingEntity target,DamageSource damage,WindDash.Impact impact) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield captured=validShield(target);
        BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        SnailShellFormation.Plan formation=SnailShellFormation.matching(target,damage);
        return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;
            private boolean accepted;
            @Override public float resolve(float amount) {
                if(!impact.validBeforeWrite()||!Float.isFinite(amount)||amount<=0)return 0;
                accepted=true;
                float guarded=defensiveDamage(guard,formation,ward,damage,amount);
                return captured==null?guarded:finalDamage(target,damage,guarded,captured,used->shieldLoss=(float)used);
            }
            @Override public void afterHealthDamage(float actualHealthLoss) {
                if(accepted)impact.applied(actualHealthLoss,shieldLoss);
            }
        };
    }
    /** Heavy uses a release-time base snapshot in beforeArmor, then the same original defenses/Gel.
     * Receipt records this exact write only; the caller dispatches secondary hits after hurt returns.
     */
    static FinalUnitDamageHooks.Finalizer prepareHeavyFinal(LivingEntity target,DamageSource damage,StoneHeavyShot.Impact impact) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield captured=validShield(target);
        BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        SnailShellFormation.Plan formation=SnailShellFormation.matching(target,damage);
        return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;
            private boolean accepted;
            @Override public float resolve(float amount) {
                if(!impact.legal()||!Float.isFinite(amount)||amount<=0)return 0;
                accepted=true;
                float guarded=defensiveDamage(guard,formation,ward,damage,amount);
                float result=captured==null?guarded:finalDamage(target,damage,guarded,captured,used->shieldLoss=(float)used);
                return impact.legal()?result:0; // Shield rebound can run callbacks before the HP write.
            }
            @Override public void afterHealthDamage(float actualHealthLoss) {
                if(accepted)impact.applied(actualHealthLoss,shieldLoss);
            }
        };
    }
    /** Exact suppression hit receipt; no ordinary Bone provenance or recursive burst. */
    static FinalUnitDamageHooks.Finalizer prepareSuppressionFinal(LivingEntity target,DamageSource damage,SquealSuppressionWave.Impact impact) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield captured=validShield(target);
        BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        SnailShellFormation.Plan formation=SnailShellFormation.matching(target,damage);
        return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;
            private boolean accepted;
            @Override public float resolve(float amount) {
                if(!impact.legal()||!Float.isFinite(amount)||amount<=0)return 0;
                accepted=true;
                float guarded=defensiveDamage(guard,formation,ward,damage,amount);
                return captured==null?guarded:finalDamage(target,damage,guarded,captured,used->shieldLoss=(float)used);
            }
            @Override public void afterHealthDamage(float actualHealthLoss) {
                if(accepted)impact.applied(actualHealthLoss,shieldLoss);
            }
        };
    }
    /** Beetle's optional first strike enhances final remaining damage, not pre-armor damage.
     * A command/reentry revokes only 30%; illegal identities/relations cannot damage a new ally.
     */
    static FinalUnitDamageHooks.Finalizer prepareBeetleFinal(LivingEntity target,DamageSource damage,BeetleAdvance.Impact impact) {
        var ward=com.dreamseekers.buildingability.DaisWardEffects.matching(target,damage);
        Shield captured=validShield(target);
        BeetleGuard.Plan guard=BeetleGuard.matching(target,damage);
        SnailShellFormation.Plan formation=SnailShellFormation.matching(target,damage);
        return new FinalUnitDamageHooks.Finalizer() {
            private float shieldLoss;
            private boolean enhanced;
            @Override public float resolve(float amount) {
                if(!impact.legal()||!Float.isFinite(amount)||amount<=0)return 0;
                enhanced=impact.enhanced();
                float accepted=enhanced?amount*BeetleAdvance.DAMAGE_MULTIPLIER:amount;
                accepted=defensiveDamage(guard,formation,ward,damage,accepted);
                return captured==null?accepted:finalDamage(target,damage,accepted,captured,used->shieldLoss=(float)used);
            }
            @Override public void afterHealthDamage(float actualHealthLoss) {
                impact.applied(enhanced,actualHealthLoss,shieldLoss);
            }
        };
    }
    /** Native Resistance has already reduced this hit, before vanilla absorption. Do not add another
     * 10/20 percent. Otherwise the two final-stage defenses take their maximum, before Gel.
     * This deliberately does not repair or undo RoN's pre-existing native-resistance arithmetic.
     */
    private static float defensiveDamage(BeetleGuard.Plan guard,SnailShellFormation.Plan formation,
                                         com.dreamseekers.buildingability.DaisWardEffects.HitPlan ward,
                                         DamageSource damage,float amount) {
        if((guard!=null&&guard.nativeResistance)||(formation!=null&&formation.nativeResistance))return amount;
        double wardReduction=ward==null?0:ward.reduction(damage);
        double reduction=Math.max(wardReduction,Math.max(guard!=null&&guard.valid(damage)?BeetleGuard.REDUCTION:0,
                SnailShellFormation.reduction(formation,damage)));
        // Preserve native arithmetic. Only a live captured ward may bridge a smaller already-used
        // native percentage to the category maximum; never multiply another 10% over Resistance.
        double nativeReduction=wardReduction>0?ward.nativeReduction(damage):0;
        if(nativeReduction>=reduction&&nativeReduction>0)return amount;
        double factor=nativeReduction>0&&nativeReduction<1?(1-reduction)/(1-nativeReduction):1-reduction;
        return (float)(amount*factor);
    }
    private static float finalDamage(LivingEntity target,DamageSource damage,float amount,Shield captured) {
        return finalDamage(target,damage,amount,captured,null);
    }
    private static float finalDamage(LivingEntity target,DamageSource damage,float amount,Shield captured,
                                     java.util.function.DoubleConsumer shieldReceipt) {
        // Cancellation, NaN and zero can never use shield health or manufacture a break proc.
        if(!Float.isFinite(amount) || amount<=0)return 0;
        Shield shield=validShield(target);
        // Already-managed hit still honors final event cancellation/amount after inner exhaustion,
        // removal or ownership change, but may not consume a replacement shield created in the event.
        if(shield!=captured)return amount;
        float consumed=Math.min(shield.remaining,amount);
        shield.remaining=Math.max(0,shield.remaining-consumed);
        boolean broken=shield.remaining<=0;
        if(broken)runtime((ServerLevel)target.level()).shields.remove(target.getUUID());
        if(shieldReceipt!=null)shieldReceipt.accept(consumed);
        // Commit consumption/removal BEFORE any derived callback/modifier work (reentry-safe).
        if(broken && enemyDamage(target,damage))
            addBuff((SkillUnit)shield.caster.entity(),target,"elastic_rebound",BuffKind.ATTACK_SPEED,
                    shield.amplification.scale(REBOUND_ATTACK_SPEED),REBOUND_TICKS);
        return Math.max(0,amount-consumed);
    }
    private static boolean enemyDamage(LivingEntity target,DamageSource damage) {
        Entity attacker=damage.getEntity();
        if(attacker==null || attacker==target || !attacker.isAlive() || attacker.isRemoved()
                || attacker.level()!=target.level() || !(target instanceof Unit protectedUnit))return false;
        String enemy=attacker instanceof Unit unit?unit.getOwnerName()
                :attacker instanceof ServerPlayer player?player.getGameProfile().getName():"";
        String owner=protectedUnit.getOwnerName();
        return !enemy.isBlank()&&!owner.isBlank()&&!owner.equals(enemy)&&!AlliancesServerEvents.isAllied(owner,enemy);
    }
    /** Trusted server effect API; not a wire action or resource-free user command. */
    public static boolean addBuff(SkillUnit source,LivingEntity target,String key,BuffKind kind,double amount,int ticks) {
        if(!server(target)||key==null||key.isBlank()||kind==null||!Double.isFinite(amount)||amount<=0||ticks<=0
                ||!friendly(source.mob(),target)||target.getAttribute(kind.attribute())==null)return false;
        Runtime world=runtime((ServerLevel)target.level());
        List<Buff> effects=world.buffs.computeIfAbsent(target.getUUID(),u->new ArrayList<>());
        effects.removeIf(b->!b.valid());
        for(Buff existing:effects)if(existing.caster.entity()==source.mob()&&existing.kind==kind&&existing.key.equals(key))return false;
        effects.add(new Buff(SkillIdentity.capture(source.mob()),SkillIdentity.capture(target),key,kind,
                Math.min(amount,SkillAmplification.BASELINE.cap(kind)),target.level().getGameTime()+Math.min(ticks,24000),false,null));
        apply(target,effects);return true;
    }
    /** The only ordinary 30% cap permission: a real tier-II Fox cast's own immutable profile.
     * Generic addBuff/tryAddNamedBuff, even using this key or amount, retain the normal 25% cap.
     * Global named occupancy remains atomic across casters; an existing blessing is not refreshed.
     */
    static boolean tryAddFoxBlessing(SkillUnit source,LivingEntity target,SkillAmplification.Profile amplification) {
        if(amplification==null || !amplification.matches(source,UnitSkill.FOX_BLESSING)
                || !SkillService.supports(source,UnitSkill.FOX_BLESSING) || !server(target)
                || !friendly(source.mob(),target) || target.getAttribute(BuffKind.ATTACK_SPEED.attribute())==null
                || hasNamedBuff(target,FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED)) return false;
        var caster=SkillIdentity.capture(source.mob());var recipient=SkillIdentity.capture(target);
        if(!caster.valid() || !recipient.valid()) return false;
        var effects=runtime((ServerLevel)target.level()).buffs.computeIfAbsent(target.getUUID(),u->new ArrayList<>());
        effects.removeIf(b->!b.valid());
        effects.add(new Buff(caster,recipient,FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED,
                amplification.scale(FOX_ATTACK_SPEED),target.level().getGameTime()+amplification.durationTicks(),
                amplification.lateFox(source),null));
        apply(target,effects);return true;
    }
    /** Fixed, trusted building pulse only. No packet accepts any of these effect parameters.
     * The self stamp authenticates only this target; the source building/account/network is guarded
     * separately on every contribution read. Existing max/category modifier semantics are shared.
     */
    public static boolean addGuardedBuildingBuff(LivingEntity target,String key,BuffKind kind,double amount,int ticks,
            java.util.function.BooleanSupplier guard) {
        if(!server(target)||!(target instanceof Unit)||!"resonance_pulse".equals(key)||kind!=BuffKind.ATTACK_SPEED
                ||!Double.isFinite(amount)||amount<=0||amount>.15||ticks<=0||ticks>160||guard==null
                ||target.getAttribute(kind.attribute())==null||!guard.getAsBoolean()||hasNamedBuff(target,key,kind))return false;
        var identity=SkillIdentity.capture(target);if(!identity.valid())return false;
        var effects=runtime((ServerLevel)target.level()).buffs.computeIfAbsent(target.getUUID(),u->new ArrayList<>());
        effects.removeIf(b->!b.valid());
        effects.add(new Buff(identity,identity,key,kind,amount,target.level().getGameTime()+ticks,false,guard));
        apply(target,effects);return true;
    }
    /** Fixed building capability; its private constructor is owned by the paid building service.
     * Does not widen resonance_pulse's legacy API or grant Fox/Firefly cap exceptions.
     */
    public static boolean addBuildingAbilityBuff(com.dreamseekers.buildingability.BuildingAbilityEffects.BuffLease lease) {
        if(lease==null||!server(lease.target())||!lease.valid())return false;
        LivingEntity target=lease.target();BuffKind kind=lease.kind();
        if(target.getAttribute(kind.attribute())==null||hasNamedBuff(target,lease.key(),kind))return false;
        var identity=SkillIdentity.capture(target);if(!identity.valid()||!lease.valid())return false;
        var effects=runtime((ServerLevel)target.level()).buffs.computeIfAbsent(target.getUUID(),u->new ArrayList<>());
        effects.removeIf(b->!b.valid());
        effects.add(new Buff(identity,identity,lease.key(),kind,Math.min(kind.cap,lease.amount()),lease.expiresAt(),false,lease::valid));
        apply(target,effects);return true;
    }
    /** Include invalid-but-not-yet-ticked records, so direct lease retirement cannot leave a stale
     * modifier for one more native attribute read. This query invokes no lease/spatial callbacks.
     */
    public static boolean hasBuildingAbilityBuffRecord(LivingEntity target) {
        if(!server(target))return false;
        var world=WORLDS.get((ServerLevel)target.level());if(world==null)return false;
        return world.buffs.getOrDefault(target.getUUID(),List.of()).stream().anyMatch(b->b.target.entity()==target
                &&(com.dreamseekers.buildingability.BuildingAbilityEffects.ROOT_KEY.equals(b.key)
                   ||com.dreamseekers.buildingability.BuildingAbilityEffects.HALL_KEY.equals(b.key)));
    }
    /** Revalidate fixed guarded building stats before movement/attack attribute consumption. */
    public static void refreshBuildingAbilityStats(LivingEntity target) { refresh(target); }
    /** Fixed mother aura only. A fresh, fully validated discovery may renew its exact still-live
     * source/target contribution before expiry. Another mother may not steal that occupied name;
     * once the old source is invalid, normal discovery may install a new legitimate source.
     * This exception never changes named skill buffs, their deadlines, category maximum or caps.
     */
    static boolean addMotherCommandBuff(BeetleMotherCommandAura.Lease lease) {
        if(lease==null||!server(lease.target.entity())||!lease.valid())return false;
        LivingEntity target=lease.target.entity();BuffKind kind=BuffKind.ATTACK_SPEED;
        if(target.getAttribute(kind.attribute())==null)return false;
        var effects=runtime((ServerLevel)target.level()).buffs.computeIfAbsent(target.getUUID(),u->new ArrayList<>());
        var occupied=effects.stream().filter(b->b.target.entity()==target&&b.kind==kind
                &&b.key.equals(BeetleMotherCommandAura.KEY)&&b.valid()).toList();
        if(!occupied.isEmpty()) {
            // Recheck the ORIGINAL live guard before authorizing renewal, not merely its key/name.
            // Stamp equality includes exact objects, world, UUID and both ownership generations.
            if(occupied.size()!=1||!occupied.get(0).caster.equals(lease.caster)
                    ||!occupied.get(0).target.equals(lease.target)||!occupied.get(0).valid())return false;
        }
        if(!lease.valid())return false;
        Buff previous=occupied.isEmpty()?null:occupied.get(0);
        effects.removeIf(b->!b.valid()||b==previous);
        effects.add(new Buff(lease.caster,lease.target,BeetleMotherCommandAura.KEY,kind,
                BeetleMotherCommandAura.ATTACK_SPEED,lease.expiresAt,false,lease::valid));
        apply(target,effects);return true;
    }
    static boolean addHandHarmonyBuff(AaroncosHarmony.Lease lease) {
        if(lease==null||!server(lease.target.entity())||!lease.valid())return false;
        LivingEntity target=lease.target.entity();BuffKind kind=BuffKind.ATTACK_SPEED;
        if(target.getAttribute(kind.attribute())==null)return false;
        var effects=runtime((ServerLevel)target.level()).buffs.computeIfAbsent(target.getUUID(),u->new ArrayList<>());
        // Any currently legal opposite hand can renew this sole contribution; never two copies.
        effects.removeIf(b->!b.valid()||(b.target.entity()==target&&b.key.equals(AaroncosHarmony.KEY)));
        if(!lease.valid())return false;
        effects.add(new Buff(lease.caster,lease.target,AaroncosHarmony.KEY,kind,AaroncosHarmony.ATTACK_SPEED,lease.expiresAt,false,lease::valid));
        apply(target,effects);return true;
    }
    private static final java.util.Set<LivingEntity> REFRESHING_BUILDING_BUFF=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Called immediately before an ordinary APS attribute read. Invalid-but-not-yet-ticked records
     * intentionally count: an expired Fox/rebound or severed network must not leave a stale attribute
     * for this tick while cached-goal consumers already observe the live (zero/fallback) bonus.
     * No managed APS contribution means exactly no work/change to the core-only cadence.
     */
    public static void refreshGuardedAttackRate(LivingEntity target) {
        if(!server(target)||REFRESHING_BUILDING_BUFF.contains(target))return;
        var world=WORLDS.get((ServerLevel)target.level());if(world==null)return;
        var effects=world.buffs.getOrDefault(target.getUUID(),List.of());
        if(effects.stream().noneMatch(b->b.target.entity()==target&&b.kind==BuffKind.ATTACK_SPEED))return;
        REFRESHING_BUILDING_BUFF.add(target);
        try{apply(target,effects);}finally{REFRESHING_BUILDING_BUFF.remove(target);}
    }
    /** Global named-effect occupancy: other sources may not refresh or steal this exact effect. */
    public static boolean hasNamedBuff(LivingEntity target,String key,BuffKind kind) {
        if(!server(target)||key==null||kind==null)return false;
        Runtime world=WORLDS.get((ServerLevel)target.level());if(world==null)return false;
        return world.buffs.getOrDefault(target.getUUID(),List.of()).stream()
                .anyMatch(b->b.target.entity()==target&&b.kind==kind&&b.key.equals(key)&&b.valid());
    }
    /** Trusted server effect transaction; weaker contributions may remain as a nonstacking fallback. */
    public static boolean tryAddNamedBuff(SkillUnit source,LivingEntity target,String key,BuffKind kind,double amount,int ticks) {
        if(!server(target)||hasNamedBuff(target,key,kind))return false;
        return addBuff(source,target,key,kind,amount,ticks);
    }
    /** Opt-in bridge for legacy cached cadence fields. Never derive from the already-buffed attribute.
     * The new managed slow participates only while live; without it the original APS adapter is exact.
     * One reset-time rounding; no cached-field, remaining-CD, charge/windup or jump-animation mutation.
     */
    public static int fixedAttackCooldown(LivingEntity target,int legacy) {
        if(!server(target)||legacy<=0)return legacy;
        double amount=bonus(target,BuffKind.ATTACK_SPEED);
        float slow=ManagedAttackSlowdown.cachedFactor(target);
        if(slow>1)return Math.max(1,(int)Math.floor(legacy*(double)slow/(Double.isFinite(amount)&&amount>0?1+amount:1)));
        return Double.isFinite(amount)&&amount>0?Math.max(1,(int)Math.floor(legacy/(1+amount))):legacy;
    }
    /** Cached float baselines keep precision; the original goal rounds once. Not a multiplier-inclusive return value. */
    public static float fixedAttackCooldown(LivingEntity target,float legacy) {
        if(!server(target)||!Float.isFinite(legacy)||legacy<=0)return legacy;
        double amount=bonus(target,BuffKind.ATTACK_SPEED);
        float slow=ManagedAttackSlowdown.cachedFactor(target);
        if(slow>1)return Math.max(1f,(float)(legacy*(double)slow/(Double.isFinite(amount)&&amount>0?1+amount:1)));
        return Double.isFinite(amount)&&amount>0?Math.max(1f,(float)(legacy/(1+amount))):legacy;
    }
    /** Militia's return value ALREADY includes getAttackCooldownMultiplier (native/managed slow,
     * Bloodlust and DISARM ordering). Only its constant-based APS bypass needs adaptation here.
     * Feeding it into either cached-baseline overload would apply the same slow twice.
     */
    public static float militiaAttackCooldown(LivingEntity target,float legacy) {
        if(!(target instanceof com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit)
                ||!server(target)||!Float.isFinite(legacy)||legacy<=0)return legacy;
        double amount=bonus(target,BuffKind.ATTACK_SPEED);
        return Double.isFinite(amount)&&amount>0?Math.max(1f,(float)(legacy/(1+amount))):legacy;
    }
    /** Captures exact live contributions, not merely a reusable name or amount. An event may not
     * substitute a new caster/effect with the same name after the damage calculation has started.
     */
    record BuffLease(double amount,java.util.function.BooleanSupplier predicate) {
        boolean valid() { return predicate.getAsBoolean(); }
    }
    static BuffLease captureNamedBonus(LivingEntity target,String key,BuffKind kind) {
        if(!server(target))return null;
        ServerLevel level=(ServerLevel)target.level();Runtime world=WORLDS.get(level);if(world==null)return null;
        var effects=world.buffs.getOrDefault(target.getUUID(),List.of()).stream()
                .filter(b->b.kind==kind&&b.target.entity()==target&&b.valid()).toList();
        var named=effects.stream().filter(b->b.key.equals(key)).toList();
        double amount=effects.stream().mapToDouble(Buff::amount).max().orElse(0);
        if(named.isEmpty()||amount<=0)return null;
        var strongest=effects.stream().filter(b->b.amount==amount).toList();
        return new BuffLease(amount,()->{
            Runtime current=WORLDS.get(level);if(current==null)return false;
            var live=current.buffs.getOrDefault(target.getUUID(),List.of());
            return named.stream().anyMatch(b->b.valid()&&live.stream().anyMatch(candidate->candidate==b))
                    &&strongest.stream().anyMatch(b->b.valid()&&live.stream().anyMatch(candidate->candidate==b));
        });
    }
    public static double bonus(LivingEntity target,BuffKind kind) {
        if(!server(target))return 0;
        Runtime world=WORLDS.get((ServerLevel)target.level());
        return categoryAmount(target,kind,world==null?List.of():world.buffs.getOrDefault(target.getUUID(),List.of()));
    }
    private static double categoryAmount(LivingEntity target,BuffKind kind,List<Buff> effects) {
        double ordinary=effects.stream().filter(b->b.kind==kind&&b.target.entity()==target&&b.valid())
                .mapToDouble(b->Math.min(b.amount,b.permittedCap())).max().orElse(0);
        // Existing dedicated skill exceptions retain their own guarded semantics; no general cap increase.
        if(kind==BuffKind.ATTACK_SPEED)return Math.max(ordinary,FriendlyDebtContract.pursuitBonus(target));
        return kind==BuffKind.MOVEMENT_SPEED?Math.max(ordinary,Math.max(FireflyRush.movementBonus(target),TerrorbeakDreadRoar.predatoryBonus(target))):ordinary;
    }
    static void refresh(LivingEntity target) {
        if(!server(target))return;
        Runtime world=WORLDS.get((ServerLevel)target.level());
        apply(target,world==null?List.of():world.buffs.getOrDefault(target.getUUID(),List.of()));
    }
    private static void apply(LivingEntity target,List<Buff> effects) {
        for(BuffKind kind:BuffKind.values()) {
            double amount=categoryAmount(target,kind,effects);
            double permittedCap=kind==BuffKind.MOVEMENT_SPEED?Math.max(kind.cap,FireflyRush.movementBonus(target)):kind.cap;
            if(kind==BuffKind.ATTACK_SPEED && effects.stream().anyMatch(b->b.kind==kind&&b.lateFox
                    && b.target.entity()==target&&b.valid())) permittedCap=Math.max(permittedCap,.30);
            applyAttribute(target.getAttribute(kind.attribute()),kind,amount,permittedCap);
            // FlyingMoveControl consumes FLYING_SPEED, while ground movement/UI consume MOVEMENT_SPEED.
            // Both receive the same single transient category multiplier, never a multiplied base value.
            if(kind==BuffKind.MOVEMENT_SPEED)applyAttribute(target.getAttribute(Attributes.FLYING_SPEED),kind,amount,permittedCap);
        }
    }
    private static void applyAttribute(AttributeInstance attribute,BuffKind kind,double amount,double permittedCap) {
        if(attribute==null)return;
        AttributeModifier old=attribute.getModifier(kind.modifierId);
        if(old!=null&&Math.abs(old.getAmount()-amount)<1e-9&&amount>0)return;
        if(old!=null)attribute.removeModifier(kind.modifierId);
        if(amount>0)attribute.addTransientModifier(new AttributeModifier(kind.modifierId,
                "dreamseekers_temporary_"+kind.name(),Math.min(amount,permittedCap),AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static void removeModifiers(LivingEntity target) {
        com.dreamseekers.buildingability.BuildingAbilityEffects.clear(target);
        ShadowHandGrip.clearEffects(target);
        FriendlyDebtContract.clear(target);
        TerrorbeakDreadRoar.clearEffects(target);
        SquealSuppressionWave.clear(target);
        AaroncosShockSuppression.clear(target);
        AaroncosVortex.removeModifier(target);
        ShadowCorrosionTouch.removeModifier(target);
        for(BuffKind kind:BuffKind.values()) {
            AttributeInstance attribute=target.getAttribute(kind.attribute());
            if(attribute!=null)attribute.removeModifier(kind.modifierId);
        }
        AttributeInstance flying=target.getAttribute(Attributes.FLYING_SPEED);
        if(flying!=null)flying.removeModifier(BuffKind.MOVEMENT_SPEED.modifierId);
    }
    public static void tick(ServerLevel level) {
        AaroncosVortex.tick(level);AaroncosCombat.tick(level);ShadowGolemStrike.tick(level);AshEmberLanding.tick(level);ShadowHandGrip.tick(level);FriendlyDebtContract.tick(level);TerrorbeakDreadRoar.tick(level);SquealSuppressionWave.tick(level);AaroncosShockSuppression.tick(level);MotherNestStance.tick(level);AaroncosHarmony.tick(level);BeetleMotherCommandAura.tick(level);ShadowCorrosionTouch.tick(level);StoneHeavyShot.tick(level);SnailShellFormation.tick(level);BeetleAdvance.tick(level);FireflyRush.tick(level);WindDash.tick(level);CoordinatedMark.tick(level);com.dreamseekers.spore.SporeLease.tick(level);tickEffects(level);
    }
    private static void tickEffects(ServerLevel level) {
        Runtime world=WORLDS.get(level);if(world==null)return;
        world.shields.values().removeIf(s->!s.valid());
        for(Heal heal:List.copyOf(world.heals.values()))tickHeal(world,heal,level.getGameTime());
        Iterator<Map.Entry<UUID,List<Buff>>> iterator=world.buffs.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();List<Buff> buffs=entry.getValue();
            LivingEntity target=buffs.isEmpty()?null:buffs.get(0).target.entity();
            buffs.removeIf(b->!b.valid());
            if(target!=null)apply(target,buffs);
            if(buffs.isEmpty())iterator.remove();
        }
        if(world.shields.isEmpty()&&world.buffs.isEmpty()&&world.heals.isEmpty())WORLDS.remove(level,world);
    }
    /** Removes both outgoing and incoming effects without invoking any completion/break reward. */
    public static void clear(LivingEntity entity) { clear(entity,false,false); }
    /** Successful cleanse throttling is not an owned buff; only this explicit transfer reason preserves it. */
    public static void clearForOwnerChange(LivingEntity entity) { clear(entity,true,false); }
    /** Authority and effects end immediately; physical carrier cleanup cannot reenter a live section iterator. */
    public static void clearForLeave(LivingEntity entity) { clear(entity,false,true); }
    public static void clearForDeath(LivingEntity entity,DamageSource source) { clear(entity,false,false,source); }
    private static void clear(LivingEntity entity,boolean ownerChanged,boolean leavingLevel) { clear(entity,ownerChanged,leavingLevel,null); }
    private static void clear(LivingEntity entity,boolean ownerChanged,boolean leavingLevel,DamageSource deathSource) {
        if(!(entity.level() instanceof ServerLevel level))return;
        com.dreamseekers.buildingability.BuildingAbilityEffects.clear(entity);
        AaroncosVortex.clear(entity);AaroncosCombat.clear(entity);ShadowGolemStrike.clear(entity,deathSource);AshEmberLanding.clear(entity);ShadowHandGrip.clear(entity,ownerChanged);FriendlyDebtContract.clear(entity);TerrorbeakDreadRoar.clear(entity,ownerChanged);SquealSuppressionWave.clear(entity);AaroncosShockSuppression.clear(entity);MotherNestStance.clear(entity);BeetleMotherCommandAura.clear(entity);ShadowCorrosionTouch.clear(entity);StoneHeavyShot.clear(entity);SnailShellFormation.clear(entity);BeetleAdvance.clear(entity);FireflyRush.clear(entity);WindDash.clear(entity);WindFangDamage.clear(entity);CoordinatedMark.clear(entity);
        if(leavingLevel)com.dreamseekers.spore.SporeLease.leftLevel(entity);
        else if(ownerChanged)com.dreamseekers.spore.SporeLease.ownerChanged(entity);
        else com.dreamseekers.spore.SporeLease.clear(entity);
        Runtime world=WORLDS.get(level);
        if(world!=null) {
            world.shields.values().removeIf(s->s.caster.entity()==entity||s.target.entity()==entity);
            world.heals.values().removeIf(h->h.caster.entity()==entity||h.target.entity()==entity);
            Iterator<List<Buff>> iterator=world.buffs.values().iterator();
            while(iterator.hasNext()) {
                List<Buff> buffs=iterator.next();LivingEntity target=buffs.isEmpty()?null:buffs.get(0).target.entity();
                buffs.removeIf(b->b.caster.entity()==entity||b.target.entity()==entity);
                if(target!=null)apply(target,buffs);
                if(buffs.isEmpty())iterator.remove();
            }
            if(world.shields.isEmpty()&&world.buffs.isEmpty()&&world.heals.isEmpty())WORLDS.remove(level,world);
        }
        removeModifiers(entity);
    }
    public static void relationshipsChanged() {
        ShadowHandGrip.relationshipsChanged();FriendlyDebtContract.relationshipsChanged();TerrorbeakDreadRoar.relationshipsChanged();SquealSuppressionWave.relationshipsChanged();AaroncosShockSuppression.relationshipsChanged();ShadowCorrosionTouch.relationshipsChanged();WindDash.relationshipsChanged();WindFangDamage.relationshipsChanged();CoordinatedMark.relationshipsChanged();
        // Snapshot keys because tick retires empty runtimes. There is no effect work for unrelated units.
        for(ServerLevel level:List.copyOf(WORLDS.keySet()))if(level.getServer().isSameThread())tickEffects(level);
    }
    public static void stop() {
        com.dreamseekers.buildingability.BuildingAbilityEffects.stop();
        AaroncosVortex.stop();AaroncosCombat.stop();ShadowGolemStrike.stop();AshEmberLanding.stop();ShadowHandGrip.stop();FriendlyDebtContract.stop();TerrorbeakDreadRoar.stop();SquealSuppressionWave.stop();AaroncosShockSuppression.stop();MotherNestStance.stop();BeetleMotherCommandAura.stop();ShadowCorrosionTouch.stop();StoneHeavyShot.stop();SnailShellFormation.stop();BeetleAdvance.stop();BeetleGuard.stop();FireflyRush.stop();com.dreamseekers.spore.SporeLease.stop();WindDash.stop();WindFangDamage.stop();CoordinatedMark.stop();OrdinarySkillHit.stop();OrdinaryFangOrigin.stop();
        for(Runtime world:WORLDS.values())for(List<Buff> buffs:world.buffs.values())
            if(!buffs.isEmpty())removeModifiers(buffs.get(0).target.entity());
        WORLDS.clear();
    }
}
