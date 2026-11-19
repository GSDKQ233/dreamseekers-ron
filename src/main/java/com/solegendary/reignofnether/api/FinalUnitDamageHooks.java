// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.api;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Default-empty integration at the final-health-damage boundary. Never links addon classes.
 * Capture opt-in BEFORE Forge dispatch: callbacks may reenter hurt, consume/remove an effect,
 * or change ownership. That must not restore ignored legacy damage for an already managed hit.
 */
public final class FinalUnitDamageHooks {
    private FinalUnitDamageHooks() { }
    @FunctionalInterface public interface Finalizer {
        float resolve(float finalEventAmount);
        /** Called once immediately after this hit's own health write (or zero for full absorption). */
        default void afterHealthDamage(float actualHealthLoss) { }
    }
    public interface OrdinaryAttack {
        /** Ordinary weapon-inclusive damage, before physical armor. Ticket calls this at most once. */
        float beforeArmor(float ordinaryDamage);
        /** Identity check after LivingHurt callbacks and before magic/vanilla absorption is consumed. */
        default float validateAfterHurt(float damage) { return damage; }
        /** Captured at the final boundary, before Forge's final-damage callback. Never return null. */
        Finalizer prepareFinal();
    }
    @FunctionalInterface public interface ResistanceObserver { void used(double reduction); }
    @FunctionalInterface public interface Handler {
        /** Null means this hit is not managed; no effects are consumed during capture. */
        Finalizer prepare(LivingEntity victim,DamageSource source);
        /** Optional per-hit plan. Empty integrations leave every legacy calculation unchanged. */
        default OrdinaryAttack prepareOrdinaryAttack(LivingEntity victim,DamageSource source) { return null; }
        /** Capture the exact existing hit token; observations never manufacture a new damage plan. */
        default ResistanceObserver captureResistanceObserver(LivingEntity victim,DamageSource source) { return null; }
        /** Last-boundary authorization; default true preserves every native damage calculation. */
        default boolean permitsFinalDamage(LivingEntity victim,DamageSource source) { return true; }
    }
    public static final class Ticket {
        private final Finalizer finalizer;
        private final LivingEntity victim;
        private final DamageSource source;
        private boolean resolved, reported;
        private float result;
        private Ticket(Finalizer finalizer,LivingEntity victim,DamageSource source) {
            this.finalizer=finalizer;this.victim=victim;this.source=source;
        }
        private boolean permitted() {
            if(victim==null||victim.level().isClientSide())return true;
            for(Handler handler:HANDLERS)if(!handler.permitsFinalDamage(victim,source))return false;
            return true;
        }
        public float resolve(float legacyAmount,float finalEventAmount) {
            if(resolved)return result;
            resolved=true; // commit before callbacks; a reentrant duplicate cannot consume twice
            // Do not consume an effect for a source whose lease was revoked during Forge callbacks.
            if(!permitted()){result=0;return 0;}
            if(finalizer==null){result=Float.isFinite(finalEventAmount)?Math.max(0,finalEventAmount):0;return result;}
            float value=finalizer.resolve(finalEventAmount);
            result=Float.isFinite(value)?Math.max(0,value):0;
            // Resolving an effect may itself invoke code which revokes the source.
            if(!permitted())result=0;
            return result;
        }
        public void appliedHealthDamage(float actualHealthLoss) {
            if(finalizer==null||!resolved||reported)return;
            reported=true; // completion may reenter; never report or reward the same hit twice
            float actual=Float.isFinite(actualHealthLoss)?Math.max(0,Math.min(result,actualHealthLoss)):0;
            finalizer.afterHealthDamage(actual);
        }
    }
    public static final class OrdinaryTicket {
        private final LivingEntity victim;
        private final DamageSource source;
        private final OrdinaryAttack attack;
        private boolean adjusted;
        private float damage;
        private Ticket finalTicket;
        private Finalizer preparedFinalizer=amount->0;
        private OrdinaryTicket(LivingEntity victim,DamageSource source,OrdinaryAttack attack) {
            this.victim=victim;this.source=source;this.attack=attack;
        }
        public float beforeArmor(float legacyDamage) {
            if(attack==null)return legacyDamage;
            if(adjusted)return damage;
            adjusted=true;
            float value=attack.beforeArmor(legacyDamage);
            damage=Float.isFinite(value)?Math.max(0,value):0;
            return damage;
        }
        public float validateAfterHurt(float legacyDamage) {
            if(attack==null)return legacyDamage;
            float value=attack.validateAfterHurt(legacyDamage);
            return Float.isFinite(value)?Math.max(0,value):0;
        }
        public Ticket prepareFinal() {
            if(finalTicket!=null)return finalTicket;
            if(attack==null)return finalTicket=begin(victim,source);
            // Publish the same fail-closed ticket BEFORE asking the provider to capture its final state.
            finalTicket=new Ticket(new Finalizer() {
                @Override public float resolve(float amount) { return preparedFinalizer.resolve(amount); }
                @Override public void afterHealthDamage(float actual) { preparedFinalizer.afterHealthDamage(actual); }
            },victim,source);
            Finalizer finalizer=attack.prepareFinal();
            // An opted-in hit cannot silently fall back to legacy semantics if its plan becomes invalid.
            if(finalizer!=null)preparedFinalizer=finalizer;
            return finalTicket;
        }
    }
    private static final class MagicFrame {
        final LivingEntity victim;
        final DamageSource source;
        final List<ResistanceObserver> observers;
        boolean sampled;
        MagicFrame(LivingEntity victim,DamageSource source,List<ResistanceObserver> observers) {
            this.victim=victim;this.source=source;this.observers=observers;
        }
    }
    private static final ArrayDeque<MagicFrame> MAGIC=new ArrayDeque<>();
    private static List<ResistanceObserver> resistanceObservers(LivingEntity victim,DamageSource source) {
        List<ResistanceObserver> observers=new ArrayList<>();
        if(!victim.level().isClientSide())for(Handler handler:HANDLERS) {
            ResistanceObserver observer=handler.captureResistanceObserver(victim,source);
            if(observer!=null)observers.add(observer);
        }
        return observers;
    }
    /** Read-only notification of the resistance value used by RoN's existing first multiplier. */
    public static void nativeResistanceUsed(LivingEntity victim,DamageSource source,double reduction) {
        for(ResistanceObserver observer:resistanceObservers(victim,source))observer.used(reduction);
    }
    /** Scope only the actual damage calculation, not public diagnostic calls from Forge callbacks.
     * Exact hit observers are captured before the call; nested calls cannot redirect their receipt.
     */
    public static float magicAbsorb(LivingEntity victim,DamageSource source,float amount) {
        if(victim.level().isClientSide())return victim.getDamageAfterMagicAbsorb(source,amount);
        MagicFrame frame=new MagicFrame(victim,source,resistanceObservers(victim,source));MAGIC.push(frame);
        try { return victim.getDamageAfterMagicAbsorb(source,amount); }
        finally { MAGIC.removeFirstOccurrence(frame); }
    }
    /** Called only at the original resistance branch's amplifier read; returns no replacement value. */
    public static void observeMagicResistance(LivingEntity victim,DamageSource source,double reduction) {
        MagicFrame frame=MAGIC.peek();
        if(frame==null||frame.sampled||frame.victim!=victim||frame.source!=source)return;
        frame.sampled=true;
        for(ResistanceObserver observer:frame.observers)observer.used(reduction);
    }
    private static final Ticket PASS=new Ticket(null,null,null);
    private static final List<Handler> HANDLERS=new ArrayList<>();
    /** Register once at mod construction; invocation/mutation is on the server thread. */
    public static synchronized void register(Handler handler) {
        if(handler==null||HANDLERS.contains(handler))throw new IllegalArgumentException("Duplicate/null final damage handler");
        HANDLERS.add(handler);
    }
    public static OrdinaryTicket beginOrdinaryAttack(LivingEntity victim,DamageSource source) {
        if(!victim.level().isClientSide())for(Handler handler:HANDLERS) {
            OrdinaryAttack attack=handler.prepareOrdinaryAttack(victim,source);
            if(attack!=null)return new OrdinaryTicket(victim,source,attack);
        }
        return new OrdinaryTicket(victim,source,null);
    }
    public static Ticket begin(LivingEntity victim,DamageSource source) {
        if(victim.level().isClientSide())return PASS;
        for(Handler handler:HANDLERS) {
            Finalizer finalizer=handler.prepare(victim,source);
            if(finalizer!=null)return new Ticket(finalizer,victim,source);
        }
        return new Ticket(null,victim,source);
    }
}
