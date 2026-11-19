// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import net.minecraft.world.entity.LivingEntity;

/** One ordinary-cadence category: strongest trusted interval factor, never multiplication. */
public final class ManagedAttackSlowdown {
    private ManagedAttackSlowdown() { }
    private static float managed(LivingEntity target) {
        return Math.max(SquealSuppressionWave.hasDebuff(target)?SquealSuppressionWave.ATTACK_INTERVAL_FACTOR:1,
                AaroncosShockSuppression.factor(target));
    }
    public static float factor(LivingEntity target,float nativeFactor) {
        float managed=managed(target);
        // No live managed state is an exact identity, including unusual native values.
        return managed>1&&Float.isFinite(nativeFactor)&&nativeFactor>0?Math.max(nativeFactor,managed):nativeFactor;
    }
    public static float cachedFactor(LivingEntity target) {
        float managed=managed(target);if(managed<=1)return 1;
        var nativeSlow=target.getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());
        float nativeFactor=nativeSlow==null?1:1+(nativeSlow.getAmplifier()+1)*.05f;
        return Math.max(managed,nativeFactor);
    }
}
