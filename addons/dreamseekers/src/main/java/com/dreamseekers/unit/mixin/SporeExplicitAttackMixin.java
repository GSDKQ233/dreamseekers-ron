// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.spore.SporeCombat;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Runs only after the existing action dispatcher has filtered authorized/actionable actors. */
@Mixin(value=UnitActionItem.class,remap=false)
public abstract class SporeExplicitAttackMixin {
    @Redirect(method="action",at=@At(value="INVOKE",target="Lcom/solegendary/reignofnether/unit/interfaces/AttackerUnit;setUnitAttackTargetForced(Lnet/minecraft/world/entity/LivingEntity;)V",remap=false),require=1)
    private void dreamseekers$explicitPointOrder(AttackerUnit attacker,LivingEntity target) {
        SporeCombat.commandedAttack(attacker,target);
    }
}
