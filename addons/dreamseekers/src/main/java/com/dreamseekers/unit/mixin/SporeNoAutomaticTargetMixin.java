// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Explicit point orders never use this idle/attack-move candidate filter. Wild/raw carriers stay legacy. */
@Mixin(value=MiscUtil.class,remap=false)
public abstract class SporeNoAutomaticTargetMixin {
    @Inject(method="isIdleOrMoveAttackable",at=@At("HEAD"),cancellable=true,require=1)
    private static void dreamseekers$explicitOnly(Mob source,LivingEntity target,boolean neutralAggro,CallbackInfoReturnable<Boolean> callback) {
        if(target instanceof JungleSporeCarrier carrier&&carrier.wasLeaseInitialized())callback.setReturnValue(false);
    }
}
