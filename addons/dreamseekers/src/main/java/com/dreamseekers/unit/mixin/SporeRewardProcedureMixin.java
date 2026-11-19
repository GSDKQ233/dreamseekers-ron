// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.spore.JungleSporeCarrier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.SporeEntityPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Do not call the native mixed visual/random reward routine for disabled summon carriers. Wild unchanged. */
@Mixin(value=SporeEntityPr0Procedure.class,remap=false)
public abstract class SporeRewardProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true)
    private static void dreamseekers$noCarrierRewards(LevelAccessor world,double x,double y,double z,Entity entity,CallbackInfo ci) {
        if(entity instanceof JungleSporeCarrier)ci.cancel();
    }
}
