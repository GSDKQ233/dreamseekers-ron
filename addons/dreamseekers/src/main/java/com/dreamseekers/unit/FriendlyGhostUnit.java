// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.shadow.FriendlyGhostBalance;
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
import net.pasterdream.entity.FriendlyGhostEntity;
import javax.annotation.Nullable;

/** Shadow-route ranged contract unit, not a HeroUnit. Native dive/melee/ranged factories
 * are replaced; only native appearance and documented damage immunities remain.
 */
public final class FriendlyGhostUnit extends FriendlyGhostEntity implements TeamSafeCombatUnit, com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit {
    public static final ResourceCost COST = FriendlyGhostBalance.COST;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(FriendlyGhostUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(FriendlyGhostUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(FriendlyGhostUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;

    @SuppressWarnings("unchecked")
    public FriendlyGhostUnit(EntityType<? extends FriendlyGhostEntity> type, Level level) {
        super((EntityType<FriendlyGhostEntity>) type, level);
        rts();
        updateAbilityButtons();
        setNoAi(false);
        setNoGravity(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false, true, false, FriendlyGhostBalance.WINDUP_TICKS);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override protected PathNavigation createNavigation(Level level) {
        return new com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation(this,level);
    }
    @Override public boolean canUsePortal() { return false; }
    @Override public com.solegendary.reignofnether.unit.goals.UsePortalGoal getUsePortalGoal() { return null; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isOnPortalCooldown() { return true; }
    @Override protected void processPortalCooldown() { }
    @Override public void handleInsidePortal(net.minecraft.core.BlockPos pos) { }
    @Override public boolean startRiding(Entity vehicle,boolean force) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    private int fogRevealDuration;
    @Override public int getFogRevealDuration() { return fogRevealDuration; }
    @Override public void setFogRevealDuration(int duration) { fogRevealDuration=Math.max(0,duration); }
    /** Native projectile factory is intentionally never used, even by direct native ranged calls. */
    @Override public void performRangedAttack(LivingEntity target,float factor) { performUnitRangedAttack(target,factor); }
    @Override public void performUnitRangedAttack(LivingEntity target,float ignoredVelocity) {
        if(!(level() instanceof net.minecraft.server.level.ServerLevel server)||!server.getServer().isSameThread()
                ||hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get())
                ||hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())
                ||!canHarm(target)||server.getEntity(getUUID())!=this||server.getEntity(getId())!=this||server.getEntity(target.getUUID())!=target||server.getEntity(target.getId())!=target
                ||distanceTo(target)>getAttackRange()||!hasLineOfSight(target))return;
        com.dreamseekers.shadow.FriendlyGhostBolt bolt=com.dreamseekers.skill.OrdinarySkillHit.constructSporeShot(this,target,()->new com.dreamseekers.shadow.FriendlyGhostBolt(
                DreamseekersMod.FRIENDLY_GHOST_BOLT.get(),server));
        bolt.arm(this,target);
        if(com.dreamseekers.skill.OrdinarySkillHit.spawn(server,bolt,this,target)) {
            // Use RoN's owner-scoped temporary attacker reveal, not a permanent/shared map mark.
            if(target instanceof Unit targetUnit)com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket.revealRangedUnit(targetUnit.getOwnerName(),getId());
            swing(InteractionHand.MAIN_HAND);
        }
    }
    @Override public void updateAbilityButtons() {
        rts().abilities=new com.solegendary.reignofnether.ability.Abilities();
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.FRIENDLY_DEBT_CONTRACT);
    }
    @Override public ResourceCost getCost() { return COST; }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.FRIENDLY_GHOST_ICON; }
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

    // The paid RTS owner/epoch is unrelated to vanilla pet ownership. No advancement,
    // owner-follow, sitting, breeding, free offspring or pet death message can be enabled.
    @Override public void tame(Player player) { }
    @Override public void setTame(boolean tame) { super.setTame(false); }
    @Override public void setOwnerUUID(@Nullable java.util.UUID owner) { super.setOwnerUUID(null); }
    @Override public LivingEntity getOwner() { return null; }
    @Override public boolean isOwnedBy(LivingEntity entity) { return false; }
    @Override public void setOrderedToSit(boolean sit) { super.setOrderedToSit(false); }
    @Override public void setInSittingPose(boolean sit) { super.setInSittingPose(false); }
    @Override public void setAge(int age) { super.setAge(0); }
    @Override public void setInLove(@Nullable Player player) { }
    @Override public void setInLoveTime(int ticks) { super.setInLoveTime(0); }
    @Override public boolean canMate(net.minecraft.world.entity.animal.Animal other) { return false; }
    @Override public net.minecraft.world.entity.AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.AgeableMob other) { return null; }
    @Override public void spawnChildFromBreeding(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.animal.Animal other) { }
    @Override public boolean isFood(net.minecraft.world.item.ItemStack stack) { return false; }

    @Override public void tick() {
        com.dreamseekers.skill.FriendlyDebtContract.tickPursuit(this);
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
        com.dreamseekers.skill.FriendlyDebtContract.tickPursuit(this);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); writeRtsData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        CompoundTag nativeTag=tag.copy();
        for(String key:java.util.List.of("Owner","Sitting","Age","ForcedAge","InLove","LoveCause")) nativeTag.remove(key);
        super.readAdditionalSaveData(nativeTag);
        readRtsData(tag);
        setOwnerUUID(null);setTame(false);setOrderedToSit(false);setInSittingPose(false);setAge(0);resetLove();
        setNoAi(false);
        setNoGravity(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return DreamRtsUnit.attributes(FriendlyGhostBalance.MAX_HEALTH, FriendlyGhostBalance.DAMAGE, FriendlyGhostBalance.SPEED,
                FriendlyGhostBalance.ARMOR, FriendlyGhostBalance.ATTACKS_PER_SECOND, FriendlyGhostBalance.AGGRO)
                .add(Attributes.FLYING_SPEED,FriendlyGhostBalance.SPEED)
                .add(AttributeRegistrar.ATTACK_RANGE.get(),FriendlyGhostBalance.RANGE)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), FriendlyGhostBalance.SIGHT);
    }
}
