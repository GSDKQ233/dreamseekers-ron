// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.PinkSlimeUnit;
import net.minecraft.world.entity.Entity;
import net.pasterdream.procedures.PinkSlimePr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Cancel only this paid subclass, including direct calls; original wild hopping is untouched. */
@Mixin(value=PinkSlimePr0Procedure.class, remap=false)
public abstract class PinkSlimeImpulseMixin {
    @Inject(method="execute", at=@At("HEAD"), cancellable=true, remap=false)
    private static void dreamseekers$keepCommandedMotion(Entity entity, CallbackInfo callback) {
        if (entity instanceof PinkSlimeUnit) callback.cancel();
    }
}
