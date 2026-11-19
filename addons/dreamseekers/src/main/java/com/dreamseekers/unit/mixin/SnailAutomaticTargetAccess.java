// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Read-only reuse of the original automatic enemy/neutral/flying/creative target policy. */
@Mixin(value=MiscUtil.class,remap=false)
public interface SnailAutomaticTargetAccess {
    @Invoker("isIdleOrMoveAttackable")
    static boolean dreamseekers$ordinaryAutomaticTarget(Mob source,LivingEntity target,boolean neutralAggro) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
