package com.solegendary.reignofnether.resources.kill;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Common-side addon veto for free/duplicated/temporary economic bodies.
 * Fired at death time after core filters. Cancellation only denies economic reward;
 * it never cancels the death or alters combat. It cannot increase the cost snapshot. */
@Cancelable
public final class RtsKillValueEligibilityEvent extends Event {
    private final LivingEntity victim;
    private final LivingEntity killer;
    public RtsKillValueEligibilityEvent(LivingEntity victim, LivingEntity killer) {
        this.victim = victim; this.killer = killer;
    }
    public LivingEntity victim() { return victim; }
    public LivingEntity killer() { return killer; }
}
