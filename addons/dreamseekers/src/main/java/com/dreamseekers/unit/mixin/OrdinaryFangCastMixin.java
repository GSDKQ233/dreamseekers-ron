// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinaryFangOrigin;
import com.solegendary.reignofnether.unit.goals.GenericTargetedSpellGoal;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.function.Consumer;
@Mixin(value=GenericTargetedSpellGoal.class,remap=false)
public abstract class OrdinaryFangCastMixin extends MoveToTargetBlockGoal {
    protected OrdinaryFangCastMixin(Mob mob) { super(mob,false,0); }
    @Redirect(method="tick",remap=true,at=@At(value="INVOKE",target="Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V",ordinal=1,remap=false),require=1)
    private void dreamseekers$ordinaryFangCast(Consumer<Object> callback,Object victim) {
        OrdinaryFangOrigin.cast(mob,(GenericTargetedSpellGoal)(Object)this,callback,victim);
    }
}
