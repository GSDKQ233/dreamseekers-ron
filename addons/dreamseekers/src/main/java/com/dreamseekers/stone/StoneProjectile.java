// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.stone;
import com.dreamseekers.unit.StoneSpiritThrowerUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.UUID;
/** Addon-owned ordinary physical stone. Vanilla cobblestone item renderer, no ARR projectile asset.
 * Single impact, no drops/terrain/AOE; normal STOP does not rewind a released stone.
 * Owner epoch, death, unload/reload or TTL invalidate authority; NBT never arms a projectile.
 */
public final class StoneProjectile extends ThrowableItemProjectile {
    private StoneSpiritThrowerUnit caster;
    private UUID casterId;
    private String ownerAtLaunch;
    private long ownerEpoch;
    private float damage;
    private com.solegendary.reignofnether.building.BuildingPlacement siegeBuilding;
    private String siegeOwner;
    private long siegeRevision;
    private UUID siegeAccountEpoch;
    private boolean armed,spent,everArmed;
    public StoneProjectile(EntityType<? extends StoneProjectile> type,Level level) {super(type,level);setNoGravity(true);}
    // ThrowableProjectile handles portals before onHitBlock. Gateway eligibility checks the
    // root entity's cooldown, not canChangeDimensions; keep this subtype permanently ineligible.
    @Override public boolean isOnPortalCooldown(){return true;}
    // Prevent the vanilla decrement from driving a permanent ineligibility sentinel below zero.
    @Override protected void processPortalCooldown() { }
    // Gateway uses the root vehicle; unsupported riding must not bypass this subtype's guard.
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    @Override public boolean canChangeDimensions(){return false;}
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos){terminate();}
    @Override protected Item getDefaultItem(){return Items.COBBLESTONE;}
    public void arm(StoneSpiritThrowerUnit source,LivingEntity target) {
        if(everArmed||spent||isRemoved()||!(level() instanceof ServerLevel server)||!server.getServer().isSameThread()
                ||source.level()!=level()||!source.canHarm(target))return;
        caster=source;casterId=source.getUUID();ownerAtLaunch=source.getOwnerName();ownerEpoch=source.rts().ownershipRevision;
        damage=Math.max(0,source.getUnitAttackDamage());setOwner(source);setPos(source.getX(),source.getEyeY(),source.getZ());
        var delta=target.getBoundingBox().getCenter().subtract(position());shoot(delta.x,delta.y,delta.z,StoneThrowerBalance.PROJECTILE_SPEED,0);
        clearFire();armed=true;everArmed=true;
    }
    /** Separate ordinary building payload; no entity hurt or skill AOE is enabled by this mode. */
    public void armBuilding(StoneSpiritThrowerUnit source,
            com.solegendary.reignofnether.building.BuildingPlacement building,net.minecraft.core.BlockPos contact) {
        if(everArmed||spent||isRemoved()||source==null||building==null||contact==null||source.level()!=level()
                ||!StoneSiegeGoal.buildingValid(source,building,building.ownerName,building.getCaptureRevision())
                ||!building.isPosPartOfBuilding(contact,true)||!StoneSiegeGoal.visible(source,contact)
                ||!StoneSiegeGoal.clearRay(source,contact)
                ||source.getEyePosition().distanceToSqr(Vec3.atCenterOf(contact))>source.getSiegeAttackRange()*source.getSiegeAttackRange())return;
        caster=source;casterId=source.getUUID();ownerAtLaunch=source.getOwnerName();ownerEpoch=source.rts().ownershipRevision;
        siegeBuilding=building;siegeOwner=building.ownerName;siegeRevision=building.getCaptureRevision();
        siegeAccountEpoch=com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(ownerAtLaunch).accountEpoch;
        damage=Math.max(0,source.getUnitAttackDamage());setOwner(source);setPos(source.getX(),source.getEyeY(),source.getZ());
        var delta=Vec3.atCenterOf(contact).subtract(position());shoot(delta.x,delta.y,delta.z,StoneThrowerBalance.PROJECTILE_SPEED,0);
        clearFire();armed=true;everArmed=true;
    }
    public boolean isBuildingShotValid() {return siegeBuilding!=null&&validCaster()&&siegeIdentity();}
    /** Completion is about the original placement, not whether another block may still be damaged. */
    private boolean siegeBuildingIdentity() {
        return siegeBuilding!=null&&siegeBuilding.getLevel()==level()
                &&com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(siegeBuilding)
                &&siegeRevision>=0&&siegeRevision==siegeBuilding.getCaptureRevision()
                &&siegeOwner.equals(siegeBuilding.ownerName);
    }
    private boolean siegeIdentity() {
        return caster!=null&&StoneSiegeGoal.accountValid(caster,siegeAccountEpoch)&&ownerAtLaunch.equals(caster.getOwnerName())&&ownerEpoch==caster.rts().ownershipRevision
                &&StoneSiegeGoal.buildingValid(caster,siegeBuilding,siegeOwner,siegeRevision);
    }
    private boolean validCaster(){return armed&&!spent&&!isRemoved()&&level() instanceof ServerLevel server&&server.getServer().isSameThread()
            &&caster!=null&&caster.isAlive()&&!caster.isRemoved()&&caster.level()==server&&server.getEntity(casterId)==caster&&getOwner()==caster
            &&ownerAtLaunch.equals(caster.getOwnerName())&&ownerEpoch==caster.rts().ownershipRevision;}
    @Override protected boolean canHitEntity(Entity entity){return validCaster()&&caster.canHarm(entity)&&super.canHitEntity(entity);}
    @Override public void onHitEntity(EntityHitResult result){
        if(siegeBuilding!=null){terminate();return;}
        if(!level().isClientSide()&&validCaster()&&caster.canHarm(result.getEntity())){
            var impact=com.solegendary.reignofnether.api.PrimaryUnitHitHooks.capture(this,result.getEntity());
            terminate(); // Consume before hurt/event callbacks, including a rejected damage attempt.
            impact.hurt(damageSources().thrown(this,caster),damage);
        }
        terminate();
    }
    @Override protected void onHitBlock(BlockHitResult result){
        if(!level().isClientSide()&&validCaster()&&siegeIdentity()
                &&siegeBuilding.isPosPartOfBuilding(result.getBlockPos(),true)
                &&StoneSiegeGoal.visible(caster,result.getBlockPos())) {
            // Reserve the one impact before callbacks; guarded building writes recheck authority per block.
            armed=false;spent=true;
            var hit=result.getBlockPos().immutable();
            double budget=damage*caster.getBuildingDamageMultiplier();
            siegeBuilding.destroyRandomBlocksGuarded(budget,
                    ()->siegeIdentity()&&StoneSiegeGoal.visible(caster,hit),this::siegeBuildingIdentity,caster);
        }
        terminate();
    }
    private void terminate(){armed=false;spent=true;discard();}
    @Override public void tick(){
        if(!level().isClientSide()&&(!validCaster()||(siegeBuilding!=null&&!siegeIdentity())||tickCount>=StoneThrowerBalance.PROJECTILE_TTL)){terminate();return;}
        clearFire();setNoGravity(true);super.tick();
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        CompoundTag safe=tag.copy();safe.remove("Item");super.readAdditionalSaveData(safe);
        armed=false;spent=true;everArmed=true;siegeBuilding=null;siegeOwner=null;caster=null;casterId=null;ownerAtLaunch=null;clearFire();setNoGravity(true);
    }
}
