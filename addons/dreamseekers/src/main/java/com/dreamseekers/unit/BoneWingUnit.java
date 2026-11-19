// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.bonewing.BoneWingBalance;
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
import net.pasterdream.entity.BoneWingEntity;
import javax.annotation.Nullable;

/** LIGHT airborne ranged unit with owner-scoped Coordinated Mark; native effects stay disabled.
 * Native random impulse is isolated by a paid-subclass-only procedure mixin.
 */
public final class BoneWingUnit extends BoneWingEntity implements TeamSafeCombatUnit, com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit {
    public static final ResourceCost COST = BoneWingBalance.COST;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(BoneWingUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(BoneWingUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(BoneWingUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;

    @SuppressWarnings("unchecked")
    public BoneWingUnit(EntityType<? extends BoneWingEntity> type, Level level) {
        super((EntityType<BoneWingEntity>) type, level);
        rts();
        setNoAi(false);
        setNoGravity(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false, true, false, BoneWingBalance.WINDUP_TICKS);
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
                ||!canHarm(target)||server.getEntity(getUUID())!=this||server.getEntity(target.getUUID())!=target
                ||distanceTo(target)>getAttackRange()||!hasLineOfSight(target))return;
        com.dreamseekers.bonewing.BoneWingBolt bolt=com.dreamseekers.skill.OrdinarySkillHit.constructSporeShot(this,target,()->new com.dreamseekers.bonewing.BoneWingBolt(
                DreamseekersMod.BONE_WING_BOLT.get(),server));
        bolt.arm(this,target);
        if(com.dreamseekers.skill.OrdinarySkillHit.spawn(server,bolt,this,target)) {
            // Use RoN's owner-scoped temporary attacker reveal, not a permanent/shared map mark.
            if(target instanceof Unit targetUnit)com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket.revealRangedUnit(targetUnit.getOwnerName(),getId());
            swing(InteractionHand.MAIN_HAND);
        }
    }
    @Override public ResourceCost getCost() { return COST; }
    @Override public com.solegendary.reignofnether.ability.Abilities getAbilities() {
        var abilities=new com.solegendary.reignofnether.ability.Abilities();
        abilities.add(com.dreamseekers.skill.UnitSkillAbility.COORDINATED_MARK);return abilities;
    }
    @Override public void updateAbilityButtons() { }
    @Override public ResourceLocation getHudIcon() { return DreamseekersMod.BONE_WING_ICON; }
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

    @Override public void tick() {
        setCanPickUpLoot(false);
        ((TeamSafeSelectedTargetGoal)getTargetGoal()).invalidateChangedRelationship();
        super.tick();
        if(!level().isClientSide())rts().skills().tick();
        if (getFollowTarget() != null && !canFollow(getFollowTarget())) {
            rts().follow = null;
            getMoveGoal().stopMoving();
        }
        if (isAlive()) {
            Unit.tick(this);
            com.solegendary.reignofnether.unit.interfaces.AttackerUnit.tick(this);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); writeRtsData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRtsData(tag);
        setNoAi(false);
        setNoGravity(true);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return DreamRtsUnit.attributes(BoneWingBalance.MAX_HEALTH, BoneWingBalance.DAMAGE, BoneWingBalance.SPEED,
                BoneWingBalance.ARMOR, BoneWingBalance.ATTACKS_PER_SECOND, BoneWingBalance.AGGRO)
                .add(Attributes.FLYING_SPEED,BoneWingBalance.SPEED)
                .add(AttributeRegistrar.ATTACK_RANGE.get(),BoneWingBalance.RANGE)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), BoneWingBalance.SIGHT);
    }
}
