package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.pasterdream.entity.SmallStoneSpiritEntity;
import java.util.ArrayList;
import java.util.List;

/** Actual SmallStoneSpirit subclass: PasterDream owns its animation/model/texture and sound. */
public class StoneSpiritWorker extends SmallStoneSpiritEntity implements DreamRtsUnit, WorkerUnit {
    public static final ResourceCost COST = ResourceCost.Unit(50, 0, 0, 15, 1);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(StoneSpiritWorker.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(StoneSpiritWorker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(StoneSpiritWorker.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;

    @SuppressWarnings("unchecked")
    public StoneSpiritWorker(EntityType<? extends SmallStoneSpiritEntity> type, Level level) {
        super((EntityType<SmallStoneSpiritEntity>) type, level);
        rts();
        setPersistenceRequired();
        xpReward = 0;
    }

    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, true);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override public ResourceCost getCost() { return COST; }
    @Override public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.LEGACY_WORKER_ICON; }
    @Override public BuildRepairGoal getBuildRepairGoal() { return rts().build; }
    @Override public GatherResourcesGoal getGatherResourceGoal() { return rts().gather; }
    @Override public ExploreBuildLocationGoal getExploreBuildLocationGoal() { return rts().explore; }
    @Override public BlockState getReplantBlockState() { return Blocks.WHEAT.defaultBlockState(); }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void customServerAiStep() { }
    @Override protected void pickUpItem(ItemEntity item) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) { }

    @Override public List<Button> getAbilityButtons() {
        List<Button> buttons = new ArrayList<>(getAbilities().getButtons(this));
        if (level().isClientSide()) buttons.addAll(Factions.getFaction(this).getBuildingButtons());
        return buttons;
    }

    @Override public void tick() {
        // Native wild-spirit death effects must not permanently grow an RTS worker.
        getPersistentData().putDouble("size", 0);
        setCanPickUpLoot(true); // RoN picks up resource tokens; vanilla pickup is disabled above.
        super.tick();
        if (isAlive()) {
            Unit.tick(this);
            AttackerUnit.tick(this);
            WorkerUnit.tick(this);
            // The installed original model has an attack animation, but no tool-in-hand layer.
            // Reuse that motion as restrained work feedback only after reaching a worksite.
            if (!level().isClientSide() && tickCount % 20 == 0 && getNavigation().isDone()
                    && (getBuildRepairGoal().isBuilding() || getGatherResourceGoal().isGathering()))
                swing(InteractionHand.MAIN_HAND);
        }
    }
    @Override public EntityDimensions getDimensions(Pose pose) { return getType().getDimensions(); }
    @Override public net.minecraft.world.entity.Entity changeDimension(net.minecraft.server.level.ServerLevel destination,
            net.minecraftforge.common.util.ITeleporter teleporter) {
        return DreamDimensionCargo.changeDimension(this, destination, () -> super.changeDimension(destination, teleporter));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); writeRtsData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); readRtsData(tag); }
    public static AttributeSupplier.Builder createAttributes() { return DreamRtsUnit.attributes(28, 1, 0.25, 1, 0.5, 0); }
}
