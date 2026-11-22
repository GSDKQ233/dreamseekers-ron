// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.skill.StoneHeavyShot;
import com.dreamseekers.stone.StoneThrowerBalance;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
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
import net.pasterdream.entity.SmallStoneSpiritEntity;
import javax.annotation.Nullable;

/** New public ground thrower ID on the native small stone parent; never replaces the legacy worker.
 * Original death growth/terrain resurrection is isolated on both source and query-target sides.
 */
public final class StoneSpiritThrowerUnit extends SmallStoneSpiritEntity implements TeamSafeCombatUnit, com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit, net.minecraft.world.entity.monster.RangedAttackMob {
    public static final ResourceCost COST = StoneThrowerBalance.COST;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(StoneSpiritThrowerUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(StoneSpiritThrowerUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(StoneSpiritThrowerUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;
    private com.dreamseekers.stone.StoneSiegeGoal siegeGoal;
    private com.solegendary.reignofnether.unit.goals.GarrisonGoal garrisonGoal;

    @SuppressWarnings("unchecked")
    public StoneSpiritThrowerUnit(EntityType<? extends SmallStoneSpiritEntity> type, Level level) {
        super((EntityType<SmallStoneSpiritEntity>) type, level);
        rts();
        updateAbilityButtons();
        setNoAi(false);
        setNoGravity(false);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false, false, false, StoneThrowerBalance.WINDUP_TICKS);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() {
        rts().register(goalSelector, targetSelector);
        goalSelector.addGoal(0,StoneHeavyShot.goal(this));
        goalSelector.addGoal(2,getGarrisonGoal());
    }
    /** Lazy because vanilla calls registerGoals during super construction; never reset the registered goal afterward. */
    @Override public com.solegendary.reignofnether.unit.goals.GarrisonGoal getGarrisonGoal() {
        if(garrisonGoal==null)garrisonGoal=new com.solegendary.reignofnether.unit.goals.GarrisonGoal(this);
        return garrisonGoal;
    }
    @Override public boolean canGarrison() { return true; }
    private com.dreamseekers.stone.StoneSiegeGoal siegeGoal() {
        if(siegeGoal==null)siegeGoal=new com.dreamseekers.stone.StoneSiegeGoal(this);
        return siegeGoal;
    }
    @Override @Nullable public com.dreamseekers.stone.StoneSiegeGoal getAttackBuildingGoal() {
        return canAttackBuildings()?siegeGoal():null;
    }
    @Override public boolean canAttackBuildings() { return com.dreamseekers.stone.StoneSiegeGoal.researched(this); }
    @Override public void setAttackBuildingTarget(@Nullable BlockPos pos,boolean forced) {
        if(pos!=null)StoneHeavyShot.commandChanged(this);
        var goal=siegeGoal();goal.setBuildingTarget(pos);
        goal.forced=forced&&goal.getBuildingTarget()!=null;
    }
    @Override public void updateAbilityButtons() {
        rts().abilities=new com.solegendary.reignofnether.ability.Abilities();
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.STONE_HEAVY_SHOT);
    }
    @Override public boolean isIdle() { return !StoneHeavyShot.active(this)&&TeamSafeCombatUnit.super.isIdle(); }
    @Override public boolean isCasting() { return StoneHeavyShot.active(this)||TeamSafeCombatUnit.super.isCasting(); }
    @Override public boolean getHoldPosition() { return StoneHeavyShot.active(this)||isGarrisoned()||TeamSafeCombatUnit.super.getHoldPosition(); }
    @Override public boolean permitsAutomaticAnchorReturn() {
        return !StoneHeavyShot.active(this)&&!isGarrisoned()
            &&getGarrisonGoal().getBuildingTarget()==null&&TeamSafeCombatUnit.super.permitsAutomaticAnchorReturn();
    }
    @Override public void resetBehaviours() {
        StoneHeavyShot.commandChanged(this);TeamSafeCombatUnit.super.resetBehaviours();
    }
    @Override public void setMoveTarget(@Nullable net.minecraft.core.BlockPos target) {
        if(target!=null)StoneHeavyShot.commandChanged(this);
        TeamSafeCombatUnit.super.setMoveTarget(target);
    }
    @Override public void setUnitAttackTargetForced(@Nullable LivingEntity target) {
        if(canHarm(target))StoneHeavyShot.commandChanged(this);
        TeamSafeCombatUnit.super.setUnitAttackTargetForced(target);
    }
    @Override public void setAttackMoveTarget(@Nullable net.minecraft.core.BlockPos target) {
        if(target!=null)StoneHeavyShot.commandChanged(this);
        TeamSafeCombatUnit.super.setAttackMoveTarget(target);
    }
    @Override public void setHoldPosition(boolean hold) {
        if(hold)StoneHeavyShot.commandChanged(this);
        TeamSafeCombatUnit.super.setHoldPosition(hold);
    }
    @Override protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this,level);
    }
    @Override public boolean canUsePortal() { return false; }
    // RoN canUsePortal alone does not gate vanilla same-dimension End Gateway relocation.
    @Override public boolean isOnPortalCooldown() { return true; }
    // Prevent the vanilla decrement from driving a permanent ineligibility sentinel below zero.
    @Override protected void processPortalCooldown() { }
    // Gateway uses the root vehicle; unsupported riding must not bypass this subtype's guard.
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos) { }
    @Override public com.solegendary.reignofnether.unit.goals.UsePortalGoal getUsePortalGoal() { return null; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    private int fogRevealDuration;
    @Override public int getFogRevealDuration() { return fogRevealDuration; }
    @Override public void setFogRevealDuration(int duration) { fogRevealDuration=Math.max(0,duration); }
    /** Original stone spirit has no ranged attack: this is an explicit addon physical stone throw. */
    @Override public void performRangedAttack(LivingEntity target,float factor) { performUnitRangedAttack(target,factor); }
    @Override public void performUnitRangedAttack(LivingEntity target,float ignoredVelocity) {
        shoot(target,false);
    }
    /** The native goal has finished its one windup. A ready heavy shot can replace this release only. */
    public void releaseGoalShot(LivingEntity target,float ignoredVelocity) {
        shoot(target,true);
    }
    private void shoot(LivingEntity target,boolean fromInstalledGoal) {
        if(!StoneHeavyShot.allowOrdinaryShot(this)
                ||!(level() instanceof net.minecraft.server.level.ServerLevel server)||!server.getServer().isSameThread()
                ||hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get())
                ||hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())
                ||!canHarm(target)||server.getEntity(getUUID())!=this||server.getEntity(target.getUUID())!=target
                ||distanceTo(target)>ordinaryShotRange(target)||!hasLineOfSight(target))return;
        if(fromInstalledGoal&&StoneHeavyShot.replaceNaturalShot(this,target))return;
        com.dreamseekers.stone.StoneProjectile bolt=com.dreamseekers.skill.OrdinarySkillHit.constructSporeShot(this,target,()->new com.dreamseekers.stone.StoneProjectile(
                DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),server));
        bolt.arm(this,target);
        if(com.dreamseekers.skill.OrdinarySkillHit.spawn(server,bolt,this,target)) {
            // Use RoN's owner-scoped temporary attacker reveal, not a permanent/shared map mark.
            if(target instanceof Unit targetUnit)com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket.revealRangedUnit(targetUnit.getOwnerName(),getId());
            swing(InteractionHand.MAIN_HAND);
        }
    }
    /** The ordinary goal and actual projectile gate read the same live, owner-scoped research. */
    @Override public float getAttackRange() {
        return getSiegeAttackRange()+StoneHeavyShot.range(this)-StoneHeavyShot.RANGE;
    }
    /** Ordinary unit-target research never increases the separate building-siege reach. */
    public float getSiegeAttackRange() {
        var attribute=getAttribute(AttributeRegistrar.ATTACK_RANGE.get());
        return (float)(attribute==null?StoneThrowerBalance.RANGE:attribute.getValue());
    }
    /** Retain existing garrison reach and target-garrison external bonus rules. */
    private float ordinaryShotRange(LivingEntity target) {
        var own=getGarrison();
        if(own!=null)return own.getBuilding().getActiveAddon(com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon.class).getAttackRange();
        float range=getAttackRange();
        if(target instanceof Unit unit) {
            var theirs=unit.getGarrison();
            if(theirs!=null)range+=theirs.getBuilding().getActiveAddon(com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon.class).getExternalAttackRangeBonus();
        }
        return range;
    }
    @Override public ResourceCost getCost() { return COST; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.STONE_SPIRIT_THROWER_ICON; }
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
    @Override public void setFollowTarget(@Nullable LivingEntity target) {
        if(canFollow(target))StoneHeavyShot.commandChanged(this);
        rts().follow=canFollow(target)?target:null;
    }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public void setTarget(@Nullable LivingEntity target) { super.setTarget(canHarm(target) ? target : null); }
    @Override public boolean doHurtTarget(Entity target) { return false; /* Ranged-only: no inherited melee fallback. */ }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void customServerAiStep() { }
    @Override protected void pickUpItem(ItemEntity item) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) { }
    @Override public int getExperienceReward() { return 0; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    /** Paid ranged unit has no native item/quest interaction or free resource source. */
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    @Override public void tick() {
        getPersistentData().putDouble("size",0);
        setCanPickUpLoot(false);
        if(siegeGoal!=null)siegeGoal.invalidate();
        StoneHeavyShot.beforeTick(this);
        ((TeamSafeSelectedTargetGoal)getTargetGoal()).invalidateChangedRelationship();
        super.tick();
        if (getFollowTarget() != null && !canFollow(getFollowTarget())) {
            rts().follow = null;
            getMoveGoal().stopMoving();
        }
        if (isAlive()) {
            Unit.tick(this);
            if(StoneHeavyShot.active(this))
                ((com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal<?>)getAttackGoal()).tickAttackCooldown();
            else com.solegendary.reignofnether.unit.interfaces.AttackerUnit.tick(this);
            if(!level().isClientSide())rts().skills().tick();
        }
        StoneHeavyShot.afterTick(this);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); writeRtsData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRtsData(tag);
        getPersistentData().putDouble("size",0);
        setNoAi(false);
        setNoGravity(false);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return DreamRtsUnit.attributes(StoneThrowerBalance.MAX_HEALTH, StoneThrowerBalance.DAMAGE, StoneThrowerBalance.SPEED,
                StoneThrowerBalance.ARMOR, StoneThrowerBalance.ATTACKS_PER_SECOND, StoneThrowerBalance.AGGRO)
                .add(AttributeRegistrar.ATTACK_RANGE.get(),StoneThrowerBalance.RANGE)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), StoneThrowerBalance.SIGHT);
    }
}
