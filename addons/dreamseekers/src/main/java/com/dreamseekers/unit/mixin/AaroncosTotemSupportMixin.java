// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Original boss support must not automatically grant free resistance to RTS subclasses. */
@Mixin(value=net.pasterdream.procedures.ShadowTuneTotemPr1Procedure.class,remap=false)
public abstract class AaroncosTotemSupportMixin {
    @Inject(method="lambda$execute$0",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$excludePaid(Entity target,CallbackInfoReturnable<Boolean> ci){if(target instanceof ControlledAaroncosHand)ci.setReturnValue(false);}
}
