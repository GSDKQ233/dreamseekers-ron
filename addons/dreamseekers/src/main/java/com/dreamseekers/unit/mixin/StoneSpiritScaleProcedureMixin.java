// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.*;
import net.minecraft.world.entity.Entity;
import net.pasterdream.procedures.SmallStoneSpiritPr1Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Both legacy worker and new combat type have fixed renderer scale, including before their first loaded tick. */
@Mixin(value=SmallStoneSpiritPr1Procedure.class,remap=false)
public abstract class StoneSpiritScaleProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$fixedPaidScale(Entity entity,CallbackInfoReturnable<Double> callback){
        if(entity instanceof StoneSpiritWorker || entity instanceof StoneSpiritThrowerUnit)callback.setReturnValue(1.0);
    }
}
