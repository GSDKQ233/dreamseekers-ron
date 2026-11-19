package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.StoneSpiritWorker;
import com.dreamseekers.unit.StoneSpiritThrowerUnit;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.SmallStoneSpiritPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress wild-only growth/terrain resurrection for paid RTS stone subtypes, not wild spirits. */
@Mixin(value = SmallStoneSpiritPr0Procedure.class, remap = false)
public abstract class StoneSpiritDeathProcedureMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamseekers$skipWildResurrection(LevelAccessor world, double x, double y, double z,
                                                         Entity entity, CallbackInfo callback) {
        if (entity instanceof StoneSpiritWorker || entity instanceof StoneSpiritThrowerUnit) callback.cancel();
    }
    /** Fixed beta0.4.2 query predicate: exclude paid targets before native size writes, not a later tick rollback. */
    @Inject(method = "lambda$execute$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamseekers$excludePaidGrowthTargets(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if(entity instanceof StoneSpiritWorker || entity instanceof StoneSpiritThrowerUnit) callback.setReturnValue(false);
    }
}
