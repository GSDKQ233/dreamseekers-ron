package com.solegendary.reignofnether.unit;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.heroAbilities.necromancer.SoulSiphonPassive;
import com.solegendary.reignofnether.ability.heroAbilities.wildfire.ScorchingGaze;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.monsters.SculkCatalyst;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.placements.SculkCatalystPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.IronGolemBuilding;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.entities.BlazeUnitFireball;
import com.solegendary.reignofnether.entities.GhastUnitFireball;
import com.solegendary.reignofnether.entities.WindcallerProjectile;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.items.ItemClientboundPacket;
import com.solegendary.reignofnether.items.ItemServerEvents;
import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.items.UnitItems;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.packets.*;
import com.solegendary.reignofnether.unit.units.monsters.*;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import com.solegendary.reignofnether.unit.units.piglins.*;
import com.solegendary.reignofnether.unit.units.villagers.*;
import com.solegendary.reignofnether.util.EnchantmentUtil;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.ParticleUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3d;

import java.util.*;
import java.util.function.Predicate;

import static com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer;
import static com.solegendary.reignofnether.player.PlayerServerEvents.serverLevel;
import static com.solegendary.reignofnether.resources.ResourcesServerEvents.*;

public class UnitServerEvents {

    private static final int UNIT_SYNC_TICKS_MAX = 20; // how often we send out unit syncing packets
    private static int unitSyncTicks = UNIT_SYNC_TICKS_MAX;

    // max possible pop you can have regardless of buildings, adjustable via /gamerule maxPopulation
    public static int maxPopulation = ResourceCosts.DEFAULT_MAX_POPULATION;

    // server-side mirror of the rtsPathfinding gamerule: when true, units route through the RTS
    // grid A* pathfinder (with walkability caching + worker pool); when false they use vanilla
    // pathfinding and the caching/worker work is skipped. Kept in sync by GameruleServerEvents.
    public static boolean rtsPathfinding = false;

    // actioned only when the associated unit is idle, one at a time
    private static final List<UnitActionItem> unitActionSlowQueue = Collections.synchronizedList(new ArrayList<>());
    // actioned ASAP regardless of what the unit was doing
    private static final List<UnitActionItem> unitActionFastQueue = Collections.synchronizedList(new ArrayList<>());

    public static List<UnitActionItem> getUnitActionSlowQueue() { return unitActionSlowQueue; }

    private static final ArrayList<LivingEntity> allUnits = new ArrayList<>();

    private static final HashMap<Integer, ChunkAccess> forcedUnitChunks = new HashMap<>();
    // The ID map is retained for existing diagnostics; actual ownership is exact-lifetime scoped.
    private static final Map<Integer, LivingEntity> forcedUnitChunkOwners = new HashMap<>();
    private static final Map<LivingEntity, UnitChunkTickets> unitChunkTickets = new IdentityHashMap<>();
    // Startup evidence only: unresolved UUIDs are NOT proof of an orphan and remain untouched.
    private static final Map<ServerLevel, Map<UUID, long[]>> restoredUnitTickets = new IdentityHashMap<>();

    public static void registerChunkTicketValidation() {
        ForgeChunkManager.setForcedChunkLoadingCallback(ReignOfNether.MOD_ID, (level, helper) -> {
            Map<UUID, long[]> restored = new HashMap<>();
            helper.getEntityTickets().forEach((uuid, chunks) -> {
                if (!chunks.getSecond().isEmpty()) restored.put(uuid, chunks.getSecond().toLongArray());
            });
            restoredUnitTickets.put(level, restored);
        });
    }

    private static void reconcileRestoredUnitTickets(LivingEntity entity, UnitChunkTickets tickets) {
        Map<UUID, long[]> restored = restoredUnitTickets.get(tickets.level);
        if (restored == null) return;
        long[] oldChunks = restored.remove(tickets.entityUuid);
        if (oldChunks == null) return;
        // Called after exact live body/UUID/ID publication validation. Only this mod's legacy
        // ticking entity tickets in this same dimension are owned by the unit center lifecycle.
        long current = new ChunkPos(entity.blockPosition()).toLong();
        for (long old : oldChunks) if (old != current)
            ForgeChunkManager.forceChunk(tickets.level, ReignOfNether.MOD_ID, tickets.entityUuid,
                    ChunkPos.getX(old), ChunkPos.getZ(old), false, true);
        if (restored.isEmpty()) restoredUnitTickets.remove(tickets.level);
    }

    private static final TicketType<UUID> UNIT_NEIGHBORHOOD = TicketType.create(
            "reignofnether:unit_neighborhood", UUID::compareTo);
    private static final class UnitChunkTickets {
        final ServerLevel level;
        final UUID entityUuid, neighborhoodOwner = UUID.randomUUID();
        final int entityId;
        ChunkAccess chunk;
        ChunkPos neighborhoodCenter;
        boolean persistent = true;
        UnitChunkTickets(LivingEntity entity, ServerLevel level, ChunkAccess chunk) {
            this.level = level; this.entityUuid = entity.getUUID(); this.entityId = entity.getId(); this.chunk = chunk;
        }
    }

