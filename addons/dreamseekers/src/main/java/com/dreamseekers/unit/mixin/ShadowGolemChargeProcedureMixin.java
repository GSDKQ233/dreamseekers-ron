// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.ShadowGolemUnit;
import net.minecraft.world.entity.Entity;
import net.pasterdream.procedures.ShadowGolemPr1Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Original hurt charges skill even when its damage is rejected. Instance isolation is not owner/AI-dependent. */
@Mixin(value=ShadowGolemPr1Procedure.class,remap=false)
public abstract class ShadowGolemChargeProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$noNativeCharge(Entity source,CallbackInfo ci){if(source instanceof ShadowGolemUnit)ci.cancel();}
}
