// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.BoneWingUnit;
import com.dreamseekers.unit.AshBoneWingUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.BoneWingPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Cancel only the paid subtype, including direct calls and missing/changed ownership. */
@Mixin(value=BoneWingPr0Procedure.class, remap=false)
public abstract class BoneWingImpulseMixin {
    @Inject(method="execute", at=@At("HEAD"), cancellable=true, remap=false)
    private static void dreamseekers$keepCommandedMotion(LevelAccessor world, double x, double y, double z,
                                                        Entity entity, CallbackInfo callback) {
        if (entity instanceof BoneWingUnit || entity instanceof AshBoneWingUnit) callback.cancel();
    }
}
