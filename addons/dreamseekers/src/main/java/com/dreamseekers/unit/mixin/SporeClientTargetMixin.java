// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Cursor classification only, not ownership/control/vision or server damage authority. */
@Mixin(value=UnitClientEvents.class,remap=false)
public abstract class SporeClientTargetMixin {
    @Inject(method="getPlayerToEntityRelationship",at=@At("HEAD"),cancellable=true,require=1)
    private static void dreamseekers$pointKillCursor(LivingEntity entity,CallbackInfoReturnable<Relationship> callback) {
        Minecraft client=Minecraft.getInstance();
        if(!(entity instanceof JungleSporeCarrier carrier)||!carrier.displayedActive()||carrier.displayedOwner().isBlank()
                ||client.level==null||client.player==null||entity.level()!=client.level||entity.isRemoved()||!entity.isAlive()
                ||client.level.getEntity(entity.getId())!=entity)return;
        String viewer=client.player.getGameProfile().getName(),owner=carrier.displayedOwner();
        callback.setReturnValue(viewer.equals(owner)||AlliancesClient.isAllied(viewer,owner)?Relationship.FRIENDLY:Relationship.HOSTILE);
    }
}
