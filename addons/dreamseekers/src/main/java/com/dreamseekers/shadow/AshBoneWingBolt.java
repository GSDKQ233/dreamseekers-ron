// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;

import com.dreamseekers.unit.AshBoneWingUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.pasterdream.entity.BoneWingFireBallProjectileEntity;
import java.util.UUID;

/** Physical single-impact ranged bolt; only installed runtime renderer/particle visuals are reused.
 * No original hit procedure, fire, extra knockback, block activation, explosion or pickup.
 * Unloaded/reloaded projectiles fail closed; NBT can never arm one or mint another paid attack.
 */
public final class AshBoneWingBolt extends BoneWingFireBallProjectileEntity {
    private AshBoneWingUnit caster;
    private UUID casterId;
    private String ownerAtLaunch;
    private long ownerEpoch;
    private float damage;
    private boolean armed;
    private boolean spent;
    private boolean everArmed;
    public AshBoneWingBolt(EntityType<? extends BoneWingFireBallProjectileEntity> type,Level level) {
        super(type,level);pickup=Pickup.DISALLOWED;setNoGravity(true);
    }
    public void arm(AshBoneWingUnit source,LivingEntity target) {
        if(everArmed||spent||isRemoved()||!(level() instanceof ServerLevel server)||!server.getServer().isSameThread()
                ||source.level()!=level()||!source.canHarm(target))return;
        caster=source;casterId=source.getUUID();ownerAtLaunch=source.getOwnerName();ownerEpoch=source.rts().ownershipRevision;
        damage=Math.max(0,source.getUnitAttackDamage());setOwner(source);
        setPos(source.getX(),source.getEyeY(),source.getZ());
        var delta=target.getBoundingBox().getCenter().subtract(position());
        shoot(delta.x,delta.y,delta.z,AshBoneWingBalance.PROJECTILE_SPEED,0);
        setBaseDamage(0);setKnockback(0);setCritArrow(false);clearFire();armed=true;everArmed=true;
    }
    private boolean validCaster() {
        return armed&&!spent&&!isRemoved()&&level() instanceof ServerLevel level&&level.getServer().isSameThread()&&caster!=null&&caster.isAlive()&&!caster.isRemoved()
                &&caster.level()==level&&level.getEntity(caster.getId())==caster&&level.getEntity(casterId)==caster&&getOwner()==caster
                &&ownerAtLaunch.equals(caster.getOwnerName())&&ownerEpoch==caster.rts().ownershipRevision;
    }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isOnPortalCooldown() { return true; }
    @Override protected void processPortalCooldown() { }
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos) { }
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    private boolean publishedTarget(Entity entity) {
        return level() instanceof ServerLevel server && entity.level()==server
                && server.getEntity(entity.getId())==entity && server.getEntity(entity.getUUID())==entity;
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return validCaster()&&publishedTarget(entity)&&caster.canHarm(entity)&&super.canHitEntity(entity);
    }
    @Override public void onHitEntity(EntityHitResult result) {
        if(!level().isClientSide()&&validCaster()&&publishedTarget(result.getEntity())&&caster.canHarm(result.getEntity())) {
            var impact=com.solegendary.reignofnether.api.PrimaryUnitHitHooks.capture(this,result.getEntity());
            terminate(); // Consume before hurt: event callbacks cannot reuse this impact.
            impact.hurt(damageSources().arrow(this,caster),damage);
        }
        terminate();
    }
    private void terminate() {armed=false;spent=true;discard();}
    @Override protected void onHitBlock(BlockHitResult result) {terminate();}
    @Override protected ItemStack getPickupItem() {return ItemStack.EMPTY;}
    @Override public void playerTouch(Player player) { }
    @Override public void tick() {
        if(!level().isClientSide()&&(!validCaster()||tickCount>=AshBoneWingBalance.PROJECTILE_TTL)) {terminate();return;}
        pickup=Pickup.DISALLOWED;clearFire();setNoGravity(true);super.tick();
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);armed=false;spent=true;everArmed=true;caster=null;casterId=null;ownerAtLaunch=null;
        pickup=Pickup.DISALLOWED;clearFire();setNoGravity(true);
    }
}
