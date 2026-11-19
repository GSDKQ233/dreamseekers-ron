// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.sandbox.SandboxEconomyFixture;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Same-owner staged transformation. No source discard or debit precedes successful publication and revalidation. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class TerrorbeakUpgradeService {
    public static final String PENDING_MARKER="dreamseekers:pending_terrorbeak_upgrade";
    private static final Map<TerrorbeakUnit,CrazyTerrorbeakUnit> STAGING=new IdentityHashMap<>();
    private static final Set<String> BUSY_OWNERS=new HashSet<>();
    private TerrorbeakUpgradeService() {}
    public enum Result { SUCCESS, INVALID_REQUEST, INVALID_SOURCE, WRONG_DIMENSION, STALE_OWNER, NOT_OWNER,
        UNAVAILABLE, BUSY, INSUFFICIENT_RESOURCES, UNSAFE_POSITION, ACTIVE_EFFECT, SPAWN_FAILED }
    public static boolean pending(Entity entity) { return entity!=null&&entity.getPersistentData().contains(PENDING_MARKER); }
    /** Exact paid-upgrade identity shared by permanent-unit consumers. Other owner, publication,
     * lifetime and provenance checks still belong to the calling service. No direct recipe exists. */
    public static boolean isCommittedAdvanced(Entity entity) {
        return entity!=null&&entity.getClass()==CrazyTerrorbeakUnit.class
            &&entity.getType()==DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get()&&!pending(entity);
    }
    public static boolean busy(TerrorbeakUnit unit) { return STAGING.containsKey(unit); }
    private static boolean actor(ServerPlayer p,TerrorbeakUnit unit) {
        if(p==null||unit==null||!(unit.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
            ||p.serverLevel()!=level||p.connection==null||!p.isAlive()||p.isRemoved()
            ||!p.getGameProfile().getName().equals(unit.getOwnerName()))return false;
        var list=level.getServer().getPlayerList();
        return list.getPlayer(p.getUUID())==p&&list.getPlayerByName(unit.getOwnerName())==p
            &&DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(unit.getOwnerName()));
    }
    private static boolean valueAllowed(TerrorbeakUnit unit) {
        if(RtsKillValueClaims.isFree(unit)||RtsKillValueClaims.isClaimed(unit)
            ||com.dreamseekers.summon.BoundedSummons.isTemporary(unit)||SandboxEconomyFixture.has(unit))return false;
        if(!unit.getPersistentData().contains(RtsKillValueClaims.KEY))return true;
        Tag raw=unit.getPersistentData().get(RtsKillValueClaims.KEY);
        return raw instanceof CompoundTag value&&value.contains("version",Tag.TAG_INT)&&value.getInt("version")==1
            &&value.hasUUID("origin")&&!value.contains("claim");
    }
    /** A form change is not a cleanse. Reject vanilla effects and every explicit managed negative. */
    private static boolean effectFree(TerrorbeakUnit unit) {
        return unit.getActiveEffects().isEmpty()&&!unit.isOnFire()&&unit.getTicksFrozen()==0&&!unit.isPassenger()&&!unit.isVehicle()
            &&unit.hurtTime==0&&unit.fallDistance<=0&&unit.getAirSupply()>=unit.getMaxAirSupply()
            &&!com.dreamseekers.buildingability.BuildingAbilityEffects.hasEffects(unit)
            &&!unit.isCasting()&&ShadowCorrosionTouch.negative(unit)==null&&SquealSuppressionWave.negative(unit)==null
            &&TerrorbeakDreadRoar.negative(unit)==null&&ShadowHandGrip.negative(unit)==null&&AaroncosShockSuppression.negative(unit)==null
            &&FriendlyDebtContract.negatives(unit).isEmpty()&&!CoordinatedMark.hasActiveTargetMark(unit)
            &&WindDash.knockbackTicks(unit)==0;
    }
    private static boolean body(TerrorbeakUnit unit) {
        return unit.isAlive()&&!unit.isRemoved()&&unit.isAddedToWorld()&&unit.level() instanceof ServerLevel level
            &&level.getEntity(unit.getId())==unit&&level.getEntity(unit.getUUID())==unit
            &&UnitServerEvents.getAllUnits().contains(unit)&&level.hasChunkAt(unit.blockPosition())
            &&Float.isFinite(unit.getHealth())&&Float.isFinite(unit.getMaxHealth())&&unit.getMaxHealth()>0
            &&unit.rts().skills().canAdvance()&&valueAllowed(unit);
    }
    private static ResourceTransaction.Cost price() { return new ResourceTransaction.Cost(80,0,60,Map.of(DreamPassiveEconomy.SHADOW,30)); }
    private static boolean funds(Resources account) { return account!=null&&account.extendedStateSupported()
        &&account.food>=80&&account.ore>=60&&account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)>=30; }
    public static boolean mayOffer(ServerPlayer actor,TerrorbeakUnit source) {
        return actor(actor,source)&&body(source)&&effectFree(source)&&!busy(source)&&!BUSY_OWNERS.contains(source.getOwnerName())
            &&funds(ExtendedResourcesSync.account(source.getOwnerName()));
    }
    private static boolean safe(TerrorbeakUnit source,CrazyTerrorbeakUnit replacement) {
        var level=source.level();var box=replacement.getBoundingBox();
        return level.hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))
            &&box.minY>=level.getMinBuildHeight()&&box.maxY<=level.getMaxBuildHeight()&&level.getWorldBorder().isWithinBounds(box)
            &&!level.containsAnyLiquid(box)&&level.getBlockCollisions(replacement,box).iterator().hasNext()==false
            &&level.getEntities(replacement,box,e->e!=source&&e instanceof LivingEntity&&e.isAlive()&&!e.isRemoved()).isEmpty();
    }
    private static CompoundTag valueSnapshot(TerrorbeakUnit source) {
        if(source.getPersistentData().contains(RtsKillValueClaims.KEY))return source.getPersistentData().getCompound(RtsKillValueClaims.KEY).copy();
        var value=new CompoundTag();value.putInt("version",1);value.putUUID("origin",source.getUUID());return value;
    }
    public static Result request(ServerPlayer sender,TerrorbeakUpgradeRequest request) {
        if(sender==null||request==null||request.schema()!=1||request.requestId()==null||request.source()==null
            ||request.target()!=TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK)return Result.INVALID_REQUEST;
        var ref=request.source();var level=sender.serverLevel();
        if(!level.getServer().isSameThread()||ref.entityUuid()==null||ref.dimension()==null||ref.ownershipRevision()<0)return Result.INVALID_REQUEST;
        if(!level.dimension().location().equals(ref.dimension()))return Result.WRONG_DIMENSION;
        var found=level.getEntity(ref.entityId());
        if(!(found instanceof TerrorbeakUnit source)||!source.getUUID().equals(ref.entityUuid())||!body(source))return Result.INVALID_SOURCE;
        if(source.rts().ownershipRevision!=ref.ownershipRevision())return Result.STALE_OWNER;
        if(!actor(sender,source))return Result.NOT_OWNER;
        if(busy(source)||BUSY_OWNERS.contains(source.getOwnerName()))return Result.BUSY;
        if(!effectFree(source))return Result.ACTIVE_EFFECT;
        Resources account=ExtendedResourcesSync.account(source.getOwnerName());if(!funds(account))return Result.INSUFFICIENT_RESOURCES;
        final UUID epoch=account.accountEpoch;final long command=source.rts().commandRevision,skillRevision=source.rts().skills().revision();
        final float hp=source.getHealth(),max=source.getMaxHealth();final var pos=source.position();
        final var stamp=SkillIdentity.capture(source);final var value=valueSnapshot(source);
        if(!(source.getAttackGoal() instanceof TeamSafeMeleeAttackGoal oldAttack))return Result.INVALID_SOURCE;
        final int attackRemaining=oldAttack.transformationCooldown();
        var replacement=DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get().create(level);if(replacement==null)return Result.SPAWN_FAILED;
        replacement.getPersistentData().putBoolean(PENDING_MARKER,true);replacement.setNoAi(true);replacement.moveTo(pos.x,pos.y,pos.z,source.getYRot(),source.getXRot());
        replacement.setOwnerName(source.getOwnerName());replacement.setScenarioRoleIndex(source.getScenarioRoleIndex());replacement.setOnDeathCommand(source.getOnDeathCommand());
        RtsKillValueClaims.restoreValue(replacement,value);
        // Defensive monotonic carryover even though present eligibility denies free bodies.
        if(source.getPersistentData().contains(RtsKillValueClaims.FREE_ORIGIN_KEY))replacement.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,source.getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY).copy());
        CompoundTag skills=new CompoundTag();source.rts().skills().save(skills);replacement.rts().skills().load(skills);
        DreamAscendancyAttributes.reconcile(replacement,DreamAscendancyAttributes.enabledFor(replacement));
        replacement.setHealth(DreamResearchMath.healthAfter(hp,max,replacement.getMaxHealth()));
        if(!(replacement.getAttackGoal() instanceof TeamSafeMeleeAttackGoal newAttack)||!safe(source,replacement))return Result.UNSAFE_POSITION;
        newAttack.inheritTransformationCooldown(attackRemaining);
        final float nextHp=replacement.getHealth(),nextMax=replacement.getMaxHealth();
        final long nextOwner=replacement.rts().ownershipRevision,nextCommand=replacement.rts().commandRevision,nextSkill=replacement.rts().skills().revision();
        boolean committed=false;STAGING.put(source,replacement);BUSY_OWNERS.add(stamp.owner());
        try {
            if(!level.addFreshEntity(replacement)||!replacement.isAddedToWorld()||replacement.isRemoved()||!replacement.isAlive())return Result.SPAWN_FAILED;
            // Join callbacks can issue orders, change owner/account/effects/HP, remove/replace either exact body, or spend money.
            if(!stamp.valid()||!actor(sender,source)||!body(source)||!effectFree(source)||source.rts().commandRevision!=command
                ||source.rts().skills().revision()!=skillRevision||!source.position().equals(pos)
                ||Float.compare(source.getHealth(),hp)!=0||Float.compare(source.getMaxHealth(),max)!=0
                ||ExtendedResourcesSync.account(stamp.owner())!=account||!epoch.equals(account.accountEpoch)
                ||!funds(account)||!safe(source,replacement)||!valueSnapshot(source).equals(value)
                ||UnitServerEvents.getAllUnits().stream().filter(e->e==source).count()!=1
                ||level.getEntity(replacement.getId())!=replacement||level.getEntity(replacement.getUUID())!=replacement||!replacement.isNoAi()
                ||UnitServerEvents.getAllUnits().stream().filter(e->e==replacement).count()!=1
                ||!replacement.getOwnerName().equals(stamp.owner())||!pending(replacement)
                ||replacement.rts().ownershipRevision!=nextOwner||replacement.rts().commandRevision!=nextCommand||replacement.rts().skills().revision()!=nextSkill
                ||!replacement.position().equals(pos)||Float.compare(replacement.getHealth(),nextHp)!=0||Float.compare(replacement.getMaxHealth(),nextMax)!=0
                ||RtsKillValueClaims.isFree(replacement)||RtsKillValueClaims.isClaimed(replacement)
                ||!replacement.getPersistentData().getCompound(RtsKillValueClaims.KEY).equals(value))return Result.INVALID_SOURCE;
            // No callbacks in exchange. After this point a normal committed transformation may be attacked; no rollback refund.
            if(!ResourceTransaction.exchange(level.getServer(),account,epoch,account.revision,price(),ResourceTransaction.Cost.ZERO).applied())return Result.INSUFFICIENT_RESOURCES;
            committed=true;
            replacement.getItems().clear();for(var stack:source.getItems())replacement.getItems().add(stack.copy());source.getItems().clear();
            // Fresh replacement has no orders; never dispatch a reset callback between payment and source retirement.
            replacement.rts().hold=true;newAttack.inheritTransformationCooldown(attackRemaining);
            // Publish committed state BEFORE synchronous leave callbacks. Never reactivate it after those callbacks.
            replacement.getPersistentData().remove(PENDING_MARKER);replacement.setNoAi(false);
            if(!UnitServerEvents.retireTransformedUnit(source,replacement))throw new IllegalStateException("Verified committed transformation retirement rejected");
            source.rts().skills().detach();
            if(replacement.isAlive()&&!replacement.isRemoved()&&replacement.getOwnerName().equals(stamp.owner()))replacement.rts().skills().sync(sender);
            ExtendedResourcesSync.send(sender);
            return Result.SUCCESS;
        } finally {
            STAGING.remove(source);BUSY_OWNERS.remove(stamp.owner());
            if(!committed)UnitServerEvents.rollbackUncommittedProductionSpawn(replacement);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide()||!pending(event.getEntity()))return;
        if(STAGING.values().stream().noneMatch(e->e==event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent event) {
        if(event.getEntity().level().isClientSide())return;
        if(pending(event.getEntity())||pending(event.getSource().getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void economic(RtsKillValueEligibilityEvent event) { if(pending(event.victim())||pending(event.killer()))event.setCanceled(true); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){STAGING.clear();BUSY_OWNERS.clear();}
}
