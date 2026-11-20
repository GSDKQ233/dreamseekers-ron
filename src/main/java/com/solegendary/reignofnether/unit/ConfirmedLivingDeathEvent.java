package com.solegendary.reignofnether.unit;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * Server-side, non-cancelable notification from the native final-death commit. All listeners
 * to Forge's cancelable death event have returned. Not a payment, kill attribution or loot hook.
 * Carry the original source reference for effects that need to distinguish their own lethal hit.
 */
public final class ConfirmedLivingDeathEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;
    ConfirmedLivingDeathEvent(LivingEntity entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
    }
    public LivingEntity getEntity() { return entity; }
    public DamageSource getSource() { return source; }
}
