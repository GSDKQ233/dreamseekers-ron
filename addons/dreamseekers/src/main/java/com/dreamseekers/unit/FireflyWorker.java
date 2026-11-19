// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import com.solegendary.reignofnether.unit.goals.ExploreBuildLocationGoal;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.CollisionFlyingWorker;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Native original-PasterDream firefly with RTS orders instead of wild wandering.
 * Model, wing animation and emissive layer remain owned by the installed dependency.
 * Its visual glow does not add world lighting, sight, damage or an attack animation.
 */
public class FireflyWorker extends net.pasterdream.entity.FireflyEntity implements DreamRtsUnit, CollisionFlyingWorker {
    public static final ResourceCost COST = ResourceCost.Unit(50, 0, 0, 15, 1);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(FireflyWorker.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(FireflyWorker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(FireflyWorker.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;

    @SuppressWarnings("unchecked")
    public FireflyWorker(EntityType<? extends net.pasterdream.entity.FireflyEntity> type, Level level) {
        super((EntityType<net.pasterdream.entity.FireflyEntity>) type, level);
        rts();
        updateAbilityButtons();
        setPersistenceRequired();
        setNoGravity(true);
        xpReward = 0;
    }

    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, true);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override protected PathNavigation createNavigation(Level level) { return new LowAltitudeFlyingNavigation(this, level); }
    @Override public void updateAbilityButtons() {
        rts().abilities=new com.solegendary.reignofnether.ability.Abilities();
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.FIREFLY_RUSH);
        rts().abilities.add(com.dreamseekers.neutralcontract.ContractAbility.INSTANCE);
    }
    @Override public boolean isIdle() {
        return !com.dreamseekers.skill.FireflyRush.hasPausedWork(this)&&DreamRtsUnit.super.isIdle();
    }
    /** RoN resource-token pickup checks this getter; vanilla equipment pickup remains disabled above. */
    @Override public boolean canPickUpLoot() {
        return !com.dreamseekers.skill.FireflyRush.active(this)&&super.canPickUpLoot();
    }
    @Override public ResourceCost getCost() { return COST; }
    @Override public int getMaxResources() { return com.dreamseekers.unitprogression.DreamSpecialistEffects.cargoCapacity(this); }
    @Override public int getResourceReturnThreshold(int nativeThreshold) {
        return com.dreamseekers.unitprogression.DreamSpecialistEffects.returnThreshold(this,nativeThreshold);
    }
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

    /** Never call the native capture procedure: it consumes a jar and discards the paid worker. */
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    @Override public List<Button> getAbilityButtons() {
        List<Button> buttons = new ArrayList<>(getAbilities().getButtons(this));
        if (level().isClientSide()) buttons.addAll(Factions.getFaction(this).getBuildingButtons());
        return buttons;
    }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.WORKER_ICON; }
    @Override public int getPortraitYOffset() { return 20; }
    @Override public int getPortraitScaleOffset() { return 10; }
    /** Picking aid only; the actual collision box remains the native half-block body. */
    @Override public AABB getInflatedSelectionBox() { return getBoundingBox().inflate(0.15); }

    @Override public void tick() {
        setCanPickUpLoot(true); // RoN's valued resource-token pickup, never vanilla equipment pickup.
        super.tick();
        if (isAlive()) {
            Unit.tick(this);
            AttackerUnit.tick(this);
            WorkerUnit.tick(this);
            if(!level().isClientSide())rts().skills().tick();
        }
    }
    @Override public EntityDimensions getDimensions(Pose pose) { return getType().getDimensions(); }
    @Override public net.minecraft.world.entity.Entity changeDimension(net.minecraft.server.level.ServerLevel destination,
            net.minecraftforge.common.util.ITeleporter teleporter) {
        return DreamDimensionCargo.changeDimension(this, destination, () -> super.changeDimension(destination, teleporter));
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); writeRtsData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRtsData(tag); // Same RoN ledger-settled cargo policy as the existing worker; never duplicate resource credit.
        setPersistenceRequired();
        setNoGravity(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        // Nominal ground/air movement, health, damage, armour and attack rate match the old worker.
        return DreamRtsUnit.attributes(28, 1, 0.25, 1, 0.5, 0).add(Attributes.FLYING_SPEED, 0.25);
    }
}
