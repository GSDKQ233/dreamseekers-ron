// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.NativeGoldenFoxHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.GoldenFoxPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent the paid unit from becoming a consumable wild NPC, including direct callback callers. */
@Mixin(value = GoldenFoxPr0Procedure.class, remap = false)
public abstract class GoldenFoxExchangeProcedureMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamseekers$skipPaidExchange(LevelAccessor world, double x, double y, double z,
                                                     Entity entity, ItemStack stack, CallbackInfo callback) {
        if (NativeGoldenFoxHooks.isPaidUnit(entity)) callback.cancel();
    }

    // Locked original beta0.4.2 has exactly four delayed reward callbacks with this
    // signature. Recheck the captured entity, not its alive flag: wild exchanges
    // intentionally discard their fox before rewarding the player fifteen ticks later.
    @Inject(method = {"lambda$execute$0", "lambda$execute$1", "lambda$execute$2", "lambda$execute$3"},
            at = @At("HEAD"), cancellable = true, remap = false, require = 4, allow = 4)
    private static void dreamseekers$skipPaidDelayedReward(LevelAccessor world, double x, double y, double z,
                                                          Entity entity, CallbackInfo callback) {
        if (NativeGoldenFoxHooks.isPaidUnit(entity)) callback.cancel();
    }
}
