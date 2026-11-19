// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.buildingability.BuildingAbilityEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Revalidate guarded movement/damage before their actual native attribute consumption.
 * Ordinary bodies without a building lease are exact no-ops; this never touches cooldowns/orders.
 */
@Mixin(LivingEntity.class)
public abstract class BuildingAbilityAttributeReadMixin {
    @Inject(method="getAttribute(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;",at=@At("HEAD"))
    private void dreamseekers$refreshBuildingInstance(Attribute attribute,CallbackInfoReturnable<AttributeInstance> cir) {
        BuildingAbilityEffects.refreshAttribute((LivingEntity)(Object)this,attribute);
    }
    @Inject(method="getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D",at=@At("HEAD"))
    private void dreamseekers$refreshBuildingAttribute(Attribute attribute,CallbackInfoReturnable<Double> cir) {
        BuildingAbilityEffects.refreshAttribute((LivingEntity)(Object)this,attribute);
    }
}
