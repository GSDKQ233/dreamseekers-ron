// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.api;

import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/** Opt-in read-only receipt at a real health write; no addon link, registry or damage replacement. */
public final class HealthWriteObservers {
    private HealthWriteObservers() { }
    private static final Logger LOGGER=LogUtils.getLogger();
    @FunctionalInterface public interface Observer { void applied(float actualHealthLoss); }
    /** Implemented only by entities that explicitly need exact-write observations. */
    public interface Observed {
        /** Capture the currently active hurt frame, not a later reusable DamageSource lookup. */
        Observer captureHealthWrite(DamageSource source);
    }
    public static final class Receipt {
        private Observer observer;
        private Receipt(Observer observer) { this.observer=observer; }
        public void applied(float actual) {
            Observer current=observer;observer=null; // consume before any callback/reentry
            if(current==null)return;
            try { current.applied(Float.isFinite(actual)?Math.max(0,actual):0); }
            catch(RuntimeException failure) { LOGGER.error("Exact health-write observer failed after original write",failure); }
        }
    }
    public static Receipt capture(LivingEntity victim,DamageSource source) {
        Observer observer=null;
        if(!victim.level().isClientSide()&&victim instanceof Observed observed) {
            try { observer=observed.captureHealthWrite(source); }
            catch(RuntimeException failure) { LOGGER.error("Exact health-write observer capture failed",failure); }
        }
        return new Receipt(observer);
    }
    /** Wrap ONLY the original setHealth invocation, inside any existing completion callback wrapper. */
    public static void write(LivingEntity victim,DamageSource source,float requestedHealth,Runnable original) {
        Receipt receipt=capture(victim,source);
        float before=victim.getHealth();
        original.run(); // exceptions from original game code retain their original semantics
        float after=victim.getHealth();
        float expected=Float.isFinite(before)&&Float.isFinite(requestedHealth)
                ?Math.max(0,Math.max(0,before)-Math.max(0,requestedHealth)):0;
        float actual=Float.isFinite(before)&&Float.isFinite(after)
                ?Math.min(expected,Math.max(0,Math.max(0,before)-Math.max(0,after))):0;
        receipt.applied(actual);
    }
}
