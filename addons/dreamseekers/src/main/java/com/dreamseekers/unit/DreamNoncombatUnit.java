package com.dreamseekers.unit;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.resources.UnitCargoSaveData;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared noncombat Unit contract; no AttackerUnit marker.
 * First extracted without behavior changes from the verified pink-chicken scout contract.
 * RTS contract and goal wiring adapted from Reign of Nether (SoLegendary contributors), GPL-3.0.
 */
public interface DreamNoncombatUnit extends com.dreamseekers.skill.SkillUnit {
    RtsUnitState rts();
    RtsSyncedData rtsSynced();
    default Mob mob() { return (Mob) this; }
    @Override default void initialiseGoals() { rts(); }
    @Override default void setAnchor(BlockPos pos) { rts().anchor = pos; }
    @Override default BlockPos getAnchor() { return rts().anchor; }
    @Override default ArrayList<Checkpoint> getCheckpoints() { return rts().checkpoints; }
    @Override default GarrisonGoal getGarrisonGoal() { return null; }
    @Override default boolean canGarrison() { return false; }
    @Override default UsePortalGoal getUsePortalGoal() { return null; }
    @Override default boolean canUsePortal() { return false; }
    @Override default Abilities getAbilities() { return rts().abilities; }
    @Override default void updateAbilityButtons() { rts().abilities = new Abilities(); }
    @Override default List<ItemStack> getItems() { return rts().items; }
    @Override default int getMaxResources() { return 0; }
    @Override default void setEatingTicksLeft(int ticks) { rts().eatingTicks = ticks; }
    @Override default int getEatingTicksLeft() { return rts().eatingTicks; }
    @Override default MoveToTargetBlockGoal getMoveGoal() { return rts().move; }
    @Override default SelectedTargetGoal<LivingEntity> getTargetGoal() { return rts().target; }
    @Override default ReturnResourcesGoal getReturnResourcesGoal() { return null; }
    @Override default LivingEntity getFollowTarget() { return rts().follow; }
    @Override default void setFollowTarget(LivingEntity target) { rts().follow = target; }
    @Override default boolean getHoldPosition() { return rts().hold; }
    @Override default void setHoldPosition(boolean hold) { rts().hold = hold; }
    @Override default String getOwnerName() { return mob().getEntityData().get(rtsSynced().owner); }
    @Override default void setOwnerName(String name) {
        if(!java.util.Objects.equals(getOwnerName(),name)){rts().commandRevision++;rts().ownershipRevision++;rts().skills().ownershipChanged();}
        mob().getEntityData().set(rtsSynced().owner, name);
    }
    @Override default void resetBehaviours() { rts().commandRevision++; }
    @Override default int getScenarioRoleIndex() { return mob().getEntityData().get(rtsSynced().scenario); }
    @Override default void setScenarioRoleIndex(int index) { mob().getEntityData().set(rtsSynced().scenario, index); }
    @Override default String getOnDeathCommand() { return mob().getEntityData().get(rtsSynced().deathCommand); }
    @Override default void setOnDeathCommand(String command) { mob().getEntityData().set(rtsSynced().deathCommand, command); }
    @Override default Object2ObjectArrayMap<Ability, Float> getAbilityCooldowns() { return rts().cooldowns; }
    @Override default Object2ObjectArrayMap<Ability, Integer> getAbilityCharges() { return rts().charges; }
    @Override default boolean hasAutocast(Ability ability) { return rts().autocast == ability; }
    @Override default void setAutocast(Ability ability) { rts().autocast = ability; }

    default void writeRtsData(CompoundTag tag) {
        addUnitSaveData(tag);
        rts().skills().save(tag);
        tag.put("DreamseekersCarried", UnitCargoSaveData.write(getItems()));
    }

    default void readRtsData(CompoundTag tag) {
        readUnitSaveData(tag);
        rts().skills().load(tag);
        UnitCargoSaveData.read(getItems(), tag.getList("DreamseekersCarried", Tag.TAG_COMPOUND), mob().isAddedToWorld());
    }

    static boolean isSettledRtsResource(ItemStack stack) {
        return UnitCargoSaveData.isLedgerValued(stack);
    }

}
