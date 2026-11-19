package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.BasaltSnailUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.BasaltSnailPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The RTS subclass owns its bounded shell cooldown; original wild snails are unchanged. */
@Mixin(value = BasaltSnailPr0Procedure.class, remap = false)
public abstract class BasaltSnailShellProcedureMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamseekers$useRtsShell(LevelAccessor world, Entity entity, CallbackInfo callback) {
        if (entity instanceof BasaltSnailUnit) callback.cancel();
    }
}
