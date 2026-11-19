package com.dreamseekers.unit;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import com.dreamseekers.skill.SnailShellFormation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.pasterdream.entity.BasaltSnailEntity;

/**
 * A real PasterDream BasaltSnail adapted as a slow RTS defender. The source creature is
 * passive; its melee role and capped shell cooldown are explicitly this addon's design.
 */
public class BasaltSnailUnit extends BasaltSnailEntity implements DreamRtsUnit {
    public static final ResourceCost COST = ResourceCost.Unit(90, 0, 35, 30, 3);
    public static final int SHELL_DURATION = 80;
    public static final int SHELL_COOLDOWN = 200;
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(BasaltSnailUnit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SCENARIO = SynchedEntityData.defineId(BasaltSnailUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_COMMAND = SynchedEntityData.defineId(BasaltSnailUnit.class, EntityDataSerializers.STRING);
    private static final RtsSyncedData SYNC = new RtsSyncedData(OWNER, SCENARIO, DEATH_COMMAND);
    private RtsUnitState state;
    private int shellCooldown;

    @SuppressWarnings("unchecked")
    public BasaltSnailUnit(EntityType<? extends BasaltSnailEntity> type, Level level) {
        super((EntityType<BasaltSnailEntity>) type, level);
        rts();
        setPersistenceRequired();
        xpReward = 0;
        updateAbilityButtons();
    }
    @Override public RtsUnitState rts() {
        if (state == null) state = new RtsUnitState(this, false);
        return state;
    }
    @Override public RtsSyncedData rtsSynced() { return SYNC; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); SYNC.define(entityData); }
    @Override protected void registerGoals() { rts().register(goalSelector, targetSelector); }
    @Override public ResourceCost getCost() { return COST; }
    @Override protected PathNavigation createNavigation(Level level) {
        return new com.dreamseekers.skill.SnailShellNavigation(this,level);
    }
    @Override public void updateAbilityButtons() {
        rts().abilities=new com.solegendary.reignofnether.ability.Abilities();
        rts().abilities.add(com.dreamseekers.skill.UnitSkillAbility.SNAIL_SHELL_FORMATION);
    }
    @Override public boolean isIdle() { return !SnailShellFormation.active(this)&&DreamRtsUnit.super.isIdle(); }
    @Override public boolean permitsAutomaticAnchorReturn() {
        return !SnailShellFormation.active(this)&&DreamRtsUnit.super.permitsAutomaticAnchorReturn();
    }
    @Override public boolean getHoldPosition() { return SnailShellFormation.active(this)||DreamRtsUnit.super.getHoldPosition(); }
    @Override public void resetBehaviours() {
        DreamRtsUnit.super.resetBehaviours();SnailShellFormation.commandChanged(this);
    }
    @Override public void setMoveTarget(BlockPos target) {
        if(target!=null)SnailShellFormation.commandChanged(this);
        DreamRtsUnit.super.setMoveTarget(target);
    }
    @Override public void setUnitAttackTargetForced(LivingEntity target) {
        if(target!=null)SnailShellFormation.commandChanged(this);
        DreamRtsUnit.super.setUnitAttackTargetForced(target);
    }
    @Override public void setFollowTarget(LivingEntity target) {
        if(target!=null)SnailShellFormation.commandChanged(this);
        DreamRtsUnit.super.setFollowTarget(target);
    }
    @Override public void setAttackMoveTarget(BlockPos target) {
        if(target!=null)SnailShellFormation.commandChanged(this);
        DreamRtsUnit.super.setAttackMoveTarget(target);
    }
    @Override public void setHoldPosition(boolean hold) {
        if(hold)SnailShellFormation.commandChanged(this);
        DreamRtsUnit.super.setHoldPosition(hold);
    }
    @Override public LivingEntity getTarget() { return getTargetGoal().getTarget(); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected void customServerAiStep() { }
    @Override protected void pickUpItem(ItemEntity item) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) { }
    public int getShellCooldown() { return shellCooldown; }

    @Override public boolean doHurtTarget(Entity target) {
        return SnailShellFormation.permitsMelee(this,target)&&super.doHurtTarget(target);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        boolean accepted = super.hurt(source, amount);
        if (accepted && isAlive() && !level().isClientSide() && shellCooldown == 0) {
            shellCooldown = SHELL_COOLDOWN;
            setAnimation("inoutshell"); // Existing animation resource remains in PasterDream.
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SHELL_DURATION, 0));
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SHELL_DURATION, 2));
        }
        return accepted;
    }
    @Override public void tick() {
        setCanPickUpLoot(false);
        if (!level().isClientSide() && shellCooldown > 0) shellCooldown--;
        if(!level().isClientSide())SnailShellFormation.tickCombat(this);
        super.tick();
        if (isAlive()) {
            Unit.tick(this);
            AttackerUnit.tick(this);
            if(!level().isClientSide())rts().skills().tick();
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeRtsData(tag);
        tag.putInt("DreamseekersShellCooldown", shellCooldown);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readRtsData(tag);
        shellCooldown = Math.max(0, Math.min(SHELL_COOLDOWN, tag.getInt("DreamseekersShellCooldown")));
    }
    public static AttributeSupplier.Builder createAttributes() { return DreamRtsUnit.attributes(100, 7, 0.16, 5, 0.45, 8); }
}
