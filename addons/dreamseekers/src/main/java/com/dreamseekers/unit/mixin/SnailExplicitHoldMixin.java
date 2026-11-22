// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.BasaltSnailUnit;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Marks only an accepted owner HOLD in the order dispatcher, not an effective hold or a direct setter. */
@Mixin(value=UnitActionItem.class,remap=false)
public abstract class SnailExplicitHoldMixin {
    @Shadow @Final private String ownerName;

    @Redirect(method="action",at=@At(value="INVOKE",target="Lcom/solegendary/reignofnether/unit/interfaces/Unit;setHoldPosition(Z)V",remap=false),require=1)
    private void dreamseekers$recordHold(Unit unit,boolean hold) {
        unit.setHoldPosition(hold);
        if(hold&&unit instanceof BasaltSnailUnit snail)snail.acceptedOwnerHold(ownerName);
    }
}
