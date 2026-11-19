// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.SkillIdentity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** All current core Unit owner setters use synced data. Only tracked skill identities are observed. */
@Mixin(SynchedEntityData.class)
public abstract class SkillOwnershipEpochMixin {
    @Shadow @Final private Entity entity;
    @Inject(method="set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",at=@At("RETURN"))
    private <T> void dreamseekers$observeOwner(EntityDataAccessor<T> key,T value,boolean force,CallbackInfo ci) {
        SkillIdentity.observeOwnerMutation(entity);
    }
}
