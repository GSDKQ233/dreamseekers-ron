// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.gametest.mixin;
import com.dreamseekers.gametest.SporeRandomProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.SporeEntityPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Forces only selected fixture UUIDs through the ORIGINAL random branches. */
@Mixin(value=SporeEntityPr0Procedure.class,remap=false)
public abstract class SporeNativeRandomProbeMixin {
    @Redirect(method="execute",at=@At(value="INVOKE",target="Ljava/lang/Math;random()D"),remap=false)
    private static double random(LevelAccessor world,double x,double y,double z,Entity entity){return SporeRandomProbe.next(entity);}
}
