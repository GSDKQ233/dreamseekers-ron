// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unitprogression.UnitBalanceMigration;
import com.dreamseekers.skill.AaroncosCombat;
import com.dreamseekers.aaroncos.AaroncosBalance;
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
import net.pasterdream.entity.AaroncosLefthand0Entity;
import javax.annotation.Nullable;

/** Independent high-resource ultimate-unit foundation, not a map-only boss.
 * Native boss overlays, spawn/death blasts, spells, summons and arena participation are isolated.
 * RTS bonds are separate from the isolated native boss pairing and arena lifecycle.
 */
public final class AaroncosLeftHandUnit extends AaroncosLefthand0Entity implements com.dreamseekers.aaroncos.ControlledAaroncosHand {
    public static final ResourceCost COST = AaroncosBalance.newTrainingCost();
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(AaroncosLeftHandUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(AaroncosLeftHandUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(AaroncosLeftHandUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;
    private UnitBalanceMigration.State balanceState=UnitBalanceMigration.fresh(UnitBalanceMigration.Profile.LEFT);

    @SuppressWarnings("unchecked")
    public AaroncosLeftHandUnit(EntityType<? extends AaroncosLefthand0Entity> type, Level level) {
        super((EntityType<AaroncosLefthand0Entity>) type, level);
        rts();
        updateAbilityButtons();
        setNoAi(false);
        setNoGravity(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this,false,true,false,-1,com.dreamseekers.aaroncos.AaroncosBuildingAttackGoal::new);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override public void setOwnerName(String name) {
        com.dreamseekers.aaroncos.HandBondService.ownerChanging(this,name);
        com.dreamseekers.aaroncos.ControlledAaroncosHand.super.setOwnerName(name);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector);goalSelector.addGoal(0,AaroncosCombat.goal(this)); }
    @Override public void updateAbilityButtons() {
        rts().abilities=new com.solegendary.reignofnether.ability.Abilities();rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.AARONCOS_LEFT_PRESS);rts().abilities.add(com.dreamseekers.aaroncos.HandBondAbility.INSTANCE);
    }
    @Override public boolean isIdle() {return !AaroncosCombat.active(this)&&com.dreamseekers.aaroncos.ControlledAaroncosHand.super.isIdle();}
    @Override public boolean isCasting() {return AaroncosCombat.active(this)||com.dreamseekers.aaroncos.ControlledAaroncosHand.super.isCasting();}
    @Override public boolean getHoldPosition() {return AaroncosCombat.active(this)||com.dreamseekers.aaroncos.ControlledAaroncosHand.super.getHoldPosition();}
    @Override public boolean permitsAutomaticAnchorReturn() {return !AaroncosCombat.active(this)&&com.dreamseekers.aaroncos.ControlledAaroncosHand.super.permitsAutomaticAnchorReturn();}
    @Override public void resetBehaviours() {AaroncosCombat.commandChanged(this);com.dreamseekers.aaroncos.ControlledAaroncosHand.super.resetBehaviours();}
    @Override public void setMoveTarget(@Nullable net.minecraft.core.BlockPos p) {if(p!=null)AaroncosCombat.commandChanged(this);com.dreamseekers.aaroncos.ControlledAaroncosHand.super.setMoveTarget(p);}
    @Override public void setUnitAttackTargetForced(@Nullable LivingEntity t) {if(canHarm(t))AaroncosCombat.commandChanged(this);com.dreamseekers.aaroncos.ControlledAaroncosHand.super.setUnitAttackTargetForced(t);}
    @Override public void setAttackMoveTarget(@Nullable net.minecraft.core.BlockPos p) {if(p!=null)AaroncosCombat.commandChanged(this);com.dreamseekers.aaroncos.ControlledAaroncosHand.super.setAttackMoveTarget(p);}
    @Override public void setHoldPosition(boolean hold) {if(hold)AaroncosCombat.commandChanged(this);com.dreamseekers.aaroncos.ControlledAaroncosHand.super.setHoldPosition(hold);}
    @Override protected PathNavigation createNavigation(Level level) {
        return new com.dreamseekers.aaroncos.AaroncosHandNavigation(this,level);
    }
    @Override public com.dreamseekers.aaroncos.ControlledAaroncosHand.Side handSide() {return com.dreamseekers.aaroncos.ControlledAaroncosHand.Side.LEFT;}
    @Override public ResourceCost getCost() { return COST; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.AARONCOS_LEFT_HAND_ICON; }
    // Fit the native model inside the RTS portrait; world rendering is unchanged.
    @Override public int getPortraitYOffset() { return -135; }
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
    @Override public void setFollowTarget(@Nullable LivingEntity target) { if(canFollow(target))AaroncosCombat.commandChanged(this);rts().follow = canFollow(target) ? target : null; }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public void setTarget(@Nullable LivingEntity target) { super.setTarget(canHarm(target) ? target : null); }
    @Override public boolean doHurtTarget(Entity target) { return level() instanceof net.minecraft.server.level.ServerLevel server && server.getServer().isSameThread()
            && !AaroncosCombat.active(this)
            && !hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get())
            && !hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())
            && canHarm(target) && server.getEntity(getUUID())==this && server.getEntity(getId())==this && server.getEntity(target.getUUID())==target && server.getEntity(target.getId())==target
            && hasLineOfSight(target) && super.doHurtTarget(target); }
    @Override public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) { /* No native private ServerBossEvent publication. */ }
    @Override public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) { }
    @Override public void customServerAiStep() { /* Native parent only adds boss progress after empty Mob hook. */ }
    @Override public void playerTouch(Player player) { /* No native contact SAN. */ }
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
    /** Controlled ultimate foundation cannot become a native reward or interaction source. */
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    @Override public void tick() {
        AaroncosCombat.beforeTick(this);
        setCanPickUpLoot(false);
        ((TeamSafeSelectedTargetGoal)getTargetGoal()).invalidateChangedRelationship();
        super.tick();
        if (getFollowTarget() != null && !canFollow(getFollowTarget())) {
            rts().follow = null;
            getMoveGoal().stopMoving();
        }
        if (isAlive()) {
            Unit.tick(this);
            if(AaroncosCombat.active(this)) {
                if(getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal melee)melee.tickAttackCooldown();
            } else com.solegendary.reignofnether.unit.interfaces.AttackerUnit.tick(this);
            if(!level().isClientSide())rts().skills().tick();
        }
        AaroncosCombat.afterTick(this);
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
        setNoGravity(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
        balanceState=UnitBalanceMigration.afterRtsLoad(this,tag,UnitBalanceMigration.Profile.LEFT);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return DreamRtsUnit.attributes(AaroncosBalance.LEFT_MAX_HEALTH, AaroncosBalance.LEFT_DAMAGE, AaroncosBalance.SPEED,
                AaroncosBalance.LEFT_ARMOR, AaroncosBalance.ATTACKS_PER_SECOND, AaroncosBalance.AGGRO)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), AaroncosBalance.SIGHT)
                .add(Attributes.FLYING_SPEED,AaroncosBalance.SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE,AaroncosBalance.KNOCKBACK_RESISTANCE);
    }
}
