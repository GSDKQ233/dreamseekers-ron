// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.DreamRtsUnit;
import com.dreamseekers.unit.DreamNoncombatUnit;
import net.minecraft.world.entity.Entity;
import net.pasterdream.procedures.AaroncoshandspawnblockPr1Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Fixed beta0.4.2 410-tick cleanup query only. Story, wild entities, rewards and other queries stay original. */
@Mixin(value=AaroncoshandspawnblockPr1Procedure.class,remap=false)
public abstract class AaroncosArenaCleanupMixin {
    @Inject(method="lambda$execute$7",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$excludePaidRts(Entity entity,CallbackInfoReturnable<Boolean> ci){
        if(entity instanceof DreamRtsUnit||entity instanceof DreamNoncombatUnit)ci.setReturnValue(false);
    }
}
