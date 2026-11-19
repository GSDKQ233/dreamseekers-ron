// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.ShadowHandUnit;
import net.minecraft.world.entity.Entity;
import net.pasterdream.procedures.ShadowHandPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native hurt invokes impulse before validating damage, even for immune hits.
 * Paid instances retain vanilla damage/knockback but never run this uncommanded dash. */
@Mixin(value=ShadowHandPr0Procedure.class,remap=false)
public abstract class ShadowHandImpulseProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$noNativeHurtDash(Entity source,CallbackInfo ci) {
        if(source instanceof ShadowHandUnit)ci.cancel();
    }
}
