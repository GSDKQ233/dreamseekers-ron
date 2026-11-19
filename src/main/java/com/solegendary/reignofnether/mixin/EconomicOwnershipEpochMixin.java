package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe already-tracked ownership-sensitive work; original skill observer remains independent. */
@Mixin(SynchedEntityData.class)
public abstract class EconomicOwnershipEpochMixin {
    @Shadow @Final private Entity entity;
    @Inject(method="set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",at=@At("RETURN"))
    private <T> void reignofnether$economicOwner(EntityDataAccessor<T> key,T value,boolean force,CallbackInfo ci) {
        EconomicUnitIdentity.observeOwnerMutation(entity);
        UnitServerEvents.observeFormationOwnerMutation(entity);
    }
}
