// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.stone;

import com.dreamseekers.skill.StoneHeavyShot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Dedicated nonordinary skill projectile. Service-private launch authority is never serialized.
 * Spent collision is reserved before hurt; physical removal waits for primary and bounded AOE finally.
 */
public final class HeavyStoneProjectile extends ThrowableItemProjectile {
    public HeavyStoneProjectile(EntityType<? extends HeavyStoneProjectile> type,Level level) {
        super(type,level);setNoGravity(true);
    }
    @Override protected Item getDefaultItem() { return Items.COBBLESTONE; }
    @Override public boolean isOnPortalCooldown() { return true; }
    @Override protected void processPortalCooldown() { }
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos) { discard(); }
    @Override public void setOwner(Entity owner) {
        super.setOwner(owner);StoneHeavyShot.ownerChanged(this);
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return StoneHeavyShot.canHit(this,entity)&&super.canHitEntity(entity);
    }
    @Override public void onHitEntity(EntityHitResult hit) {
        if(!level().isClientSide())StoneHeavyShot.collide(this,hit);
    }
    @Override protected void onHitBlock(BlockHitResult hit) { if(!level().isClientSide())discard(); }
    @Override public void tick() {
        if(!level().isClientSide()&&(!StoneHeavyShot.flightValid(this)||tickCount>=StoneThrowerBalance.PROJECTILE_TTL)) {
            discard();return;
        }
        clearFire();setNoGravity(true);super.tick();
    }
    @Override public void remove(RemovalReason reason) {
        StoneHeavyShot.projectileLeft(this);super.remove(reason);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        CompoundTag safe=tag.copy();safe.remove("Item");super.readAdditionalSaveData(safe);
        StoneHeavyShot.projectileLeft(this);clearFire();setNoGravity(true);
    }
}
