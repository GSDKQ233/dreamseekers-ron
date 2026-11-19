package com.dreamseekers.unit.mixin;

import com.dreamseekers.client.DreamUnitVisibilityPolicy;
import com.dreamseekers.unit.DreamUnitVisibilitySubject;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-list-only, bounded distance eligibility; does not disable any other visibility gate. */
@Mixin(Entity.class)
public abstract class DreamUnitRenderDistanceMixin {
    @Inject(method = "shouldRenderAtSqrDistance(D)Z", at = @At("RETURN"), cancellable = true)
    private void dreamseekers$allowRtsCameraDistance(double distanceSquared, CallbackInfoReturnable<Boolean> callback) {
        if (!DreamUnitVisibilitySubject.matches(this) || !OrthoviewClientEvents.isEnabled()) return;
        if (DreamUnitVisibilityPolicy.renderAtDistance(callback.getReturnValue(), true, true, distanceSquared)) {
            callback.setReturnValue(true);
        }
    }
}
