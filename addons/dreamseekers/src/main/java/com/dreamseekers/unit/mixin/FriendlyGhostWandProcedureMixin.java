// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.FriendlyGhostUnit;
import net.minecraft.world.entity.Entity;
import net.pasterdream.procedures.SquealWaveWandPr3Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Fixed beta0.4.2 query predicates, not a global MAGIC immunity or wand cancellation.
 * Wild immediate damage and one-tick original summoning/taming remain unchanged. */
@Mixin(value=SquealWaveWandPr3Procedure.class,remap=false)
public abstract class FriendlyGhostWandProcedureMixin {
    @Inject(method={"lambda$execute$0","lambda$execute$2"},at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$excludePaidContractGhost(Entity target,CallbackInfoReturnable<Boolean> cir) {
        if(target instanceof FriendlyGhostUnit)cir.setReturnValue(false);
    }
}
