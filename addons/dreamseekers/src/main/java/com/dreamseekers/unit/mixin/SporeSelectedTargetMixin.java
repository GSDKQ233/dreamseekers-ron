// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.spore.SporeCombat;
import com.solegendary.reignofnether.unit.goals.SelectedTargetGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SelectedTargetGoal.class,remap=false)
public abstract class SporeSelectedTargetMixin extends TargetGoal {
    protected SporeSelectedTargetMixin(Mob mob,boolean mustSee) { super(mob,mustSee); }
    @Inject(method="setTarget",at=@At("HEAD"),require=1)
    private void dreamseekers$realTargetChanged(LivingEntity target,CallbackInfo callback) { SporeCombat.targetChanged(mob,target); }
}
