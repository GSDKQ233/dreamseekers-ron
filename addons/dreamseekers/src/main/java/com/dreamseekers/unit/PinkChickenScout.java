// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.scout.ScoutBalance;
import com.dreamseekers.scout.ScoutMarkAbility;
import com.dreamseekers.scout.ScoutMarkState;
import com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.goals.UsePortalGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.pasterdream.entity.PinkChickenEntity;
import javax.annotation.Nullable;

/**
 * Paid noncombat scout on the original model/renderer and native FlyingMoveControl.
 * Only the new addon entity has RTS goals; wild PasterDream chickens stay unchanged.
 * Native passive eggs are blocked at their entity-specific call site by PinkChickenEggMixin.
 */
public final class PinkChickenScout extends PinkChickenEntity implements DreamScoutUnit,
        com.solegendary.reignofnether.api.HealthWriteObservers.Observed {
    public static final ResourceCost COST = ScoutBalance.COST;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(PinkChickenScout.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(PinkChickenScout.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(PinkChickenScout.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;
    private ScoutMarkState mark;
    private com.dreamseekers.skill.LightFeatherAlert.State lightFeatherAlert;

    @SuppressWarnings("unchecked")
    public PinkChickenScout(EntityType<? extends PinkChickenEntity> type, Level level) {
        super((EntityType<PinkChickenEntity>) type, level);
        rts();
        updateAbilityButtons();
        setPersistenceRequired();
        setNoGravity(true);
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false, true, true);
        return state;
    }
    public ScoutMarkState scoutMark() {
        if (mark == null) mark = new ScoutMarkState(this);
        return mark;
    }
    public com.dreamseekers.skill.LightFeatherAlert.State lightFeatherAlert() {
        if(lightFeatherAlert==null)lightFeatherAlert=new com.dreamseekers.skill.LightFeatherAlert.State(this);
        return lightFeatherAlert;
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide())return super.hurt(source,amount);
        try(var frame=lightFeatherAlert().enter(source)) { return super.hurt(source,amount); }
    }
    @Override public com.solegendary.reignofnether.api.HealthWriteObservers.Observer captureHealthWrite(DamageSource source) {
        return lightFeatherAlert().captureWrite(source);
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override protected PathNavigation createNavigation(Level level) { return new LowAltitudeFlyingNavigation(this, level); }
    @Override public ResourceCost getCost() { return COST; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.PINK_CHICKEN_ICON; }
    @Override public AABB getInflatedSelectionBox() { return getBoundingBox().inflate(.15); }
    @Override public EntityDimensions getDimensions(Pose pose) { return getType().getDimensions(); }
    @Override public void updateAbilityButtons() {
        rts().abilities = new Abilities();
        rts().abilities.add(ScoutMarkAbility.INSTANCE);
    }
    // The scout has no gathering, construction, garrison or portal duties.
    @Override public boolean canUsePortal() { return false; }
    @Override public UsePortalGoal getUsePortalGoal() { return null; }
    private boolean canFollow(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level() || !(target instanceof Unit unit)
                || getOwnerName().isBlank() || unit.getOwnerName().isBlank()) return false;
        return getOwnerName().equals(unit.getOwnerName()) || (level().isClientSide()
                ? AlliancesClient.isAllied(getOwnerName(), unit.getOwnerName())
                : AlliancesServerEvents.isAllied(getOwnerName(), unit.getOwnerName()));
    }
    /** Core noncombat ATTACK falls back to FOLLOW; never let that track an enemy through fog. */
    @Override public void setFollowTarget(@Nullable LivingEntity target) { rts().follow = canFollow(target) ? target : null; }
    @Override public LivingEntity getTarget() { return null; }
    @Override public void setTarget(@Nullable LivingEntity target) { super.setTarget(null); }
    @Override public boolean doHurtTarget(Entity target) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void customServerAiStep() { }
    @Override protected void pickUpItem(ItemEntity item) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) { }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public int getExperienceReward() { return 0; }
    @Override public void setInLoveTime(int ticks) { super.setInLoveTime(0); }
    @Override public boolean canMate(Animal other) { return false; }
    @Override public boolean canFallInLove() { return false; }
    @Override public void setInLove(@Nullable Player player) { resetLove(); }
    @Override public void spawnChildFromBreeding(ServerLevel level, Animal other) { resetLove(); }
    @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return null; }
    @Override public boolean canBeLeashed(Player player) { return false; }

    @Override public void tick() {
        setCanPickUpLoot(false);
        super.tick();
        if (getFollowTarget() != null && !canFollow(getFollowTarget())) {
            rts().follow = null;
            getMoveGoal().stopMoving();
        }
        if (isAlive()) Unit.tick(this);
        if (!level().isClientSide()) scoutMark().tick();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeRtsData(tag);
        scoutMark().save(tag);
        lightFeatherAlert().save(tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRtsData(tag);
        scoutMark().load(tag);
        lightFeatherAlert().load(tag);
        setNoAi(false);
        setAge(0);
        resetLove();
        setPersistenceRequired();
        setNoGravity(true);
        setCanPickUpLoot(false);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MAX_HEALTH, ScoutBalance.MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, ScoutBalance.SPEED)
                .add(Attributes.FLYING_SPEED, ScoutBalance.SPEED)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), ScoutBalance.SIGHT)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), 0);
    }
}
