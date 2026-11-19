// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;

import com.dreamseekers.skill.AshEmberLanding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Independent non-homing visual carrier. Neither collision deals direct damage. */
public final class EmberLandingProjectile extends ThrowableItemProjectile {
    public static final int TTL=60;
    public static final float SPEED=.8f;
    public EmberLandingProjectile(EntityType<? extends EmberLandingProjectile> type,Level level) { super(type,level);setNoGravity(true); }
    @Override protected Item getDefaultItem() { return Items.FIRE_CHARGE; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isOnPortalCooldown() { return true; }
    @Override protected void processPortalCooldown() { }
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos) { discard(); }
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    @Override public void setOwner(Entity owner) { super.setOwner(owner);AshEmberLanding.ownerChanged(this); }
    @Override protected boolean canHitEntity(Entity target) { return AshEmberLanding.canHit(this,target)&&super.canHitEntity(target); }
    @Override public void onHitEntity(EntityHitResult hit) { if(!level().isClientSide())AshEmberLanding.land(this,hit); }
    @Override protected void onHitBlock(BlockHitResult hit) { if(!level().isClientSide())AshEmberLanding.land(this,hit); }
    @Override public void tick() {
        if(!level().isClientSide()&&(!AshEmberLanding.flightValid(this)||tickCount>=TTL)) { discard();return; }
        clearFire();setNoGravity(true);super.tick();
    }
    @Override public void remove(RemovalReason reason) { AshEmberLanding.projectileLeft(this);super.remove(reason); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        CompoundTag safe=tag.copy();safe.remove("Item");super.readAdditionalSaveData(safe);
        AshEmberLanding.projectileLeft(this);clearFire();setNoGravity(true);
    }
}
