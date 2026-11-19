package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.packets.UnitConvertClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public interface ConvertableUnit {
    boolean shouldDiscard();
    void setShouldDiscard(boolean shouldDiscard);

    /** Server-authoritative replacement. A failed publication leaves the original fully intact;
     * client selection mapping is a notification and never authorizes retirement. */
    default LivingEntity convertToUnit(EntityType<? extends Unit> entityType) {
        Unit oldUnit = (Unit) this;
        LivingEntity oldEntity = (LivingEntity) this;
        if (!(oldEntity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !oldEntity.isAlive() || oldEntity.isRemoved()
                || level.getEntity(oldEntity.getUUID()) != oldEntity || level.getEntity(oldEntity.getId()) != oldEntity)
            return null;
        var identity = EconomicUnitIdentity.capture(oldEntity);
        LivingEntity newEntity = (LivingEntity) entityType.create(level);
        if (!(newEntity instanceof Unit newUnit)) return null;

        if (this instanceof UnitInventory from && newEntity instanceof UnitInventory to)
            for (int i = 0; i < UnitInventory.MAX_INVENTORY_SIZE; i++) to.set(i, from.get(i).copy());
        for (ItemStack item : oldUnit.getItems()) newUnit.getItems().add(item.copy());
        float maxHealthDiff = newEntity.getMaxHealth() - oldEntity.getMaxHealth();
        newEntity.setHealth(Math.max(1, oldEntity.getHealth() + maxHealthDiff));
        for (MobEffectInstance effect : oldEntity.getActiveEffects()) newEntity.addEffect(new MobEffectInstance(effect));
        newEntity.copyPosition(oldEntity);
        newUnit.setOwnerName(oldUnit.getOwnerName());
        newUnit.setScenarioRoleIndex(oldUnit.getScenarioRoleIndex());
        newUnit.setOnDeathCommand(oldUnit.getOnDeathCommand());
        // Preserve economic lineage/free provenance, not arbitrary transient addon launch leases.
        if (oldEntity.getPersistentData().contains(RtsKillValueClaims.KEY))
            newEntity.getPersistentData().put(RtsKillValueClaims.KEY,oldEntity.getPersistentData().get(RtsKillValueClaims.KEY).copy());
        else {
            CompoundTag value = new CompoundTag();value.putInt("version",1);value.putUUID("origin",oldEntity.getUUID());
            newEntity.getPersistentData().put(RtsKillValueClaims.KEY,value);
        }
        if (oldEntity.getPersistentData().contains(RtsKillValueClaims.FREE_ORIGIN_KEY))
            newEntity.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,oldEntity.getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY).copy());

        if (!level.addFreshEntity(newEntity) || !identity.sameOwner() || !oldEntity.isAlive()
                || !newEntity.isAlive() || level.getEntity(newEntity.getUUID()) != newEntity
                || !identity.owner().equals(newUnit.getOwnerName())) {
            UnitServerEvents.rollbackUncommittedProductionSpawn(newEntity);
            return null;
        }
        Entity vehicle = oldEntity.getVehicle();
        List<Entity> passengers = List.copyOf(oldEntity.getPassengers());
        if (vehicle != null) { oldEntity.stopRiding(); newEntity.startRiding(vehicle, true); }
        for (Entity passenger : passengers) { passenger.stopRiding(); passenger.startRiding(newEntity, true); }
        if (!identity.sameOwner() || !UnitServerEvents.retireTransformedUnit(oldEntity,newEntity,() ->
                UnitConvertClientboundPacket.syncConvertedUnits(identity.owner(),List.of(oldEntity.getId()),List.of(newEntity.getId())))) {
            // Riding callbacks can invalidate the transaction too. Return only still-owned riders.
            if (newEntity.getVehicle() == vehicle) newEntity.stopRiding();
            if (!oldEntity.isRemoved() && vehicle != null && !vehicle.isRemoved()) oldEntity.startRiding(vehicle,true);
            for (Entity passenger : passengers) if (passenger.getVehicle() == newEntity) {
                passenger.stopRiding();if (!oldEntity.isRemoved()) passenger.startRiding(oldEntity,true);
            }
            UnitServerEvents.rollbackUncommittedProductionSpawn(newEntity);
            return null;
        }
        // Retirement has committed. Never require a player to be online to transfer active orders.
        if (newEntity.isAlive() && !newEntity.isRemoved() && identity.owner().equals(newUnit.getOwnerName())) {
        newUnit.setHoldPosition(oldUnit.getHoldPosition());
        var follow = oldUnit.getFollowTarget();
        if (follow != null && follow.isAlive() && follow.level() == level) newUnit.setFollowTarget(follow);
        if (oldUnit.getMoveGoal() != null && newUnit.getMoveGoal() != null)
            newUnit.getMoveGoal().setMoveTarget(oldUnit.getMoveGoal().getMoveTarget());
        if (oldUnit.getReturnResourcesGoal() != null && oldUnit.getReturnResourcesGoal().getBuildingTarget() != null && newUnit.getReturnResourcesGoal() != null)
            newUnit.getReturnResourcesGoal().setBuildingTarget(oldUnit.getReturnResourcesGoal().getBuildingTarget());
        if (oldUnit instanceof AttackerUnit oldAttacker && newUnit instanceof AttackerUnit newAttacker) {
            newAttacker.setAttackMoveTarget(oldAttacker.getAttackMoveTarget());
            var target = oldUnit.getTargetGoal() == null ? null : oldUnit.getTargetGoal().getTarget();
            if (target != null && target.isAlive() && target.level() == level) newAttacker.setUnitAttackTargetForced(target);
        }
        }
        if (!oldUnit.getItems().isEmpty()) oldUnit.getItems().clear();
        UnitSyncClientboundPacket.sendSyncResourcesPacket(newUnit);
        return newEntity;
    }
}
