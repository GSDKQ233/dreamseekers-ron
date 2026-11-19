// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.FireflyWorker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.pasterdream.procedures.FireflyPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the paid RTS worker safe even if a compatibility mod calls the native procedure directly. */
@Mixin(value = FireflyPr0Procedure.class, remap = false)
public abstract class FireflyCaptureProcedureMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamseekers$skipWorkerCapture(Entity entity, Entity sourceentity, ItemStack itemstack,
                                                       CallbackInfo callback) {
        if (entity instanceof FireflyWorker) callback.cancel();
    }
}
