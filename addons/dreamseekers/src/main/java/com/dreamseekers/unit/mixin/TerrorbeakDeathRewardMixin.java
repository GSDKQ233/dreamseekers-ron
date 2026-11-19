// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.TerrorbeakUnit;
import net.minecraft.world.entity.Entity;
import net.pasterdream.entity.TerrorbeakEntity;
import net.pasterdream.procedures.TerrorbeakPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** The procedure parameter is the killer, not the dead unit: gate this exact dying instance. */
@Mixin(TerrorbeakEntity.class)
public abstract class TerrorbeakDeathRewardMixin {
    @Redirect(method="die",at=@At(value="INVOKE",target="Lnet/pasterdream/procedures/TerrorbeakPr0Procedure;execute(Lnet/minecraft/world/entity/Entity;)V",remap=false))
    private void dreamseekers$noPaidDeathReward(Entity killer){
        if(!((Object)this instanceof TerrorbeakUnit))TerrorbeakPr0Procedure.execute(killer);
    }
}
