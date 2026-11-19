// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.shadow.TerrorbeakBalance;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.pasterdream.entity.TerrorbeakEntity;
import javax.annotation.Nullable;

/** Paid ground shadow melee foundation. Native SAN/contact/roar/rewards remain isolated; only approved Dread Roar/Predatory Instinct are enabled. */
public final class TerrorbeakUnit extends TerrorbeakEntity implements TeamSafeCombatUnit {
    public static final ResourceCost COST = TerrorbeakBalance.COST;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(TerrorbeakUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(TerrorbeakUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(TerrorbeakUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;

    @SuppressWarnings("unchecked")
    public TerrorbeakUnit(EntityType<? extends TerrorbeakEntity> type, Level level) {
        super((EntityType<TerrorbeakEntity>) type, level);
        rts();
        updateAbilityButtons();
        setNoAi(false);
        setNoGravity(false);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false, false, false);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }
    @Override public void updateAbilityButtons() {
        rts().abilities=new com.solegendary.reignofnether.ability.Abilities();
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.TERRORBEAK_DREAD_ROAR);
        rts().abilities.add(com.dreamseekers.shadow.TerrorbeakUpgradeAbility.INSTANCE);
    }
    @Override public void setSpeed(float speed) {
        super.setSpeed(speed);
        // Paid dry-ground subtype only: native MOVE_TO otherwise consumes temporary MS twice.
        if(onGround()&&!isInWater()&&!isInLava()) {
            var attribute=getAttribute(Attributes.MOVEMENT_SPEED);
            var modifier=attribute==null?null:attribute.getModifier(com.dreamseekers.skill.BuffKind.MOVEMENT_SPEED.modifierId);
            double bonus=modifier!=null&&modifier.getOperation()==net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL
                    ?modifier.getAmount():0;
            if(Double.isFinite(bonus)&&bonus>0)setZza((float)(zza/(1+bonus)));
        }
    }
    @Override public boolean canHarmOwner(String owner) {
        return !com.dreamseekers.shadow.TerrorbeakUpgradeService.busy(this) && TeamSafeCombatUnit.super.canHarmOwner(owner);
    }
    @Override public ResourceCost getCost() { return COST; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.TERRORBEAK_ICON; }
    // Fit the native model inside the RTS portrait; world rendering is unchanged.
    @Override public int getPortraitYOffset() { return -161; }
    @Override public int getPortraitScaleOffset() { return -38; }
    @Override public AABB getInflatedSelectionBox() { return getBoundingBox().inflate(.12); }
    @Override public EntityDimensions getDimensions(Pose pose) { return getType().getDimensions(); }
    private boolean canFollow(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level() || !(target instanceof Unit unit)
                || getOwnerName().isBlank() || unit.getOwnerName().isBlank()) return false;
        return getOwnerName().equals(unit.getOwnerName()) || (level().isClientSide()
                ? AlliancesClient.isAllied(getOwnerName(), unit.getOwnerName())
                : AlliancesServerEvents.isAllied(getOwnerName(), unit.getOwnerName()));
    }
    /** Explicit FOLLOW remains friendly; hostile tracking uses the filtered combat target. */
    @Override public void setFollowTarget(@Nullable LivingEntity target) { rts().follow = canFollow(target) ? target : null; }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public void setTarget(@Nullable LivingEntity target) { super.setTarget(canHarm(target) ? target : null); }
    @Override public boolean doHurtTarget(Entity target) { return level() instanceof net.minecraft.server.level.ServerLevel server && server.getServer().isSameThread()
            && !hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get())
            && !hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())
            && canHarm(target) && server.getEntity(getId())==this && server.getEntity(getUUID())==this
            && server.getEntity(target.getId())==target && server.getEntity(target.getUUID())==target
            && hasLineOfSight(target) && super.doHurtTarget(target); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean canUsePortal() { return false; }
    @Override public com.solegendary.reignofnether.unit.goals.UsePortalGoal getUsePortalGoal() { return null; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isOnPortalCooldown() { return true; }
    @Override protected void processPortalCooldown() { }
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos) { }
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    @Override protected void pickUpItem(ItemEntity item) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) { }
    @Override public int getExperienceReward() { return 0; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    /** Paid unit cannot become a native reward or interaction source. */
    @Override public void playerTouch(Player player) { }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    @Override public void tick() {
        if(com.dreamseekers.shadow.TerrorbeakUpgradeService.busy(this))return;
        com.dreamseekers.skill.TerrorbeakDreadRoar.tickPredatory(this);
        setCanPickUpLoot(false);
        ((TeamSafeSelectedTargetGoal)getTargetGoal()).invalidateChangedRelationship();
        super.tick();
        if (getFollowTarget() != null && !canFollow(getFollowTarget())) {
            rts().follow = null;
            getMoveGoal().stopMoving();
        }
        if (isAlive()) {
            Unit.tick(this);
            com.solegendary.reignofnether.unit.interfaces.AttackerUnit.tick(this);
            if(!level().isClientSide())rts().skills().tick();
        }
        com.dreamseekers.skill.TerrorbeakDreadRoar.tickPredatory(this);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); writeRtsData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRtsData(tag);
        setNoAi(false);
        setNoGravity(false);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return DreamRtsUnit.attributes(TerrorbeakBalance.MAX_HEALTH, TerrorbeakBalance.DAMAGE, TerrorbeakBalance.SPEED,
                TerrorbeakBalance.ARMOR, TerrorbeakBalance.ATTACKS_PER_SECOND, TerrorbeakBalance.AGGRO)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), TerrorbeakBalance.SIGHT)
                .add(Attributes.KNOCKBACK_RESISTANCE, TerrorbeakBalance.KNOCKBACK_RESISTANCE);
    }
}
