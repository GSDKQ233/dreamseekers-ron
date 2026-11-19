// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.spore.SporeCombat;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Recheck only this authorized non-Unit before RoN's existing damage-triggered benefits. */
@Mixin(value=UnitServerEvents.class,remap=false)
public abstract class SporeOnHitGuardMixin {
    @Inject(method="onEntityDamaged",at=@At("HEAD"),cancellable=true,require=1)
    private static void dreamseekers$validateBeforeBenefits(LivingDamageEvent event,CallbackInfo callback) {
        if(event.getEntity() instanceof JungleSporeCarrier carrier&&carrier.wasLeaseInitialized()
                &&(event.isCanceled()||!Float.isFinite(event.getAmount())||event.getAmount()<=0
                    ||!SporeCombat.validDamage(carrier,event.getSource()))) {
            event.setAmount(0);event.setCanceled(true);callback.cancel();
        }
    }
}