    private static boolean withinNeighborhood(ChunkPos point, ChunkPos center) {
        return center != null && Math.abs((long) point.x - center.x) <= 2 && Math.abs((long) point.z - center.z) <= 2;
    }
    private static void releaseNeighborhood(UnitChunkTickets tickets) {
        ChunkPos center = tickets.neighborhoodCenter;
        tickets.neighborhoodCenter = null;
        if (center == null) return;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            tickets.level.getChunkSource().removeRegionTicket(UNIT_NEIGHBORHOOD,
                    new ChunkPos(center.x + dx, center.z + dz), 2, tickets.neighborhoodOwner);
    }
    private static boolean releasePersistentUnitTicket(LivingEntity entity, UnitChunkTickets tickets) {
        if (!tickets.persistent) return false;
        tickets.persistent = false;
        if (forcedUnitChunkOwners.remove(tickets.entityId, entity))
            forcedUnitChunks.remove(tickets.entityId, tickets.chunk);
        ForgeChunkManager.forceChunk(tickets.level, ReignOfNether.MOD_ID, tickets.entityUuid,
                tickets.chunk.getPos().x, tickets.chunk.getPos().z, false, true);
        return true;
    }
    /** Called only after native publication; no world/chunk lookup is needed on removal. */
    private static void retireUnitChunkTickets(LivingEntity entity, boolean releasePersistent) {
        UnitChunkTickets tickets = unitChunkTickets.remove(entity);
        if (tickets == null) return;
        releaseNeighborhood(tickets);
        if (releasePersistent) releasePersistentUnitTicket(entity, tickets);
        else if (forcedUnitChunkOwners.remove(tickets.entityId, entity))
            forcedUnitChunks.remove(tickets.entityId, tickets.chunk);
    }
    /** Preserve the existing five-by-five neighborhood, but own and retire every runtime ticket.
     * Overlapping units never share owner keys and cannot unforce each other or vanilla force loads. */
    public static void updateUnitChunkTickets(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !entity.isAlive() || entity.isRemoved()) return;
        UnitChunkTickets tickets = unitChunkTickets.get(entity);
        if (tickets == null || tickets.level != level || tickets.entityId != entity.getId()
                || !tickets.entityUuid.equals(entity.getUUID()) || level.getEntity(entity.getUUID()) != entity
                || level.getEntity(entity.getId()) != entity) return;
        if (tickets.persistent) reconcileRestoredUnitTickets(entity, tickets);
        ChunkPos center = new ChunkPos(entity.blockPosition());
        if (!center.equals(tickets.neighborhoodCenter)) {
            ChunkPos old = tickets.neighborhoodCenter;
            if (old != null) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                ChunkPos pos = new ChunkPos(old.x + dx, old.z + dz);
                if (!withinNeighborhood(pos, center)) level.getChunkSource().removeRegionTicket(
                        UNIT_NEIGHBORHOOD, pos, 2, tickets.neighborhoodOwner);
            }
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                if (!withinNeighborhood(pos, old)) level.getChunkSource().addRegionTicket(
                        UNIT_NEIGHBORHOOD, pos, 2, tickets.neighborhoodOwner);
            }
            tickets.neighborhoodCenter = center;
        }
        if (tickets.persistent && !center.equals(tickets.chunk.getPos())) {
            ForgeChunkManager.forceChunk(level, ReignOfNether.MOD_ID, tickets.entityUuid,
                    tickets.chunk.getPos().x, tickets.chunk.getPos().z, false, true);
            ForgeChunkManager.forceChunk(level, ReignOfNether.MOD_ID, tickets.entityUuid,
                    center.x, center.z, true, true);
            tickets.chunk = level.getChunk(center.x, center.z);
            if (forcedUnitChunkOwners.get(tickets.entityId) == entity)
                forcedUnitChunks.put(tickets.entityId, tickets.chunk);
        }
    }

    private static final Random RANDOM = new Random();

    // Time-sliced formation dispatch: VANILLA-ONLY (rtsPathfinding off). Large group MOVE commands
    // are spread across ticks to avoid the spike from N units all running vanilla's synchronous main-thread
    // A* in the same tick. With RTS pathfinding on, moves dispatch immediately (UnitActionItem) since the
    // worker pool queues + backpressures off-thread, so this queue stays empty. LinkedHashMap preserves
    // insertion order while letting a re-queued unit overwrite its old target (supersession).
    private static final int FORMATION_DISPATCH_PER_TICK = 5;
    // unit -> formation slot. Queued so a large vanilla selection's moves dispatch time-sliced across ticks
    // instead of running N concurrent synchronous A* searches in one tick.
    private record FormationOrder(LivingEntity unit, ServerLevel level, int id, UUID uuid,
                                  String owner, BlockPos target) {
        boolean valid(Level dispatchLevel) {
            return dispatchLevel == level && unit.level() == level && unit.isAlive() && !unit.isRemoved()
                    && unit.isAddedToWorld() && unit.getId() == id && uuid.equals(unit.getUUID())
                    && level.getEntity(id) == unit && level.getEntity(uuid) == unit
                    && unit instanceof Unit controlled && Objects.equals(owner, controlled.getOwnerName())
                    && allUnits.stream().anyMatch(existing -> existing == unit);
        }
    }
    private static final LinkedHashMap<Integer, FormationOrder> formationDispatchQueue = new LinkedHashMap<>();

    public static void queueFormationMove(List<Pair<LivingEntity, BlockPos>> pairs) {
        synchronized (formationDispatchQueue) {
            for (Pair<LivingEntity, BlockPos> p : pairs) {
                LivingEntity entity = p.getFirst();
                if (!(entity instanceof Unit unit) || !(entity.level() instanceof ServerLevel level)
                        || !level.getServer().isSameThread() || p.getSecond() == null) continue;
                FormationOrder order = new FormationOrder(entity, level, entity.getId(), entity.getUUID(),
                        unit.getOwnerName(), p.getSecond().immutable());
                if (order.valid(level)) formationDispatchQueue.put(entity.getId(), order);
            }
        }
    }
    /** Call at an accepted command/reset boundary, never from an unverified foreign packet. */
    public static void discardPendingFormationMove(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        com.solegendary.reignofnether.building.production.NativeProductionRally.cancel(entity);
        synchronized (formationDispatchQueue) {
            formationDispatchQueue.values().removeIf(order -> order.unit() == entity);
        }
    }
    /** A deferred first MOVE is still an active order, even before its navigation target is published. */
    public static boolean hasPendingFormationMove(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        synchronized (formationDispatchQueue) {
            FormationOrder order = formationDispatchQueue.get(entity.getId());
            return order != null && order.unit() == entity && order.valid(entity.level());
        }
    }
    /** Synced-data observer catches ownership A -> B -> A before dispatch; unqueued units are no-ops. */
    public static void observeFormationOwnerMutation(Entity entity) {
        if (entity == null || entity.level().isClientSide() || !(entity instanceof Unit unit)) return;
        synchronized (formationDispatchQueue) {
            formationDispatchQueue.values().removeIf(order -> order.unit() == entity
                    && !Objects.equals(order.owner(), unit.getOwnerName()));
        }
    }

    public static ArrayList<LivingEntity> getAllUnits() {
        return allUnits;
    }

    /** Roll back only a freshly rejected spawn, not a tracked entity's ordinary death/leave lifecycle.
     * A later Forge Join listener may cancel after our NORMAL listener has registered population/tickets.
     * Callers retain the exact attempted object; no client IDs, leave packets, defeat or rewards are replayed.
     */
    public static void rollbackRejectedUnitSpawn(LivingEntity entity) {
        if (!(entity instanceof Unit) || !(entity.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || entity.isAddedToWorld()) return;
        boolean registered;
        synchronized (allUnits) { registered = allUnits.removeIf(existing -> existing == entity); }
        if (!registered) return; // Never release another object's ID-indexed ticket.
        discardPendingFormationMove(entity);
        retireUnitChunkTickets(entity, true);
    }

    /** Release only this exact tracked lifetime's native chunk ticket. Does not remove the unit,
     * dispatch leave/defeat, or roll back a completed production. Safe to call before retirement. */
    public static boolean releaseTrackedUnitChunkTicket(LivingEntity entity) {
        if (!(entity instanceof Unit) || !(entity.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()
                || level.getEntity(entity.getId()) != entity || level.getEntity(entity.getUUID()) != entity) return false;
        synchronized (allUnits) {
            if (allUnits.stream().noneMatch(other -> other == entity)
                    || allUnits.stream().anyMatch(other -> other != entity && other.getId() == entity.getId())) return false;
        }
        UnitChunkTickets tickets = unitChunkTickets.get(entity);
        return tickets != null && releasePersistentUnitTicket(entity, tickets);
    }

    private static final Set<LivingEntity> transformedRetirements =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** Trusted committed same-owner replacement only. The replacement is already paid and published.
     * Remove only the retired lifetime's population/ticket; discard is not a combat death and does
     * not create a false defeat. Leave callbacks may affect the committed replacement; callers must
     * never reactivate or restore its owner/HP afterward. No entity-ID packet calls this helper. */
    public static boolean retireTransformedUnit(LivingEntity oldBody, LivingEntity replacement) {
        return retireTransformedUnit(oldBody,replacement,() -> {});
    }

    /** Trusted UI notification at the committed boundary, before the old leave packet. */
    public static boolean retireTransformedUnit(LivingEntity oldBody, LivingEntity replacement, Runnable beforeRetirement) {
        if (oldBody == replacement || !(oldBody instanceof Unit oldUnit) || !(replacement instanceof Unit nextUnit)
                || !(oldBody.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || replacement.level() != level || !oldBody.isAlive() || !replacement.isAlive()
                || oldBody.isRemoved() || replacement.isRemoved()
                || level.getEntity(oldBody.getUUID()) != oldBody || level.getEntity(oldBody.getId()) != oldBody
                || level.getEntity(replacement.getUUID()) != replacement || level.getEntity(replacement.getId()) != replacement
                || !oldUnit.getOwnerName().equals(nextUnit.getOwnerName())) return false;
        synchronized (allUnits) {
            if (allUnits.stream().filter(e -> e == oldBody).count() != 1
                    || allUnits.stream().filter(e -> e == replacement).count() != 1) return false;
        }
        if (!transformedRetirements.add(oldBody)) return false;
        try {
            beforeRetirement.run();
            releaseTrackedUnitChunkTicket(oldBody);
            synchronized (allUnits) { allUnits.removeIf(e -> e == oldBody); }
            oldBody.discard();
            return true;
        } finally { transformedRetirements.remove(oldBody); }
    }

    // Scoped only to synchronous discard of the exact fresh uncommitted production object.
    private static final Set<LivingEntity> uncommittedSpawnRollbacks =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** Internal server production rollback, not an entity-ID action. Call only with the exact freshly
     * returned spawn object before recording score/consuming completion. Normal death is untouched. */
    public static void rollbackUncommittedProductionSpawn(LivingEntity entity) {
        if (!(entity instanceof Unit) || !(entity.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        // Never clean another lifetime that happens to reuse an ID or UUID.
        var published = level.getEntity(entity.getUUID());
        if (published != null && published != entity) return;
        synchronized (allUnits) {
            if (allUnits.stream().anyMatch(other -> other != entity && other.getId() == entity.getId())) return;
        }
        if (!uncommittedSpawnRollbacks.add(entity)) return;
        try {
            boolean registered;
            synchronized (allUnits) { registered = allUnits.removeIf(existing -> existing == entity); }
            if (registered) {
                discardPendingFormationMove(entity);
                retireUnitChunkTickets(entity, true);
            }
            // Vanilla/Forge synchronously dispatches leave from discard; skip RTS defeat below.
            if (!entity.isRemoved()) entity.discard();
        } finally { uncommittedSpawnRollbacks.remove(entity); }
    }

    public static final ArrayList<TargetResourcesSave> savedTargetResources = new ArrayList<>();

    private static boolean isServerStopping = false;

    private static final int SAVE_TICKS_MAX = 600;
    private static int saveTicks = 0;
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        // Both startup SavedData listeners have completed before the first server tick.
        // This also covers units whose EntityJoin event happened before ServerStarted.
        restorePendingGatherTargets();
        saveTicks += 1;
        if (saveTicks >= SAVE_TICKS_MAX) {
            ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
            if (level != null) {
                saveGatherTargets(level);
                saveTicks = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        pendingNativeDeaths.clear();
        committedNativeDeaths.clear();
        nativeDeathDispatch.remove();
        isServerStopping = true;
        restoredUnitTickets.clear();
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
        if (level != null) {
            saveFallenHeroUnits(level);
            saveGatherTargets(level);
            allUnits.clear();
            // Persist live Forge center tickets for normal reload, but no runtime callback/footprint survives.
            for (UnitChunkTickets tickets : unitChunkTickets.values()) releaseNeighborhood(tickets);
            unitChunkTickets.clear(); forcedUnitChunkOwners.clear(); forcedUnitChunks.clear();
            synchronized (formationDispatchQueue) { formationDispatchQueue.clear(); }
        }
        synchronized (savedTargetResources) { savedTargetResources.clear(); }
    }

    public static void addUnitPoofs(Level level, Entity entity) {
        ParticleUtil.addParticleExplosion(ParticleTypes.POOF, 35, level, entity.position());
    }

    public static void saveFallenHeroUnits(ServerLevel level) {
        if (!HeroServerEvents.isCurrentHeroWorld(level)) return;
        ServerLevel overworld = level.getServer().overworld();
        HeroUnitSaveData data = HeroUnitSaveData.getInstance(overworld);
        data.setDirty();
        // Death/claim notifications can originate in another dimension; the saved roster cannot.
        overworld.getDataStorage().save();
        ReignOfNether.LOGGER.info("Saved {} fallen hero units", data.heroUnits.size());
    }

    public static void saveGatherTargets(ServerLevel level) {
        TargetResourcesSaveData data = TargetResourcesSaveData.getInstance(level);
        Map<String, TargetResourcesSave> snapshot = new LinkedHashMap<>();
        synchronized (savedTargetResources) {
            // A not-yet-published worker is not evidence that its last known order was cancelled.
            for (TargetResourcesSave pending : savedTargetResources) {
                if (!pending.unitUUID.isBlank() && pending.hasData()) {
                    snapshot.put(pending.unitUUID, pending.diskSnapshot(pending.unitUUID));
                }
            }
        }
        for (LivingEntity entity : getAllUnits()) {
            if (entity.isRemoved() || !entity.isAlive() || !(entity instanceof WorkerUnit worker)
                    || !(entity.level() instanceof ServerLevel workerLevel) || workerLevel.getServer() != level.getServer()) continue;
            var goal = worker.getGatherResourceGoal();
            if (goal == null) continue;
            TargetResourcesSave active = goal.data.hasData() ? goal.data : goal.saveData;
            if (active.hasData()) snapshot.put(entity.getStringUUID(), active.diskSnapshot(entity.getStringUUID()));
        }
        data.targetData.clear();
        data.targetData.addAll(snapshot.values());
        data.save();
        level.getDataStorage().save();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
        isServerStopping = false;
        synchronized (savedTargetResources) { savedTargetResources.clear(); }

        if (level != null) {
            HeroServerEvents.bindFallenHeroes(level);
            ReignOfNether.LOGGER.info("Loaded {} world-scoped fallen hero units", HeroServerEvents.fallenHeroes.size());

            synchronized (savedTargetResources) {
                TargetResourcesSaveData data = TargetResourcesSaveData.getInstance(level);
                // Restore consumes records. Keep the storage snapshot separate from mutable pending work.
                for (TargetResourcesSave saved : data.targetData) {
                    savedTargetResources.add(saved.diskSnapshot(saved.unitUUID));
                }
                ReignOfNether.LOGGER.info("Loaded " + data.targetData.size() + " gatherTargets in serverevents");
            }
        }
    }

    // A newer authoritative command wins over a startup snapshot still awaiting END tick.
    public static void discardPendingGatherTarget(Entity entity) {
        if (entity.level().isClientSide()) return;
        synchronized (savedTargetResources) {
            savedTargetResources.removeIf(saved -> saved.unitUUID.equals(entity.getStringUUID()));
        }
    }

    private static void restorePendingGatherTargets() {
        synchronized (savedTargetResources) {
            if (savedTargetResources.isEmpty()) return;
            Map<String, LivingEntity> workers = new HashMap<>();
            for (LivingEntity entity : allUnits) {
                if (entity instanceof WorkerUnit worker && !entity.isRemoved() && entity.isAlive()
                        && worker.getGatherResourceGoal() != null) {
                    workers.putIfAbsent(entity.getStringUUID(), entity);
                }
            }
            savedTargetResources.removeIf(saved -> {
                LivingEntity entity = workers.get(saved.unitUUID);
                if (!(entity instanceof WorkerUnit worker)) return false;
                BlockPos farmOrigin = saved.getFarmOrigin();
                if (farmOrigin != null) {
                    saved.targetFarm = BuildingServerEvents.getBuildings().stream()
                            .filter(b -> b instanceof FarmPlacement && b.originPos.equals(farmOrigin)
                                    && b.getLevel() == entity.level() && !b.isDestroyedServerside
                                    && b.ownerName.equals(((Unit) entity).getOwnerName()))
                            .findFirst().orElse(null);
                }
                // Resolution is one-shot: do not adopt a future, same-position farm.
                saved.savedFarmOrigin = null;
                worker.getGatherResourceGoal().saveData = saved;
                worker.getGatherResourceGoal().loadState();
                // A restored task is not a persistent auto-return checkpoint.
                saved.delete();
                return true;
            });
        }
    }

    // convert all entities that match the condition to the given unit type
    public static void convertAllToUnit(
        String ownerName,
        ServerLevel level,
        Predicate<LivingEntity> entityCondition,
        EntityType<? extends Unit> entityType
    ) {
        // Work on a snapshot because each successful conversion atomically retires its old body.
        for (LivingEntity unit : new ArrayList<>(getAllUnits())) {
            if (unit.level() == level && unit instanceof Unit owned && ownerName.equals(owned.getOwnerName())
                    && entityCondition.test(unit) && unit instanceof ConvertableUnit convertible)
                convertible.convertToUnit(entityType);
        }
    }

    public static int getCurrentPopulation(String ownerName) {
        int currentPopulation = 0;
        for (LivingEntity entity : allUnits)
            if (entity instanceof Unit unit) {
                if (unit.getOwnerName().equals(ownerName)) {
                    currentPopulation += unit.getCost().population;
                }
            }
        for (BuildingPlacement building : BuildingServerEvents.getBuildings())
            if (building.ownerName.equals(ownerName)) {
                if (building instanceof ProductionPlacement prodPlacement) {
                    for (ActiveProduction prodItem : prodPlacement.productionQueue)
                        if (!(prodPlacement instanceof GraveyardPlacement gy) || gy.getUpgradeLevel() <= 0)
                            currentPopulation += prodItem.item.getCost(false, ownerName).population;
                } else if (building.getBuilding() instanceof IronGolemBuilding) {
                    currentPopulation += ResourceCosts.IRON_GOLEM.population;
                }
            }
        return currentPopulation;
    }

    // manually provide all the variables required to do unit actions
    /** Read-only scheduling observation, not authorization or an unknown-ID retry facility. */
    public static boolean hasPendingImmediateAction(int entityId) {
        synchronized (unitActionFastQueue) {
            return unitActionFastQueue.stream().anyMatch(action ->
                    java.util.Arrays.stream(action.getUnitIds()).anyMatch(id -> id == entityId));
        }
    }

    public static void addActionItem(
        String ownerName,
        UnitAction action,
        int unitId,
        int[] unitIds,
        BlockPos preselectedBlockPos,
        BlockPos selectedBuildingPos
    ) {
        addActionItem(ownerName, action, unitId, unitIds, preselectedBlockPos, selectedBuildingPos, false);
    }

    public static void addActionItem(
        String ownerName,
        UnitAction action,
        int unitId,
        int[] unitIds,
        BlockPos preselectedBlockPos,
        BlockPos selectedBuildingPos,
        boolean shiftQueue
    ) {
        if (shiftQueue) {
            synchronized(unitActionSlowQueue) {
                for (int actionableUnitId : unitIds) {
                    unitActionSlowQueue.add(
                        new UnitActionItem(ownerName,
                                action,
                                unitId,
                                new int[] {actionableUnitId},
                                preselectedBlockPos,
                                selectedBuildingPos
                        )
                    );
                    //System.out.println("added item to shiftQueue: " + action.name() + "|" + actionableUnitId + "|" + preselectedBlockPos);
                }
            }
        } else {
            synchronized(unitActionSlowQueue) {
                for (int actionableUnitId : unitIds)
                    unitActionSlowQueue.removeIf(uai -> uai.getUnitIds().length > 0 && uai.getUnitIds()[0] == actionableUnitId);
            }
            synchronized (unitActionFastQueue) {
                UnitActionItem uai = new UnitActionItem(ownerName,
                        action,
                        unitId,
                        unitIds,
                        preselectedBlockPos,
                        selectedBuildingPos
                );
                // Suppress only a consecutive duplicate. Comparing the first item
                // also discarded the final A in same-tick MOVE A -> B -> A.
                boolean duplicateMove = action == UnitAction.MOVE && !unitActionFastQueue.isEmpty()
                        && unitActionFastQueue.get(unitActionFastQueue.size() - 1).equals(uai);
                if (!duplicateMove) unitActionFastQueue.add(uai);
            }
        }
    }

    public static Relationship getUnitToEntityRelationship(Unit unit, Level level, int unitId) {
        return getUnitToEntityRelationship(unit, level.getEntity(unitId));
    }

    public static Relationship getRl(Unit unit, Entity entity) {
        return getUnitToEntityRelationship(unit, entity);
    }

    public static Relationship getUnitToEntityRelationship(Unit unit, Entity entity) {
        String ownerName1 = unit.getOwnerName();
        String ownerName2 = "";

        if (entity instanceof ItemEntity item && item.getOwner() instanceof Unit unitItemOwner) {
            ownerName2 = unitItemOwner.getOwnerName();
        } else if (entity instanceof Player player) {
            ownerName2 = player.getName().getString();
        } else if (entity instanceof Unit) {
            ownerName2 = ((Unit) entity).getOwnerName();
        } else {
            return Relationship.NEUTRAL;
        }

        // Check if the owners are allied first
        if (AlliancesServerEvents.isAllied(ownerName1, ownerName2)) {
            return Relationship.FRIENDLY;
        }
        // If not allied, check if the owners are the same
        if (ownerName1.equals(ownerName2)) {
            return Relationship.FRIENDLY;
        } else if (ownerName1.isBlank() || ownerName2.isBlank()) {
            return Relationship.NEUTRAL;
        } else {
            return Relationship.HOSTILE;
        }
    }
    // similar to UnitClientEvents getUnitRelationship: given a Unit and Entity, what is the relationship between them
    public static Relationship getUnitToBuildingRelationship(Unit unit, BuildingPlacement building) {
        String unitOwnerName = unit.getOwnerName();
        String buildingOwnerName = building.ownerName;

        if (unitOwnerName.equals(buildingOwnerName)) {
            return Relationship.OWNED;
        } else if (buildingOwnerName.isBlank() || unitOwnerName.isBlank()) {
            return Relationship.NEUTRAL;
        } else if (AlliancesServerEvents.isAllied(unitOwnerName, buildingOwnerName)) {
            return Relationship.FRIENDLY;
        } else {
            return Relationship.HOSTILE;
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (evt.getEntity() instanceof LivingEntity le &&
                (ResourceSources.isHuntableAnimal(le) || le instanceof PhantomSummon || le instanceof Unit))
            addUnitPoofs(evt.getLevel(), le);

        if (evt.getEntity() instanceof Unit && evt.getEntity() instanceof Mob mob) {
            mob.setBaby(false);
            mob.setPathfindingMalus(BlockPathTypes.WATER, -1.0f);
            mob.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 1.0f);
            mob.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 1.0f);
            mob.setPathfindingMalus(BlockPathTypes.STICKY_HONEY, 1.0f);
        }

        // for some reason some units need to be nudged a little on spawn or they can't move
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof Unit && evt.getEntity() instanceof LivingEntity le) {
            boolean bool1 = le.getRandom().nextBoolean();
            boolean bool2 = le.getRandom().nextBoolean();
            evt.getEntity().push(0.005d * (bool1 ? -1 : 1), 0, 0.005d * (bool2 ? -1 : 1));
        }

        if (evt.getEntity() instanceof Unit unit && evt.getEntity() instanceof LivingEntity entity
            && !evt.getLevel().isClientSide) {
            allUnits.add(entity);

            ((Unit) entity).setupEquipmentAndUpgradesServer();

            boolean isChristmas = MiscUtil.isChristmasSeason();
            boolean isWearingPumpkin = entity.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.CARVED_PUMPKIN;
            if (isChristmas && MiscUtil.canWearChristmasHat(entity)) {
                entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            } else if (isWearingPumpkin) {
                entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.AIR));
            }

            ChunkAccess chunk = evt.getLevel().getChunk(entity.getOnPos());
            ForgeChunkManager.forceChunk((ServerLevel) evt.getLevel(),
                ReignOfNether.MOD_ID,
                entity,
                chunk.getPos().x,
                chunk.getPos().z,
                true,
                true
            );
            forcedUnitChunks.put(entity.getId(), chunk);
            forcedUnitChunkOwners.put(entity.getId(), entity);
            UnitChunkTickets previous = unitChunkTickets.put(entity, new UnitChunkTickets(entity, (ServerLevel) evt.getLevel(), chunk));
            if (previous != null) releaseNeighborhood(previous);
        }

        if (evt.getEntity() instanceof Projectile proj) {
            proj.getPersistentData().putFloat("accuracyRoll", RANDOM.nextFloat());
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent evt) {
        discardPendingFormationMove(evt.getEntity());
        if (isServerStopping) return;
        if (evt.getEntity() instanceof LivingEntity rollback && (uncommittedSpawnRollbacks.contains(rollback) || transformedRetirements.contains(rollback))) {
            // Persistent-ticket/population cleanup already happened. Retire any runtime footprint too.
            retireUnitChunkTickets(rollback, true);
            UnitSyncClientboundPacket.sendLeavePacket(rollback);
            return;
        }

        if (evt.getEntity() instanceof Unit && evt.getEntity() instanceof LivingEntity entity
            && !evt.getLevel().isClientSide) {

            // Removal callbacks can run after native entity lookup retirement. Use captured exact ownership,
            // not a new world/chunk read or just a reusable numeric entity ID.
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (reason != null && reason.shouldDestroy()) discardPendingGatherTarget(entity);
            retireUnitChunkTickets(entity, reason != null && (reason.shouldDestroy()
                    || reason == Entity.RemovalReason.CHANGED_DIMENSION));
            allUnits.removeIf(e -> e == entity);
            UnitSyncClientboundPacket.sendLeavePacket(entity);
        }

        // if a player has no more units, then they are defeated
        synchronized (allUnits) {
            try {
                if (evt.getEntity() instanceof Unit unit) {
                    var unitsOwned = 0;
                    for (LivingEntity u : allUnits) {
                        if ((u instanceof Unit unit1 && unit1.getOwnerName().equals(unit.getOwnerName()))) unitsOwned++;
                    }
                    if (!SandboxServer.isSandboxPlayer(unit.getOwnerName()) &&
                            unitsOwned == 0 && isRTSPlayer(unit.getOwnerName())
                            && BuildingUtils.getTotalCompletedBuildingsOwned(false, unit.getOwnerName()) == 0) {
                        PlayerServerEvents.defeat(unit.getOwnerName(), Component.translatable("server.reignofnether.lost_all").getString());
                    }
                }
            } catch (ConcurrentModificationException e) {
                ReignOfNether.LOGGER.error("Caught ConcurrentModificationException in UnitServerEvents EntityLeaveLevelEvent", e);
            }
        }
    }

    // Old native death effects predate ConfirmedRtsKillEvent and also need a final-death boundary.
    // Do not pay/spawn/drop/mutate terrain from LivingDeathEvent: any later listener may cancel it.
    private static final List<PendingNativeDeath> pendingNativeDeaths = new ArrayList<>();
    private static final Map<LivingEntity, Boolean> committedNativeDeaths = new WeakHashMap<>();
    private static final ThreadLocal<NativeDeathDispatch> nativeDeathDispatch = new ThreadLocal<>();

    /** Exact stack frame around vanilla die's one ForgeHooks.onLivingDeath call. Nested deaths
     * cannot steal another hit's event or complete a cancelled outer death. */
    public static final class NativeDeathDispatch implements AutoCloseable {
        private final LivingEntity victim;
        private final net.minecraft.world.damagesource.DamageSource source;
        private final NativeDeathDispatch previous;
        private PendingNativeDeath pending;
        private boolean finished;
        private NativeDeathDispatch(LivingEntity victim,net.minecraft.world.damagesource.DamageSource source) {
            this.victim=victim;this.source=source;previous=nativeDeathDispatch.get();nativeDeathDispatch.set(this);
        }
        public void finish(boolean canceled) {
            if (finished) return;
            finished=true;
            if (pending!=null) {
                pendingNativeDeaths.remove(pending);
                if (!canceled) confirmNativeDeath(pending);
            }
        }
        @Override public void close() {
            // A thrown Forge listener has no final uncancelled result; never replay its partial event.
            if (!finished && pending!=null) pendingNativeDeaths.remove(pending);
            if (previous==null) nativeDeathDispatch.remove();else nativeDeathDispatch.set(previous);
        }
    }

    public static NativeDeathDispatch beginNativeDeathDispatch(LivingEntity victim,net.minecraft.world.damagesource.DamageSource source) {
        return new NativeDeathDispatch(victim,source);
    }


    private static final class PendingNativeDeath {
        final LivingDeathEvent event;
        final LivingEntity victim, lastHurtByMob;
        final ServerLevel level;
        final int tick;
        final UUID uuid;
        final Vec3 position;
        final BlockPos blockPos;
        final float yRot;
        final String victimOwner, deathCommand;
        final List<ItemStack> cargo;
        final Map<MobEffect,MobEffectInstance> effects = new HashMap<>();
        final com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity.Stamp victimStamp, killerStamp, infectionStamp;
        final ResourceCost cost;
        final boolean slimeConversion, itemEconomy;
        final float bountyPercent;
        PendingNativeDeath(LivingDeathEvent event,ServerLevel level) {
            this.event=event;this.level=level;victim=event.getEntity();tick=level.getServer().getTickCount();
            uuid=victim.getUUID();position=victim.position();blockPos=victim.getOnPos().immutable();yRot=victim.getYRot();
            lastHurtByMob=victim.getLastHurtByMob();
            for (MobEffectInstance effect:victim.getActiveEffects()) effects.put(effect.getEffect(),new MobEffectInstance(effect));
            if (victim instanceof Unit unit) {
                victimStamp=com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity.capture(victim);
                victimOwner=unit.getOwnerName();deathCommand=unit.getOnDeathCommand();
                cargo=unit.getItems().stream().map(ItemStack::copy).toList();
                ResourceCost original=unit.getCost();cost=ResourceCost.Unit(original.food,original.wood,original.ore,0,original.population);
            } else { victimStamp=null;victimOwner="";deathCommand="";cargo=List.of();cost=null; }
            killerStamp=event.getSource().getEntity() instanceof LivingEntity killer && killer instanceof Unit
                    ?com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity.capture(killer):null;
            infectionStamp=lastHurtByMob instanceof Unit
                    ?com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity.capture(lastHurtByMob):null;
            slimeConversion=infectionStamp!=null&&ResearchServerEvents.playerHasResearch(infectionStamp.owner(),ProductionItems.RESEARCH_SLIME_CONVERSION);
            itemEconomy=ItemServerEvents.ENABLED;
            float percent=0;
            if (cost!=null&&killerStamp!=null) {
                if (victimOwner.isEmpty()) percent=NEUTRAL_UNIT_BOUNTY_PERCENT;
                else if (!AlliancesServerEvents.isAlliedOrOwned(victimOwner,killerStamp.owner()))
                    percent=killerStamp.entity().getMainHandItem().getEnchantmentLevel(Enchantments.MOB_LOOTING)*UNIT_BOUNTY_PERCENT_PER_LOOTING_LEVEL;
            }
            bountyPercent=percent;
        }
        boolean confirmed() {
            if (event.isCanceled() || victim.getHealth()>0 || !victim.isDeadOrDying()
                    || victim.level()!=level || !victim.getUUID().equals(uuid)
                    || !victim.isRemoved() && (level.getEntity(uuid)!=victim || level.getEntity(victim.getId())!=victim)
                    || level.getEntity(uuid)!=null && level.getEntity(uuid)!=victim
                    || victim.isRemoved() && victim.getRemovalReason()!=Entity.RemovalReason.KILLED) return false;
            // Only the native one-way defeat clear may occur after this body's legitimate death.
            return victimStamp==null || victimStamp.sameOwner() || victimStamp.clearedOnceByDefeat();
        }
    }

    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST,receiveCanceled=true)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && !committedNativeDeaths.containsKey(event.getEntity())) {
            PendingNativeDeath pending=new PendingNativeDeath(event,level);
            pendingNativeDeaths.add(pending);
            NativeDeathDispatch dispatch=nativeDeathDispatch.get();
            if (dispatch!=null && dispatch.victim==event.getEntity() && dispatch.source==event.getSource())
                dispatch.pending=pending;
        }
    }

    private static void confirmNativeDeath(PendingNativeDeath pending) {
        if (!pending.confirmed() || committedNativeDeaths.containsKey(pending.victim)) return;
        committedNativeDeaths.put(pending.victim,true); // before any reentrant spawn, reward or command
        // Preserve native synchronous revival eligibility and snapshot hero state before legacy
        // effects (especially Creeper explosion, defeat and scenario commands) can mutate it.
        HeroServerEvents.commitConfirmedDeath(pending.event);
        ItemServerEvents.commitConfirmedDeath(pending.event);
        commitNativeDeath(pending);
    }

    @SubscribeEvent
    public static void onNativeDeathAliveTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity().isAlive())
            committedNativeDeaths.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onNativeDeathConfirmationTick(TickEvent.ServerTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        List<PendingNativeDeath> ready=new ArrayList<>();
        pendingNativeDeaths.removeIf(p -> {
            if (p.level.getServer()!=event.getServer() || p.tick==event.getServer().getTickCount()) return false;
            ready.add(p);return true;
        });
        for (PendingNativeDeath pending:ready) {
            // Compatibility for a third-party native death implementation which posts Forge itself:
            // require actual terminal progression, not merely a fabricated event on a living body.
            if (pending.victim.deathTime>0 || pending.victim.getRemovalReason()==Entity.RemovalReason.KILLED)
                confirmNativeDeath(pending);
        }
    }

    private static void commitNativeDeath(PendingNativeDeath pending) {
        LivingDeathEvent evt=pending.event;
        // Ignore a changed/ABA attacker without losing the victim's genuine cargo/script death.
        boolean killerValid=pending.killerStamp==null||pending.killerStamp.sameOwner();
        boolean infectionValid=pending.infectionStamp==null||pending.infectionStamp.sameOwner();
        for (BuildingPlacement building:BuildingServerEvents.getBuildings()) {
            if (building instanceof SculkCatalystPlacement sc && building.getLevel()==pending.level
                    && pending.position.distanceToSqr(Vec3.atCenterOf(sc.centrePos))<SculkCatalyst.ESTIMATED_RANGE*SculkCatalyst.ESTIMATED_RANGE) {
                BlockPos bp=pending.blockPos;
                if (pending.level.getBlockState(bp.above()).getBlock() instanceof IPlantable) pending.level.destroyBlock(bp.above(),false);
                for (int x=-3;x<=3;x++) for (int y=-3;y<=3;y++) for (int z=-3;z<=3;z++) {
                    BlockPos target=bp.offset(x,y,z);
                    if (target.distManhattan(bp)<=3 && pending.level.getBlockState(target).is(Blocks.DIRT_PATH))
                        pending.level.setBlockAndUpdate(target,Blocks.DIRT.defaultBlockState());
                }
            }
        }
        // drop all resources held
        if (evt.getEntity() instanceof Unit unit) {
            List<ItemStack> itemStacks = pending.cargo;
            for (ItemStack itemStack : itemStacks)
                evt.getEntity().spawnAtLocation(itemStack.copy());
            if (!unit.getItems().isEmpty()) unit.getItems().clear();
        }

        // for some reason, if we discard() creepers en masse via exploding them,
        // /rts-reset fails to run
        if (evt.getEntity() instanceof CreeperUnit creeperUnit &&
            !PlayerServerEvents.rtsPlayers.isEmpty()) {
            creeperUnit.explodeCreeper();
        }

        LivingEntity lastHurtByMob = pending.lastHurtByMob;

        boolean drownedInfected = pending.effects.containsKey(MobEffectRegistrar.ZOMBIE_INFECTED.get()) ||
                lastHurtByMob instanceof DrownedUnit;
        boolean slimeInfected = (pending.effects.containsKey(MobEffectRegistrar.SLIME_INFECTED.get()) ||
                ((lastHurtByMob instanceof SlimeUnit) && !(lastHurtByMob instanceof MagmaCubeUnit)));
        boolean lastHurtByAlly = lastHurtByMob instanceof Unit unit && evt.getEntity() instanceof Unit unit2 &&
                AlliancesServerEvents.isAlliedOrOwned(unit.getOwnerName(), pending.victimOwner);

        if (infectionValid && lastHurtByMob instanceof Unit unit && (drownedInfected || slimeInfected) && !lastHurtByAlly) {

            EntityType<? extends Unit> entityType = null;

            if (drownedInfected) {
                if (evt.getEntity() instanceof GruntUnit || evt.getEntity() instanceof BruteUnit
                        || evt.getEntity() instanceof HeadhunterUnit || evt.getEntity() instanceof MarauderUnit) {
                    entityType = EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get();
                } else if (evt.getEntity() instanceof HoglinUnit) {
                    entityType = EntityRegistrar.ZOGLIN_UNIT.get();
                } else if (evt.getEntity() instanceof VillagerUnit || evt.getEntity() instanceof MilitiaUnit) {
                    entityType = EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get();
                } else if (evt.getEntity() instanceof VindicatorUnit || evt.getEntity() instanceof PillagerUnit || evt.getEntity() instanceof EvokerUnit ||
                        evt.getEntity() instanceof WitchUnit || evt.getEntity() instanceof WindcallerUnit) {
                    entityType = EntityRegistrar.DROWNED_UNIT.get();
                }
            }
            if (slimeInfected && entityType == null && pending.slimeConversion) {
                entityType = EntityRegistrar.SLIME_UNIT.get();
            }

            if (entityType != null && evt.getEntity().level() instanceof ServerLevel serverLevel) {
                Entity entity = entityType.spawn(serverLevel,
                        (CompoundTag) null,
                    spawned -> com.solegendary.reignofnether.resources.kill.RtsKillValueClaims.markFree(spawned,
                            com.solegendary.reignofnether.resources.kill.RtsKillValueClaims.FreeOrigin.NATIVE_INFECTION),
                    pending.blockPos,
                    MobSpawnType.SPAWNER,
                    true,
                    false
                );
                if (entity instanceof SlimeUnit sUnit && evt.getEntity() instanceof Unit originalEntity) {
                    sUnit.setSize(Mth.clamp(pending.cost.population - 1, 1, 5), true);
                }
                if (entity instanceof Unit convertedUnit) {
                    convertedUnit.setOwnerName(unit.getOwnerName());
                    entity.setYRot(pending.yRot);
                }
            }
        }

        if (killerValid && evt.getSource().getEntity() instanceof VillagerUnit vUnit &&
            ResourceSources.isHuntableAnimal(evt.getEntity())) {
            vUnit.incrementHunterExp();
            if (!(evt.getEntity() instanceof Chicken))
                vUnit.incrementHunterExp();
        }

        if (killerValid && evt.getEntity() instanceof Unit unitKilled && evt.getSource().getEntity() instanceof Unit unit) {
            float bountyPercent = pending.bountyPercent;
            if (bountyPercent > 0) {

                ResourceCost cost = pending.cost;
                Resources resources;
                int food = (int) (cost.food * bountyPercent);
                int wood = (int) (cost.wood * bountyPercent);
                int ore =  (int) (cost.ore * bountyPercent);
                if (pending.itemEconomy) {
                    resources = Resources.emeralds(unit.getOwnerName(), food + wood + ore);
                } else {
                    resources = new Resources(unit.getOwnerName(), food, wood, ore);
                }
                if (resources.getTotalValue() > 0) {
                    ResourcesClientboundPacket.showFloatingText(resources, pending.blockPos);
                    ResourcesServerEvents.addSubtractResources(resources);
                }
            }
        }

        if (!(evt.getEntity() instanceof NecromancerUnit) && !evt.getEntity().level().isClientSide()) {
            Vec3 pos = pending.position;
            List<NecromancerUnit> necromancers = MiscUtil.getEntitiesWithinRange(
                    new Vector3d(pos.x, pos.y, pos.z),
                    SoulSiphonPassive.RANGE,
                    NecromancerUnit.class,
                    evt.getEntity().level());

            for (NecromancerUnit necromancerUnit : necromancers) {
                SoulSiphonPassive soulSiphon = necromancerUnit.getSoulSiphon();
                if (soulSiphon != null) {
                    if (soulSiphon.soulsMax > 0 && pending.cost != null && !pending.victimOwner.equals(necromancerUnit.getOwnerName()))
                        necromancerUnit.souls += pending.cost.population / necromancers.size();
                    necromancerUnit.souls = Math.min(necromancerUnit.souls,soulSiphon.soulsMax);
                    AbilityClientboundPacket.doAbility(necromancerUnit.getId(), UnitAction.SOUL_SIPHON_UPDATE, necromancerUnit.souls);
                }
            }
        }

        if (pending.effects.containsKey(MobEffectRegistrar.SCORCHING_FIRE.get())) {
            List<Mob> mobs = MiscUtil.getEntitiesWithinRange(pending.position, ScorchingGaze.SPREAD_RANGE, Mob.class, evt.getEntity().level());
            ArrayList<Mob> friendlyUnits = new ArrayList<>();
            for (Mob mob : mobs) {
                if (mob instanceof Unit unit1 && evt.getEntity() instanceof Unit unit2 &&
                    getUnitToEntityRelationship(unit1, evt.getEntity()) == Relationship.FRIENDLY && unit1 != unit2) {
                    friendlyUnits.add((Mob) unit1);
                }
            }
            friendlyUnits.sort(Comparator.comparing(le -> le.position().distanceToSqr(pending.position)));
            if (!friendlyUnits.isEmpty()) {
                int durationSeconds = pending.effects.get(MobEffectRegistrar.SCORCHING_FIRE.get()).getAmplifier() - 2;
                int durationTicks = durationSeconds * 20;
                if (durationSeconds > 0 && friendlyUnits.get(0).addEffect(new MobEffectInstance(MobEffectRegistrar.SCORCHING_FIRE.get(), durationTicks, durationSeconds))) {
                    ParticleUtil.addParticleExplosion(ParticleTypes.LAVA, 12, evt.getEntity().level(), pending.position);
                    SoundClientboundPacket.playSoundAtPos(SoundAction.WILDFIRE_SCORCHING_GAZE_END, friendlyUnits.get(0).blockPosition());
                    friendlyUnits.get(0).addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks,0, true, true));
                    if (pending.effects.containsKey(MobEffectRegistrar.SOULS_AFLAME.get())) {
                        friendlyUnits.get(0).addEffect(new MobEffectInstance(MobEffectRegistrar.SOULS_AFLAME.get(), durationTicks + 20, 0, true, true));
                    }
                }
            }
        }

        if (evt.getEntity() instanceof Unit unit) {
            for (Mob mob : MiscUtil.getEntitiesWithinRange(pending.position, UnitItems.SOUL_COLLECTOR_RADIUS, Mob.class, evt.getEntity().level())) {
                if (mob instanceof HeroUnit heroUnit && mob instanceof UnitInventory inv && inv.isHolding(UnitItems.SOUL_COLLECTOR)) {
                    float manaRestored = (pending.cost.population * 2) + 3;
                    heroUnit.setMana(heroUnit.getMana() + manaRestored);
                    ParticleUtil.addParticleExplosion(ParticleRegistrar.MANA.get(), (int) (manaRestored / 3), mob.level(), mob.getEyePosition());
                }
            }
        }

        runNativeDeathCommand(pending.victim,pending.level,pending.position,pending.deathCommand);
    }

    private static void runNativeDeathCommand(LivingEntity body,ServerLevel level,Vec3 position,String command) {
        if (command==null||command.isEmpty()) return;
        var source=level.getServer().createCommandSourceStack().withEntity(body)
                .withPosition(position).withLevel(level).withPermission(2);
        level.getServer().getCommands().performPrefixedCommand(source,command);
    }

    /** Exact completed native self-explosion is a terminal gameplay action, unlike conversion discard. */
    public static void onCompletedCreeperSelfExplosion(CreeperUnit creeper) {
        if (!(creeper.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !creeper.isRemoved() || creeper.getRemovalReason()!=Entity.RemovalReason.DISCARDED
                || committedNativeDeaths.containsKey(creeper)) return;
        committedNativeDeaths.put(creeper,true);
        runNativeDeathCommand(creeper,level,creeper.position(),creeper.getOnDeathCommand());
    }

    // prevent onDropItem firing twice if the same animal is killed by two workers on the same tick
    private static int lastHuntedAnimalId = -1;

    // animal hunting
    @SubscribeEvent
    public static void onDropItem(LivingDropsEvent evt) {
        if (ResourceSources.isHuntableAnimal(evt.getEntity()) && !evt.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO) && evt.getSource()
            .getEntity() instanceof Unit unit && evt.getSource().getEntity() instanceof WorkerUnit && evt.getSource()
            .getEntity() instanceof Mob mob && mob.canPickUpLoot()) {

            if (!Unit.atMaxResources(unit))
                evt.setCanceled(true);

            if (lastHuntedAnimalId != evt.getEntity().getId()) {

                if (!Unit.atMaxResources(unit)) {
                    for (ItemStack itemStack : ResourceSources.getFoodItemsFromAnimal((Animal) evt.getEntity())) {
                        ResourceSource res = ResourceSources.getFromItem(itemStack.getItem());
                        if (res != null)
                            unit.getItems().add(itemStack);
                    }
                }

                // insert a drop-off command without disrupting other queued commands
                /*
                if (Unit.atThresholdResources(unit)) {
                    int unitId = ((Mob) unit).getId();
                    boolean hasDropOffCommandQueued = false;
                    for (UnitActionItem uai : unitActionSlowQueue) {
                        for (int id : uai.getUnitIds()) {
                            if (id == unitId && (
                                uai.getAction() == UnitAction.RETURN_RESOURCES_TO_CLOSEST ||
                                uai.getAction() == UnitAction.RETURN_RESOURCES)) {
                                hasDropOffCommandQueued = true;
                                break;
                            }
                        }
                    }
                    if (!hasDropOffCommandQueued) {
                        unitActionSlowQueue.add(0, new UnitActionItem(
                                unit.getOwnerName(),
                                UnitAction.RETURN_RESOURCES_TO_CLOSEST,
                                -1,
                                new int[]{((Entity) unit).getId()}
                        ));
                    }
                }

                 */
            } else {
                lastHuntedAnimalId = evt.getEntity().getId();
            }
        }
    }

    @SubscribeEvent
    public static void onFormationDispatchTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide() || evt.level.dimension() != Level.OVERWORLD)
            return;
        synchronized (formationDispatchQueue) {
            if (formationDispatchQueue.isEmpty())
                return;
            int processed = 0;
            Iterator<FormationOrder> it = formationDispatchQueue.values().iterator();
            while (processed < FORMATION_DISPATCH_PER_TICK && it.hasNext()) {
                FormationOrder order = it.next();
                processed += 1;
                // A queued immediate command must run first; its accepted-command hook invalidates
                // this lease. Invalid/foreign actions merely postpone it until authorization finishes.
                if (order.valid(evt.level) && hasPendingImmediateAction(order.id())) continue;
                it.remove();
                LivingEntity le = order.unit();
                if (order.valid(evt.level) && le instanceof Unit unit && unit.getMoveGoal() != null)
                    unit.getMoveGoal().setMoveTarget(order.target());
            }
        }
    }

    // for some reason we have to use the level in the same tick as the unit actions or else level.getEntity returns
    // null
    // remember to always reset targets so that users' actions always overwrite any existing action
    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide() || evt.level.dimension() != Level.OVERWORLD) {
            return;
        }
        unitSyncTicks -= 1;
        if (unitSyncTicks <= 0) {
            unitSyncTicks = UNIT_SYNC_TICKS_MAX;
            UnitIdleWorkerClientBoundPacket.sendIdleWorkerPacket();

            for (LivingEntity entity : allUnits) {
                if (entity instanceof Unit unit && evt.level.getServer() != null) {
                    UnitSyncClientboundPacket.sendSyncResourcesPacket(unit);
                    UnitSyncClientboundPacket.sendSyncStatsPacket(evt.level.getServer().getPlayerList().getPlayers(), entity);

                    if (unit.getAnchor() != null)
                        UnitSyncClientboundPacket.sendSyncAnchorPosPacket(entity, unit.getAnchor());
                    else
                        UnitSyncClientboundPacket.sendRemoveAnchorPosPacket(entity);
                    if (entity instanceof VillagerUnit vUnit && vUnit.isVeteran())
                        UnitSyncClientboundPacket.makeVillagerVeteran(vUnit);
                }
                if (entity instanceof WorkerUnit) {
                    UnitSyncWorkerClientBoundPacket.sendSyncWorkerPacket(entity);
                }
                if (entity instanceof UnitInventory inv) {
                    ItemClientboundPacket.syncInventory(entity.getId(), inv.getAllItems());
                }

                // Per-unit tick maintains exact-lifetime persistent and runtime neighborhood tickets.

            }
        }
        synchronized (unitActionSlowQueue) {
            UnitActionItem actionedItem = null;

            for (UnitActionItem uai : unitActionSlowQueue) {
                if (uai.getUnitIds().length > 0) {
                    int queuedUnitId = uai.getUnitIds()[0];
                    // A MOVE followed by a shift-queued MOVE can arrive in the same
                    // server tick. Do not consume the queued leg while the unit is
                    // still idle, before its pending immediate order is dispatched.
                    if (hasPendingImmediateAction(queuedUnitId)) continue;
                    Entity entity = evt.level.getEntity(queuedUnitId);
                    if (hasPendingFormationMove(entity)) continue;
                    if (entity instanceof Unit unit && unit.isIdle()) {
                        uai.action(evt.level);
                        actionedItem = uai;
                        //System.out.println("actioned item from queue: " + uai.getAction().name() + "|" + uai.getUnitIds()[0] + "|" + uai.getPreselectedBlockPos());
                        break;
                    }
                }
            }
            if (actionedItem != null)
                unitActionSlowQueue.remove(actionedItem);
        }
        synchronized (unitActionFastQueue) {
            for (UnitActionItem actionItem : unitActionFastQueue)
                actionItem.action(evt.level);
            unitActionFastQueue.clear();
        }
    }

    @SubscribeEvent
    // assign unit owner when spawned with an egg based on whoever is closest
    public static void onMobSpawn(MobSpawnEvent.FinalizeSpawn evt) {
        if (!evt.getSpawnType().equals(MobSpawnType.SPAWN_EGG)) {
            return;
        }

        Entity entity = evt.getEntity();
        if (evt.getEntity() instanceof Unit) {

            Vec3 pos = entity.position();
            List<Player> nearbyPlayers = MiscUtil.getEntitiesWithinRange(new Vector3d(pos.x, pos.y, pos.z),
                10,
                Player.class,
                evt.getEntity().level()
            );

            float closestPlayerDist = 10;
            Player closestPlayer = null;
            for (Player player : nearbyPlayers) {
                if (player.distanceTo(entity) < closestPlayerDist && isRTSPlayer(player.getName().getString())) {
                    closestPlayerDist = player.distanceTo(entity);
                    closestPlayer = player;
                }
            }
            if (closestPlayer != null) {
                ((Unit) entity).setOwnerName(closestPlayer.getName().getString());
            }
        }
    }

    private static boolean shouldIgnoreKnockback(LivingDamageEvent evt) {
        Entity directEntity = evt.getSource().getDirectEntity();
        Entity sourceEntity = evt.getSource().getEntity();

        if (sourceEntity instanceof LivingEntity le && (le.getMainHandItem().getEnchantmentLevel(Enchantments.PUNCH_ARROWS) > 0)) {
            return false;
        }

        if (sourceEntity instanceof WretchedWraithUnit)
            return true;
        if (sourceEntity instanceof WraithUnit)
            return true;
        if (sourceEntity instanceof SlimeUnit slimeUnit && slimeUnit.isTiny())
            return true;
        if (directEntity instanceof Fireball && sourceEntity instanceof BlazeUnit)
            return true;
        if (directEntity instanceof AbstractArrow)
            return true;
        if (directEntity instanceof WindcallerProjectile)
            return true;
        if (directEntity instanceof BlazeUnitFireball)
            return true;
        if (sourceEntity instanceof WorkerUnit &&
            sourceEntity instanceof Mob mob &&
            ResourceSources.isHuntableAnimal(mob.getTarget()))
            return true;

        return evt.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO) && evt.getSource().isIndirect()
                && (!(sourceEntity instanceof EvokerUnit));
    }

    public static Entity spawnMob(
            EntityType<? extends Mob> entityType, ServerLevel level, Vec3i pos, String ownerName
    ) {
        ArrayList<Entity> entities = UnitServerEvents.spawnMobs(entityType, level, pos,1, ownerName);
        if (entities.isEmpty())
            return null;
        else {
            if (entities.get(0) instanceof SlimeUnit slimeUnit)
                slimeUnit.setSize(2, true);
            return entities.get(0);
        }

    }

    public static ArrayList<Entity> spawnMobs(
        EntityType<? extends Mob> entityType, ServerLevel level, Vec3i pos, int qty, String ownerName
    ) {
        ArrayList<Entity> entities = new ArrayList<>();
        if (level != null) {
            for (int i = 0; i < qty; i++) {
                Entity entity = entityType.create(level);
                if (entity != null) {
                    entity.moveTo(
                            pos.above().getX() + 0.5f + i,
                            pos.above().getY(),
                            pos.above().getZ() + 0.5f
                    );
                    entities.add(entity);
                    if (entity instanceof Unit unit) {
                        unit.setOwnerName(ownerName);
                    }
                    level.addFreshEntity(entity);
                }
            }
        }
        return entities;
    }

    @SubscribeEvent
    public static void onEntityDamaged(LivingDamageEvent evt) {

        if (evt.getEntity() instanceof WretchedWraithUnit wraith && wraith.isFrostBlinkInProgress()) {
            evt.setCanceled(true);
        }

        if (shouldIgnoreKnockback(evt)) {
            knockbackIgnoreIds.add(evt.getEntity().getId());
        }

        // halve friendly fire from your own/friendly creepers (but still cause knockback)
        if (evt.getSource().getEntity() instanceof CreeperUnit creeperUnit &&
                getUnitToEntityRelationship(creeperUnit, evt.getEntity()) == Relationship.FRIENDLY) {
            evt.setAmount(evt.getAmount() / 2);

            if (evt.getEntity() instanceof CreeperUnit)
                evt.setAmount(evt.getAmount() / 2);
        }

        if (evt.getEntity() instanceof Unit && (
            evt.getSource() == evt.getEntity().damageSources().sweetBerryBush() || evt.getSource() == evt.getEntity().damageSources().cactus()
        )) {
            evt.setCanceled(true);
            return;
        }

        if (evt.getEntity().hasEffect(MobEffectRegistrar.INVINCIBLE.get())) {
            evt.setCanceled(true);
            return;
        }

        // halve direct ghast damage since they get bonus damage from launching units into the air
        if (evt.getSource().getEntity() instanceof GhastUnit) {
            // (unless its to a garrisoned unit)
            if (!(evt.getEntity() instanceof Unit unit && GarrisonableBuildingAddon.getGarrison(unit) != null)) {
                evt.setAmount(evt.getAmount() / 2);
            }
        }

        // ignore added weapon damage for workers
        if (evt.getSource().getEntity() instanceof WorkerUnit && evt.getSource()
            .getEntity() instanceof AttackerUnit attackerUnit) {
            evt.setAmount(attackerUnit.getUnitAttackDamage());
        }

        if (evt.getSource() == evt.getEntity().damageSources().lightningBolt()) {
            if (evt.getEntity() instanceof CreeperUnit) {
                evt.setCanceled(true);
            } else {
                evt.setAmount(evt.getAmount() / 2);
            }
        }

        if (evt.getEntity() instanceof Unit && (evt.getSource() == evt.getEntity().damageSources().inWall())) {
            evt.setCanceled(true);
        }

        // prevent friendly fire damage from ranged units (unless specifically targeted)
        if (evt.getSource().is(DamageTypeTags.IS_PROJECTILE) && evt.getSource().getEntity() instanceof Unit unit) {
            if (getUnitToEntityRelationship(unit, evt.getEntity()) == Relationship.FRIENDLY
                && unit.getTargetGoal().getTarget() != evt.getEntity()) {
                evt.setCanceled(true);
            }
        }

        MinecraftServer server = evt.getEntity().level().getServer();
        if (evt.getEntity().getAbsorptionAmount() > 0 && server != null)
            UnitSyncClientboundPacket.sendSyncStatsPacket(server.getPlayerList().getPlayers(), evt.getEntity());

        if (evt.getSource().getEntity() instanceof HeadhunterUnit headhunterUnit &&
                headhunterUnit.hasFlameTrident() &&
                evt.getAmount() > 0)
            evt.getEntity().setSecondsOnFire(4);

        if (evt.getSource().getEntity() instanceof LivingEntity le) {
            int breachLevel = le.getMainHandItem().getEnchantmentLevel(EnchantmentRegistrar.BREACHING.get());
            MobEffectInstance existingDmgIncrease = evt.getEntity().getEffect(MobEffectRegistrar.DAMAGE_TAKEN_INCREASE.get());
            if (breachLevel > 0) {
                int amp = existingDmgIncrease != null ? (breachLevel * 2) + existingDmgIncrease.getAmplifier() : Math.max(0, (breachLevel * 2) - 1);
                evt.getEntity().addEffect(new MobEffectInstance(MobEffectRegistrar.DAMAGE_TAKEN_INCREASE.get(), 100, amp));
            }
        }
        if (evt.getSource().getEntity() instanceof Vex vex && vex.getOwner() instanceof EvokerUnit evokerUnit) {
            int zealLevel = evokerUnit.getMainHandItem().getEnchantmentLevel(EnchantmentRegistrar.ZEAL.get());
            if (zealLevel > 0) {
                evt.setAmount(evt.getAmount() + zealLevel);
            }
        }
        if (evt.getSource().getEntity() instanceof EvokerUnit evokerUnit) {
            int zealLevel = evokerUnit.getMainHandItem().getEnchantmentLevel(EnchantmentRegistrar.ZEAL.get());
            if (zealLevel > 0) {
                evt.setAmount(evt.getAmount() + zealLevel);
            }
        }

        if (evt.getSource().is(DamageTypeTags.IS_FIRE)) {
            Level level = evt.getEntity().level();
            Block block = level.getBlockState(evt.getEntity().getOnPos().above()).getBlock();
            if (block == Blocks.SOUL_FIRE || block == BlockRegistrar.UNEXTINGUISHABLE_SOUL_FIRE.get()) {
                evt.getEntity().addEffect(new MobEffectInstance(MobEffectRegistrar.SOULS_AFLAME.get(), 120, 0, true, true));
            }
        }

        if (evt.getEntity().hasEffect(MobEffectRegistrar.SCORCHING_FIRE.get()) && evt.getSource().is(DamageTypes.ON_FIRE)) {
            evt.setAmount(evt.getAmount() * 3);
        }

        if (evt.getEntity().hasEffect(MobEffectRegistrar.SOULS_AFLAME.get()) && evt.getSource().is(DamageTypes.ON_FIRE)) {
            evt.setAmount(evt.getAmount() * 2);
        }

        if (evt.getEntity() instanceof HeroUnit && evt.getSource().getEntity() instanceof PhantomSummon) {
            evt.setAmount(evt.getAmount() * PhantomSummon.HERO_DAMAGE_MULT);
        }

        if (evt.getSource().getDirectEntity() instanceof GhastUnitFireball) {
            evt.setAmount(evt.getAmount() / 2);
        }

        if (evt.getSource().getEntity() instanceof AttackerUnit aUnit) {
            if (!evt.getSource().is(DamageTypes.EXPLOSION) &&
                !evt.getSource().is(DamageTypes.PLAYER_EXPLOSION) &&
                RANDOM.nextFloat() < aUnit.getExplosiveChance() && evt.getAmount() > 0.1f) {
                doExplosiveHit((LivingEntity) aUnit, evt.getEntity());
            }
            float lifeDmgPerc = aUnit.getLifeStealPercent();
            if (lifeDmgPerc > 0) {
                ((LivingEntity) aUnit).heal(evt.getAmount() * lifeDmgPerc);
                ParticleUtil.addParticleExplosion(ParticleRegistrar.FLOATING_HEART.get(), (int) (evt.getAmount() * lifeDmgPerc) + 1,
                        ((Entity) aUnit).level(), ((Entity) aUnit).getEyePosition());
            }
            float manaDmgPerc = aUnit.getManaOnHitPercent();
            if (manaDmgPerc > 0 && aUnit instanceof HeroUnit heroUnit) {
                heroUnit.setMana(heroUnit.getMana() + (evt.getAmount() * manaDmgPerc));
                ParticleUtil.addParticleExplosion(ParticleRegistrar.MANA.get(), (int) (evt.getAmount() * manaDmgPerc) + 1,
                        ((Entity) aUnit).level(), ((Entity) aUnit).getEyePosition());
            }
        }

        if (evt.getEntity() instanceof UnitInventory inv && evt.getEntity() instanceof Unit unit && inv.isHolding(UnitItems.BEENEST_ARMOUR)) {
            // Overkill damage shouldn't count: cap at the wearer's current health
            float effectiveDamage = Math.min(evt.getAmount(), evt.getEntity().getHealth());

            for (ItemStack itemStack : inv.getAllItems()) {
                if (itemStack.getItem() == ItemRegistrar.BEENEST_ARMOUR.get()) {
                    CompoundTag tag = itemStack.getOrCreateTag();

                    float dmg = effectiveDamage + tag.getFloat("damageTaken");

                    // Record overflow damage
                    while (dmg >= UnitItems.BEENEST_ARMOUR_DAMAGE_PER_BEE) {
                        Entity entity = UnitServerEvents.spawnMob(EntityRegistrar.BEE_UNIT.get(),
                                serverLevel, evt.getEntity().blockPosition().above(), unit.getOwnerName());
                        if (entity instanceof BeeUnit beeUnit) {
                            beeUnit.setIsSummoned(true);
                        }
                        dmg -= UnitItems.BEENEST_ARMOUR_DAMAGE_PER_BEE;
                    }
                    tag.putFloat("damageTaken", dmg);
                }
            }
        }
    }

    private static final float EXPLOSIVE_HIT_SPLASH_MULT = 0.5f;
    private static final float EXPLOSIVE_HIT_SPLASH_RADIUS = 2.5f;
    private static final float EXPLOSIVE_HIT_KNOCKBACK = 0.5f;

    private static void doExplosiveHit(LivingEntity attacker, LivingEntity pEntity) {
        com.solegendary.reignofnether.resources.kill.EconomicExplosionHooks.nativeAction(attacker,attacker,()->attacker.level().explode(attacker, null, null, pEntity.getX(), pEntity.getEyeY(), pEntity.getZ(),
                1.0f, false, Level.ExplosionInteraction.NONE));

        if (attacker instanceof AttackerUnit aUnit) {
            for (LivingEntity hitEntity : MiscUtil.getEntitiesWithinRange(pEntity.getEyePosition(), EXPLOSIVE_HIT_SPLASH_RADIUS, LivingEntity.class, attacker.level())) {
                if (hitEntity instanceof Unit unit) {
                    var relationShip = UnitServerEvents.getUnitToEntityRelationship(unit, attacker);
                    if (relationShip.equals(Relationship.OWNED)) continue;
                    if (relationShip.equals(Relationship.FRIENDLY)) continue;
                }
                if (hitEntity == pEntity)
                    continue;
                boolean hurt = com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(attacker,hitEntity,hitEntity::hurt).hurt(attacker.damageSources().explosion(attacker, attacker), aUnit.getUnitAttackDamage() * EXPLOSIVE_HIT_SPLASH_MULT);
                if (hurt) {
                    hitEntity.knockback(EXPLOSIVE_HIT_KNOCKBACK, Mth.sin(attacker.getYRot() * 0.017453292F), -Mth.cos(attacker.getYRot() * 0.017453292F));
                    attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                    attacker.setLastHurtMob(hitEntity);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLightningStrike(EntityStruckByLightningEvent evt) {
        if (evt.getEntity() instanceof CreeperUnit creeperUnit) {
            creeperUnit.setSecondsOnFire(0);
        }
    }

    // prevent friendly fire from ranged units (unless specifically targeted)
    // (just allows piercing, damage is cancelled in LivingDamageEvent)
    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent evt) {
        Entity owner = evt.getProjectile().getOwner();
        Entity hit = null;
        if (evt.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
            hit = ((EntityHitResult) evt.getRayTraceResult()).getEntity();
        }

        // prevent fireballs actually directly hitting anything, except other flying units
        // instead just relying on splash damage and fire creation
        if (owner instanceof GhastUnit && hit != null) {
            if (!(hit instanceof Unit unit && unit.isFlyingUnit())) {
                evt.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            }
        }

        if (owner instanceof Unit unit && hit != null) {
            if (getUnitToEntityRelationship(unit, hit) == Relationship.FRIENDLY
                && unit.getTargetGoal().getTarget() != hit) {
                // for some reason, if we try to cancel a pierced arrow, it loops here forever
                if (evt.getProjectile() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
                    return;
                }
                evt.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            }
        }

        if (hit instanceof Unit unit && evt.getProjectile().getPersistentData().contains("accuracyRoll")) {
            float accuracyRoll = evt.getProjectile().getPersistentData().getFloat("accuracyRoll");
            if (accuracyRoll < unit.getEvasionChance())
                evt.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }

    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added evt) {
        // double level of all enchants
        if (evt.getEffectInstance().getEffect() == MobEffectRegistrar.ENCHANTMENT_AMPLIFIER.get() &&
            evt.getOldEffectInstance() == null) {
            EnchantmentUtil.updateEnchantLevels(evt.getEntity(), false);
        }

        if (!evt.getEntity().level().isClientSide())
            UnitSyncMobEffectsClientboundPacket.addEffectClientside(evt.getEntity(), evt.getEffectInstance());
    }

    @SubscribeEvent
    public static void onMobEffectExpired(MobEffectEvent.Expired evt) {
        // halve level of all enchants
        if (evt.getEffectInstance() != null) {
            MobEffect effect = evt.getEffectInstance().getEffect();
            if (effect == MobEffectRegistrar.ENCHANTMENT_AMPLIFIER.get()) {
                EnchantmentUtil.updateEnchantLevels(evt.getEntity(), true);
            } else if (effect == MobEffectRegistrar.TEMPORARY_EFFICIENCY.get()) {
                EnchantmentHelper.setEnchantments(new HashMap<>(), evt.getEntity().getMainHandItem());
            }
        }
        if (!evt.getEntity().level().isClientSide())
            UnitSyncMobEffectsClientboundPacket.removeEffectClientside(evt.getEntity(), evt.getEffectInstance().getEffect());
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable evt) {
        // Added is not cancelable in Forge 47.4.0. Deny before any effect mutation.
        if (evt.getEntity() instanceof Unit unit && unit.uninterruptable()
                && MobEffectRegistrar.isInterrupt(evt.getEffectInstance().getEffect())) {
            evt.setResult(Event.Result.DENY);
            return;
        }
        // allow undead to be poisoned
        if (evt.getEntity().getMobType() == MobType.UNDEAD &&
            evt.getEntity() instanceof Unit &&
            (evt.getEffectInstance().getEffect() == MobEffects.POISON ||
            evt.getEffectInstance().getEffect() == MobEffects.REGENERATION)) {
            evt.setResult(Event.Result.ALLOW);
        }
    }

    private static float KNOCKBACK_RESIST_PER_TICKS = 0.01f;

    public static ArrayList<Integer> knockbackIgnoreIds = new ArrayList<>();
    public static ArrayList<Pair<Integer, Integer>> knockbackResistIds = new ArrayList<>();

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent evt) {
        if (evt.getEntity().getEffect(MobEffectRegistrar.FREEZE.get()) != null)
            evt.setCanceled(true);
        if (evt.getEntity() instanceof GhastUnit)
            evt.setCanceled(true);
        else if (evt.getEntity() instanceof WraithUnit)
            evt.setCanceled(true);
        else if (evt.getEntity() instanceof BruteUnit bruteUnit && bruteUnit.isHoldingUpShield())
            evt.setCanceled(true);
        else if (knockbackIgnoreIds.removeIf(i -> i == evt.getEntity().getId()))
            evt.setCanceled(true);
    }

    public static void debug1(BlockPos pos) {
        //BlockUtils.placeWraithSnow(serverLevel, pos.above());
    }

    public static void debug2() {
    }
}
