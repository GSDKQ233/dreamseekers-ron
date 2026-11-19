// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unitprogression.UnitBalanceMigration;
import com.dreamseekers.beetlemother.BeetleMotherBalance;
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
import net.pasterdream.entity.BlackBeetleMotherEntity;
import javax.annotation.Nullable;

/** High-tier RTS melee unit with bounded brood summoning.
 * This is not a HeroUnit: hero XP, mana, revival and hero caps are not provided.
 * Native free summons, area buffs and Boss HUD are isolated; recruitment uses explicit resource/population costs.
 */
public final class BeetleMotherUnit extends BlackBeetleMotherEntity implements TeamSafeCombatUnit {
    public static final ResourceCost COST = BeetleMotherBalance.COST;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(BeetleMotherUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(BeetleMotherUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(BeetleMotherUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;
    private UnitBalanceMigration.State balanceState=UnitBalanceMigration.fresh(UnitBalanceMigration.Profile.MOTHER);

    @SuppressWarnings("unchecked")
    public BeetleMotherUnit(EntityType<? extends BlackBeetleMotherEntity> type, Level level) {
        super((EntityType<BlackBeetleMotherEntity>) type, level);
        rts();
        setNoAi(false);
        setNoGravity(false);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
        updateAbilityButtons();
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false, false, false);
        return state;
    }
    @Override public void updateAbilityButtons() {
        rts().abilities = new com.solegendary.reignofnether.ability.Abilities();
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.MOTHER_BROOD);
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.MOTHER_NEST);
    }
    @Override public boolean isIdle(){return !com.dreamseekers.skill.MotherNestStance.active(this)&&TeamSafeCombatUnit.super.isIdle();}
    @Override public boolean permitsAutomaticAnchorReturn(){return !com.dreamseekers.skill.MotherNestStance.active(this)&&TeamSafeCombatUnit.super.permitsAutomaticAnchorReturn();}
    @Override public boolean getHoldPosition(){return com.dreamseekers.skill.MotherNestStance.active(this)||TeamSafeCombatUnit.super.getHoldPosition();}
    @Override public void resetBehaviours(){TeamSafeCombatUnit.super.resetBehaviours();com.dreamseekers.skill.MotherNestStance.commandChanged(this);}
    @Override public void setMoveTarget(net.minecraft.core.BlockPos target){if(target!=null)com.dreamseekers.skill.MotherNestStance.commandChanged(this);TeamSafeCombatUnit.super.setMoveTarget(target);}
    @Override public void setAttackMoveTarget(net.minecraft.core.BlockPos target){if(target!=null)com.dreamseekers.skill.MotherNestStance.commandChanged(this);TeamSafeCombatUnit.super.setAttackMoveTarget(target);}
    @Override public void setUnitAttackTargetForced(LivingEntity target){if(target!=null)com.dreamseekers.skill.MotherNestStance.commandChanged(this);TeamSafeCombatUnit.super.setUnitAttackTargetForced(target);}
    @Override public void setAttackBuildingTarget(net.minecraft.core.BlockPos target,boolean forced){if(target!=null&&forced)com.dreamseekers.skill.MotherNestStance.commandChanged(this);TeamSafeCombatUnit.super.setAttackBuildingTarget(target,forced);}
    @Override public void setHoldPosition(boolean hold){if(hold)com.dreamseekers.skill.MotherNestStance.commandChanged(this);TeamSafeCombatUnit.super.setHoldPosition(hold);}
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new com.dreamseekers.beetlemother.BeetleMotherGroundNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }
    @Override public ResourceCost getCost() { return COST; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.BEETLE_MOTHER_ICON; }
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
    @Override public void setFollowTarget(@Nullable LivingEntity target) { if(target!=null)com.dreamseekers.skill.MotherNestStance.commandChanged(this);rts().follow = canFollow(target) ? target : null; }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public void setTarget(@Nullable LivingEntity target) { super.setTarget(canHarm(target) ? target : null); }
    @Override public boolean doHurtTarget(Entity target) { return !level().isClientSide() && canHarm(target) && com.dreamseekers.skill.MotherNestStance.permitsMelee(this,target) && super.doHurtTarget(target); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public void customServerAiStep() { }
    @Override public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) { }
    @Override public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) { }
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
    /** Paid mother foundation cannot become a native reward or interaction source. */
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    @Override public void tick() {
        setCanPickUpLoot(false);
        ((TeamSafeSelectedTargetGoal)getTargetGoal()).invalidateChangedRelationship();
        if(!level().isClientSide())com.dreamseekers.skill.MotherNestStance.tickCombat(this);
        super.tick();
        if (getFollowTarget() != null && !canFollow(getFollowTarget())) {
            rts().follow = null;
            getMoveGoal().stopMoving();
        }
        if (isAlive()) {
            Unit.tick(this);
            com.solegendary.reignofnether.unit.interfaces.AttackerUnit.tick(this);
            if (!level().isClientSide()) rts().skills().tick();
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeRtsData(tag);
        balanceState.writeTo(tag);
    }
    /** Diagnostic only: an unsupported/custom archive is not silently reported as upgraded. */
    public UnitBalanceMigration.Result balanceMigrationResult() { return balanceState.result(); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRtsData(tag);
        setNoAi(false);
        setNoGravity(false);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
        balanceState=UnitBalanceMigration.afterRtsLoad(this,tag,UnitBalanceMigration.Profile.MOTHER);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return DreamRtsUnit.attributes(BeetleMotherBalance.MAX_HEALTH, BeetleMotherBalance.DAMAGE, BeetleMotherBalance.SPEED,
                BeetleMotherBalance.ARMOR, BeetleMotherBalance.ATTACKS_PER_SECOND, BeetleMotherBalance.AGGRO)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), BeetleMotherBalance.SIGHT)
                .add(Attributes.KNOCKBACK_RESISTANCE, BeetleMotherBalance.KNOCKBACK_RESISTANCE);
    }
}
