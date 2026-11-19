// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.DreamNoncombatUnit;
import com.dreamseekers.unit.DreamRtsUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Vanilla stop clears only path, not its delayed recompute request. Cancel that request for addon RTS only. */
@Mixin(PathNavigation.class)
public abstract class CommandNavigationStopMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected boolean hasDelayedRecomputation;
    @Shadow private BlockPos targetPos;
    @Inject(method="stop",at=@At("TAIL"))
    private void dreamseekers$cancelDeferredCommandPath(CallbackInfo callback) {
        if (mob instanceof DreamRtsUnit || mob instanceof DreamNoncombatUnit) {
            hasDelayedRecomputation=false;
            targetPos=null;
        }
    }
}
