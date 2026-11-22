package com.dreamseekers.unit;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.pasterdream.entity.BlackBeetleEntity;

/** Genuine BlackBeetle with RTS orders replacing taming, owner-following, and breeding AI. */
public class BeetleUnit extends BlackBeetleEntity implements DreamRtsUnit {
    public static final ResourceCost COST = ResourceCost.Unit(75, 20, 0, 16, 2);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(BeetleUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(BeetleUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(BeetleUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;

    @SuppressWarnings("unchecked")
    public BeetleUnit(EntityType<? extends BlackBeetleEntity> type, Level level) {
        super((EntityType<BlackBeetleEntity>) type, level);
        rts();
        setPersistenceRequired();
        xpReward = 0;
        updateAbilityButtons();
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false, false, false, -1,
            com.dreamseekers.summon.SummonSafeBuildingGoal::new);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override public ResourceCost getCost() { return COST; }
    @Override public net.minecraft.resources.ResourceLocation getHudIcon() { return com.dreamseekers.DreamseekersMod.BEETLE_ICON; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public void updateAbilityButtons() {
        rts().abilities=new com.solegendary.reignofnether.ability.Abilities();
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.BEETLE_ADVANCE);
    }
    @Override public boolean doHurtTarget(Entity target) {
        return com.dreamseekers.skill.BeetleAdvance.invokeMelee(this,target,()->super.doHurtTarget(target));
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        return com.dreamseekers.skill.BeetleGuard.hurt(this,source,amount,()->super.hurt(source,amount));
    }
    @Override public void setSpeed(float speed) {
        super.setSpeed(speed);
        // Native ground MOVE_TO consumes speed both as friction speed and forward input.
        // Remove only the extra temporary multiplier from the latter, never from attributes.
        // STRAFE overwrites forward input after this call; air/fluid keep their native path.
        if(onGround()&&!isInWater()&&!isInLava()) {
            double bonus=com.dreamseekers.skill.SkillEffects.bonus(this,com.dreamseekers.skill.BuffKind.MOVEMENT_SPEED);
            if(Double.isFinite(bonus)&&bonus>0)setZza((float)(zza/(1+bonus)));
        }
    }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void customServerAiStep() { }
    @Override protected void pickUpItem(ItemEntity item) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) { }
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public void tick() {
        setCanPickUpLoot(false);
        super.tick();
        if (isAlive()) {
            Unit.tick(this);
            AttackerUnit.tick(this);
            if(!level().isClientSide())rts().skills().tick();
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); writeRtsData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTame(false);
        setOrderedToSit(false);
        readRtsData(tag);
    }
    public static AttributeSupplier.Builder createAttributes() { return DreamRtsUnit.attributes(55, 5, 0.37, 2, 0.8, 10); }
}
