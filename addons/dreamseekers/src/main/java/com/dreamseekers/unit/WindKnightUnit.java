// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.windknight.WindKnightBalance;
import com.dreamseekers.skill.WindDash;
import com.solegendary.reignofnether.ability.Abilities;
import net.minecraft.core.BlockPos;
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
import net.pasterdream.entity.WindKnightEntity;
import javax.annotation.Nullable;

/** LIGHT heavy ground melee using the installed native body/renderer.
 * Native delayed unfiltered AOE stays disabled; Wind Dash uses authoritative collision navigation and one melee hit.
 */
public final class WindKnightUnit extends WindKnightEntity implements TeamSafeCombatUnit {
    public static final ResourceCost COST = WindKnightBalance.COST;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(WindKnightUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(WindKnightUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(WindKnightUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;

    @SuppressWarnings("unchecked")
    public WindKnightUnit(EntityType<? extends WindKnightEntity> type, Level level) {
        super((EntityType<WindKnightEntity>) type, level);
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
    @Override protected void registerGoals() {
        rts().register(goalSelector, targetSelector);
        goalSelector.addGoal(0,WindDash.goal(this));
    }
    @Override protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }
    @Override public ResourceCost getCost() { return COST; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.WIND_KNIGHT_ICON; }
    @Override public AABB getInflatedSelectionBox() { return getBoundingBox().inflate(.12); }
    @Override public EntityDimensions getDimensions(Pose pose) { return getType().getDimensions(); }
    @Override public void updateAbilityButtons() {
        rts().abilities=new Abilities();rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.WIND_DASH);
    }
    @Override public boolean isIdle() { return !WindDash.isActive(this)&&TeamSafeCombatUnit.super.isIdle(); }
    @Override public boolean isCasting() { return WindDash.isActive(this)||TeamSafeCombatUnit.super.isCasting(); }
    @Override public void resetBehaviours() {
        TeamSafeCombatUnit.super.resetBehaviours();WindDash.cancel(this);
    }
    @Override public void setMoveTarget(@Nullable BlockPos pos) {
        if(pos!=null)WindDash.commandChanged(this);
        TeamSafeCombatUnit.super.setMoveTarget(pos);
    }
    @Override public void setUnitAttackTargetForced(@Nullable LivingEntity target) {
        if(canHarm(target))WindDash.commandChanged(this);
        TeamSafeCombatUnit.super.setUnitAttackTargetForced(target);
    }
    @Override public void setHoldPosition(boolean hold) {
        if(hold)WindDash.commandChanged(this);
        TeamSafeCombatUnit.super.setHoldPosition(hold);
    }
    @Override public void setAttackMoveTarget(@Nullable BlockPos pos) {
        if(pos!=null)WindDash.commandChanged(this);
        TeamSafeCombatUnit.super.setAttackMoveTarget(pos);
    }
    private boolean canFollow(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level() || !(target instanceof Unit unit)
                || getOwnerName().isBlank() || unit.getOwnerName().isBlank()) return false;
        return getOwnerName().equals(unit.getOwnerName()) || (level().isClientSide()
                ? AlliancesClient.isAllied(getOwnerName(), unit.getOwnerName())
                : AlliancesServerEvents.isAllied(getOwnerName(), unit.getOwnerName()));
    }
    /** Explicit FOLLOW remains friendly; hostile tracking uses the filtered combat target. */
    @Override public void setFollowTarget(@Nullable LivingEntity target) {
        if(canFollow(target))WindDash.commandChanged(this);
        rts().follow=canFollow(target)?target:null;
    }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public void setTarget(@Nullable LivingEntity target) { super.setTarget(canHarm(target) ? target : null); }
    @Override public boolean doHurtTarget(Entity target) {
        return !level().isClientSide()&&canHarm(target)&&WindDash.allowMelee(this,target)&&super.doHurtTarget(target);
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override protected void customServerAiStep() { }
    @Override protected void pickUpItem(ItemEntity item) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) { }
    @Override public int getExperienceReward() { return 0; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    /** Paid heavy infantry cannot become a native reward or interaction source. */
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    @Override public void tick() {
        setCanPickUpLoot(false);
        WindDash.beforeTick(this);
        clearFire(); // Preserve native fire immunity without invoking its mixed AOE procedure.
        ((TeamSafeSelectedTargetGoal)getTargetGoal()).invalidateChangedRelationship();
        super.tick();
        if (getFollowTarget() != null && !canFollow(getFollowTarget())) {
            rts().follow = null;
            getMoveGoal().stopMoving();
        }
        if (isAlive()) {
            Unit.tick(this);
            // Ordinary cooldown keeps ticking, but automatic targeting/building ticks may not seize dash navigation.
            if(WindDash.isActive(this))((TeamSafeMeleeAttackGoal)getAttackGoal()).tickAttackCooldown();
            else com.solegendary.reignofnether.unit.interfaces.AttackerUnit.tick(this);
        }
        WindDash.afterTick(this);
        rts().skills().tick();
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
        return DreamRtsUnit.attributes(WindKnightBalance.MAX_HEALTH, WindKnightBalance.DAMAGE, WindKnightBalance.SPEED,
                WindKnightBalance.ARMOR, WindKnightBalance.ATTACKS_PER_SECOND, WindKnightBalance.AGGRO)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), WindKnightBalance.SIGHT)
                .add(Attributes.KNOCKBACK_RESISTANCE, WindKnightBalance.KNOCKBACK_RESISTANCE);
    }
}
