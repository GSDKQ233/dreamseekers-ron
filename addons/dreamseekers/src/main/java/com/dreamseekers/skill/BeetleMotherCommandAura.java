// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.DreamFactionAccess;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.unit.BeetleMotherUnit;
import com.dreamseekers.unit.BeetleUnit;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** A bounded, transient ordinary-APS aura, not a skill cast, building pulse or summon lease. */
public final class BeetleMotherCommandAura {
    public static final int RANGE=6, MAX_TARGETS=6, DISCOVERY_PERIOD=10, LEASE_TICKS=20;
    public static final double ATTACK_SPEED=.15;
    static final String KEY="mother_swarm_command";
    private static final Map<BeetleMotherUnit,Selection> SELECTIONS=new IdentityHashMap<>();
    private BeetleMotherCommandAura() { }
    public static int range(BeetleMotherUnit source){return MotherNestStance.active(source)?8:RANGE;}
    private static final class Selection {
        final BeetleMotherUnit source;
        Set<BeetleUnit> targets=Collections.newSetFromMap(new IdentityHashMap<>());
        Selection(BeetleMotherUnit source) { this.source=source; }
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean sourceEligible(BeetleMotherUnit source) {
        return source.getClass()==BeetleMotherUnit.class&&source.getType()==DreamseekersMod.BEETLE_MOTHER_UNIT.get()
                &&source.isAlive()&&!source.isRemoved()&&!source.isSpectator()&&source.isAddedToWorld()
                &&source.level() instanceof ServerLevel level&&level.getEntity(source.getUUID())==source
                &&level.getEntity(source.getId())==source&&!source.getOwnerName().isBlank()
                &&!BoundedSummons.isTemporary(source)&&!NeutralContracts.pending(source)
                &&!source.hasEffect(MobEffectRegistrar.STUN.get())&&!source.hasEffect(MobEffectRegistrar.FREEZE.get())
                &&!source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static boolean targetEligible(BeetleMotherUnit source,BeetleUnit target) {
        return target.getClass()==BeetleUnit.class&&target.getType()==DreamseekersMod.BEETLE.get()
                &&target.isAlive()&&!target.isRemoved()&&!target.isSpectator()&&target.isAddedToWorld()
                &&target.level()==source.level()&&target.level() instanceof ServerLevel level
                &&level.getEntity(target.getUUID())==target&&level.getEntity(target.getId())==target
                &&source.getOwnerName().equals(target.getOwnerName())&&!NeutralContracts.pending(target)
                &&BoundedSummons.canAct(target)&&source.distanceToSqr(target)<=(double)range(source)*range(source)&&hasLoadedLineOfSight(level,source,target);
    }
    /** Same native eye-to-eye COLLIDER/NONE ray, but never creates a chunk ticket.
     * EntityLeave can run inside ChunkHolder demotion while other original entities are still
     * indexed. Level.clip would then call Level.getBlockState -> getChunk(create=true), reentering
     * DistanceManager's live future-update iterator. Missing ray/shape data denies this read;
     * it is not transparent air and does not authorize a persisted or reconstructed lease.
     * Package-private solely so a direct GameTest can exercise this no-load boundary.
     */
    static boolean hasLoadedLineOfSight(ServerLevel level,LivingEntity source,LivingEntity target) {
        if(source.level()!=level||target.level()!=level)return false;
        var from=new net.minecraft.world.phys.Vec3(source.getX(),source.getEyeY(),source.getZ());
        var to=new net.minecraft.world.phys.Vec3(target.getX(),target.getEyeY(),target.getZ());
        return hasLoadedLineOfSight(level,source,from,to);
    }
    /** The same no-ticket ray for previewing a small ground placement before constructing an entity. */
    static boolean hasLoadedLineOfSight(ServerLevel level,LivingEntity source,net.minecraft.world.phys.Vec3 from,
                                        net.minecraft.world.phys.Vec3 to) {
        if(source.level()!=level||from.distanceTo(to)>128)return false; // native LivingEntity.hasLineOfSight bound
        LoadedBlocks blocks=new LoadedBlocks(level);
        var hit=blocks.clip(new net.minecraft.world.level.ClipContext(from,to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,source));
        return blocks.complete&&hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private static final class LoadedBlocks implements net.minecraft.world.level.BlockGetter {
        private final ServerLevel level;
        private boolean complete=true;
        LoadedBlocks(ServerLevel level) { this.level=level; }
        private net.minecraft.world.level.chunk.LevelChunk chunk(net.minecraft.core.BlockPos pos) {
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null)complete=false;
            return chunk;
        }
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
            if(level.isOutsideBuildHeight(pos))return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            var chunk=chunk(pos);
            return chunk==null?net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState():chunk.getBlockState(pos);
        }
        @Override public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos) {
            if(level.isOutsideBuildHeight(pos))return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            var chunk=chunk(pos);
            return chunk==null?net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState():chunk.getFluidState(pos);
        }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) {
            if(level.isOutsideBuildHeight(pos))return null;
            var chunk=chunk(pos);if(chunk==null)return null;
            // LevelChunk.getBlockEntity may promote pending NBT and call onLoad. A read-only
            // collision view may use only an already instantiated, nonremoved block entity.
            var existing=chunk.getBlockEntities().get(pos);
            if(existing!=null&&!existing.isRemoved())return existing;
            if(existing!=null||chunk.getBlockState(pos).hasBlockEntity())complete=false;
            return null;
        }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
    /** Constructor stays private: SkillEffects accepts only this component's fixed-value authority. */
    static final class Lease {
        final SkillIdentity.Stamp caster,target;
        final long expiresAt;
        private final Selection selection;
        private final Resources account;
        private final UUID accountEpoch;
        private final RTSPlayer owner;
        private final Faction faction;
        private Lease(Selection selection,BeetleUnit target,Resources account,RTSPlayer owner) {
            this.selection=selection;caster=SkillIdentity.capture(selection.source);this.target=SkillIdentity.capture(target);
            this.account=account;accountEpoch=account.accountEpoch;this.owner=owner;faction=owner.faction;
            expiresAt=caster.level().getGameTime()+LEASE_TICKS;
        }
        boolean valid() {
            return caster.level().getGameTime()<expiresAt&&loaded(caster)&&loaded(target)
                    &&SELECTIONS.get(selection.source)==selection&&selection.targets.contains(target.entity())
                    &&sourceEligible(selection.source)&&targetEligible(selection.source,(BeetleUnit)target.entity())
                    &&ExtendedResourcesSync.account(caster.owner())==account&&account.extendedStateSupported()
                    &&accountEpoch.equals(account.accountEpoch)&&PlayerServerEvents.getRTSPlayer(caster.owner())==owner
                    &&owner.faction==faction&&DreamFactionAccess.allows(owner);
        }
    }
    static void tick(ServerLevel level) {
        SELECTIONS.entrySet().removeIf(entry->entry.getKey().level()==level&&!sourceEligible(entry.getKey()));
        if(level.getGameTime()%DISCOVERY_PERIOD!=0)return;
        // The existing loaded RTS index is only discovery, never authority; exact indexes are rechecked.
        List<BeetleMotherUnit> mothers=UnitServerEvents.getAllUnits().stream()
                .filter(entity->entity instanceof BeetleMotherUnit&&entity.level()==level)
                .map(entity->(BeetleMotherUnit)entity).filter(BeetleMotherCommandAura::sourceEligible)
                .sorted(Comparator.comparing(entity->entity.getUUID().toString())).toList();
        for(BeetleMotherUnit source:mothers) {
            Resources account=ExtendedResourcesSync.account(source.getOwnerName());
            RTSPlayer owner=PlayerServerEvents.getRTSPlayer(source.getOwnerName());
            if(account==null||!account.extendedStateSupported()||!DreamFactionAccess.allows(owner)) {
                SELECTIONS.remove(source);continue;
            }
            Selection selection=SELECTIONS.computeIfAbsent(source,Selection::new);
            List<BeetleUnit> chosen=level.getEntitiesOfClass(BeetleUnit.class,source.getBoundingBox().inflate(range(source)),
                    target->targetEligible(source,target)).stream()
                    .sorted(Comparator.comparingDouble((BeetleUnit target)->source.distanceToSqr(target))
                            .thenComparing(target->target.getUUID().toString())).limit(MAX_TARGETS).toList();
            selection.targets=Collections.newSetFromMap(new IdentityHashMap<>());selection.targets.addAll(chosen);
            for(BeetleUnit target:chosen) {
                // Revalidate the same authoritative source each discovery, before its old lease
                // expires during ordinary entity ticks. SkillEffects refuses a different live source.
                Lease lease=new Lease(selection,target,account,owner);
                if(lease.valid())SkillEffects.addMotherCommandBuff(lease);
            }
        }
    }
    static void clear(LivingEntity entity) {
        SELECTIONS.entrySet().removeIf(entry->entry.getKey()==entity);
        for(Selection selection:SELECTIONS.values())selection.targets.remove(entity);
    }
    static void stop() { SELECTIONS.clear(); }
}
