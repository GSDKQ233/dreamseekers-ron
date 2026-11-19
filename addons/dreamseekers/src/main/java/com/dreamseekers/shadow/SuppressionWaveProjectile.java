// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;

import com.dreamseekers.skill.SquealSuppressionWave;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Independent physical skill shot: no ordinary Squeal factory/provenance or native hit procedure. */
public final class SuppressionWaveProjectile extends ThrowableItemProjectile {
    public SuppressionWaveProjectile(EntityType<? extends SuppressionWaveProjectile> type,Level level) {
        super(type,level);setNoGravity(true);
    }
    @Override protected Item getDefaultItem() { return Items.ECHO_SHARD; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isOnPortalCooldown() { return true; }
    @Override protected void processPortalCooldown() { }
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos) { discard(); }
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    @Override public void setOwner(Entity owner) { super.setOwner(owner);SquealSuppressionWave.ownerChanged(this); }
    @Override protected boolean canHitEntity(Entity target) { return SquealSuppressionWave.canHit(this,target)&&super.canHitEntity(target); }
    @Override public void onHitEntity(EntityHitResult hit) { if(!level().isClientSide())SquealSuppressionWave.collide(this,hit); }
    @Override protected void onHitBlock(BlockHitResult hit) { if(!level().isClientSide())discard(); }
    @Override public void tick() {
        if(!level().isClientSide()&&(!SquealSuppressionWave.flightValid(this)||tickCount>=ShadowSquealGhostBalance.PROJECTILE_TTL)) { discard();return; }
        clearFire();setNoGravity(true);super.tick();
    }
    @Override public void remove(RemovalReason reason) { SquealSuppressionWave.projectileLeft(this);super.remove(reason); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        CompoundTag safe=tag.copy();safe.remove("Item");super.readAdditionalSaveData(safe);
        SquealSuppressionWave.projectileLeft(this);clearFire();setNoGravity(true);
    }
}
