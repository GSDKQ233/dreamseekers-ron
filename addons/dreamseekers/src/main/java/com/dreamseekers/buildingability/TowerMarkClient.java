// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionClient;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Caster-private, existing-visible-body markers. No target positions are received or cached. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT)
public final class TowerMarkClient {
    private static final ActiveClientTickClock CLOCK = new ActiveClientTickClock();
    private static final Map<BuildingPlacement, Expectation> ACTIVE = new IdentityHashMap<>();
    // A spent/rejected expectation must not be reopened by another click on the same stale UI revision.
    private static final Map<BuildingPlacement, CastKey> LAST_EXPECTED = new WeakHashMap<>();
    private static final int PENDING_TICKS = 20, HEARTBEAT_TTL = 10, RANGE = 24;
    private static ClientLevel level;
    private static Connection connection;
    private static long highestOrder = -1;

    private record CastKey(UUID instance, long generation, long revision, UUID session, UUID epoch) { }

    private static final class Target {
        final int id;
        final UUID uuid;
        final LivingEntity entity;
        final String owner;
        Target(LivingEntity entity) {
            this.entity = entity;
            id = entity.getId();
            uuid = entity.getUUID();
            owner = ((Unit) entity).getOwnerName();
        }
    }

    private static final class Expectation {
        final BuildingPlacement host;
        final CastKey key;
        final ClientLevel level;
        final Connection connection;
        final UUID viewer;
        final String owner;
        final BlockPos origin;
        final Rotation rotation;
        final long localCapture, started;
        final Map<UUID, Integer> original = new HashMap<>();
        final Map<UUID, Target> targets = new HashMap<>();
        final Set<UUID> retired = new HashSet<>();
        boolean accepted;
        long received;
        int remaining;
        Expectation(BuildingPlacement host, BuildingAbilityPackets.Snapshot snapshot, Minecraft mc) {
            this.host = host;
            key = new CastKey(snapshot.instance(), snapshot.generation(), snapshot.revision() + 1,
                    snapshot.session(), snapshot.epoch());
            level = mc.level;
            connection = mc.getConnection().getConnection();
            viewer = mc.player.getUUID();
            owner = snapshot.owner();
            origin = host.originPos.immutable();
            rotation = host.rotation;
            localCapture = host.getCaptureRevision();
            started = CLOCK.now();
        }
    }

    private TowerMarkClient() { }

    private static void clearViews() { ACTIVE.clear(); LAST_EXPECTED.clear(); highestOrder = -1; }

    private static void context() {
        Minecraft mc = Minecraft.getInstance();
        Connection next = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        if (CLOCK.context(mc.level, next)) clearViews();
        level = mc.level;
        connection = next;
    }

    public static void clear() {
        CLOCK.clear();
        level = null;
        connection = null;
        clearViews();
    }

    /** Called immediately before this UI's actual CAST submit; no unsolicited S2C creates an expectation. */
    public static void expect(BuildingPlacement host, BuildingAbilityPackets.Snapshot snapshot) {
        context();
        Minecraft mc = Minecraft.getInstance();
        if (host == null || snapshot == null || !snapshot.valid() || !snapshot.supported() || !snapshot.available()
                || snapshot.ability() != BuildingAbility.WATCHTOWER_MARK || snapshot.revision() == Long.MAX_VALUE
                || mc.player == null || level == null || connection == null || !connection.isConnected()
                || !snapshot.owner().equals(mc.player.getGameProfile().getName())
                || !snapshot.dimension().equals(level.dimension().location()) || !snapshot.origin().equals(host.originPos)
                || !ExtendedResourcesClient.ready() || !snapshot.epoch().equals(ExtendedResourcesClient.epoch())
                || !EconomyActionClient.matchesContext(snapshot.session(), snapshot.epoch())) return;
        Expectation expectation = new Expectation(host, snapshot, mc);
        if (!sourceValid(expectation)) return;
        if (expectation.key.equals(LAST_EXPECTED.get(host))) return;
        LAST_EXPECTED.put(host, expectation.key);
        ACTIVE.put(host, expectation);
    }

    private static LevelChunk loadedChunk(ClientLevel world, BlockPos pos) {
        // ClientChunkCache with create=false returns an existing cached chunk or null; it cannot request one.
        return world.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
    }

    private static boolean footprintLoaded(Expectation e) {
        long width = (long) e.host.maxCorner.getX() - e.host.minCorner.getX();
        long depth = (long) e.host.maxCorner.getZ() - e.host.minCorner.getZ();
        if (width < 0 || width > 6 || depth < 0 || depth > 6) return false;
        for (int x = e.host.minCorner.getX() >> 4; x <= e.host.maxCorner.getX() >> 4; x++)
            for (int z = e.host.minCorner.getZ() >> 4; z <= e.host.maxCorner.getZ() >> 4; z++)
                if (e.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null) return false;
        return true;
    }

    private static boolean sourceValid(Expectation e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.level != level || e.connection != connection || connection == null || !connection.isConnected()
                || mc.player == null || !mc.player.isAlive() || mc.player.isRemoved() || !mc.player.getUUID().equals(e.viewer)
                || !mc.player.getGameProfile().getName().equals(e.owner) || e.host.getLevel() != level
                || !e.owner.equals(e.host.ownerName) || !BuildingClientEvents.getBuildings().contains(e.host)
                || e.host.getBuilding() != DreamseekersMod.SHADOW_WATCHTOWER || !e.host.isBuilt
                || e.host.isDestroyedServerside || e.host.shouldBeDestroyed() || e.host.getHealth() <= 0
                || !e.host.originPos.equals(e.origin) || e.host.rotation != e.rotation
                || e.host.getCaptureRevision() != e.localCapture || !(e.host instanceof DreamWatchtowerPlacement tower)
                || !tower.hasGarrisonDeck() || !tower.stateSnapshot().supported() || !footprintLoaded(e)
                || PlayerClientEvents.getRTSPlayer(e.owner) == null || !ExtendedResourcesClient.ready()
                || !e.key.epoch().equals(ExtendedResourcesClient.epoch())
                || !EconomyActionClient.matchesContext(e.key.session(), e.key.epoch())) return false;
        // Same-owner real occupancy only; allied occupants do not keep this private source alive.
        for (LivingEntity body : List.copyOf(UnitClientEvents.getAllUnits()))
            if (body instanceof Unit unit && !(body instanceof HeroUnit) && body.level() == e.level
                    && body.isAlive() && !body.isRemoved() && e.level.getEntity(body.getId()) == body
                    && e.owner.equals(unit.getOwnerName()) && e.host.isPosInsideBuilding(body.getOnPos().above())
                    && body.getOnPos().getY() > e.origin.getY() + 2 && GarrisonableBuildingAddon.getGarrison(unit) == e.host) return true;
        return false;
    }

    private static boolean fresh(Expectation e) {
        return !e.accepted ? CLOCK.fresh(e.started, PENDING_TICKS - 1)
                : CLOCK.fresh(e.received, HEARTBEAT_TTL - 1) && CLOCK.remaining(e.received, e.remaining) > 0;
    }

    private static boolean eligible(Expectation e, LivingEntity body) {
        if (!(body instanceof Unit unit) || body instanceof HeroUnit || body.level() != e.level || !body.isAlive()
                || body.isRemoved() || body.isSpectator() || body.isInvisible() || e.level.getEntity(body.getId()) != body
                || !UnitClientEvents.getAllUnits().contains(body) || loadedChunk(e.level, body.blockPosition()) == null
                || unit.getOwnerName().isBlank() || AlliancesClient.isAlliedOrOwned(e.owner, unit.getOwnerName())
                || !FogOfWarClientEvents.isInBrightChunk(body)) return false;
        double dx = body.getX() - (e.origin.getX() + .5), dy = body.getY() - (e.origin.getY() + .5), dz = body.getZ() - (e.origin.getZ() + .5);
        return Math.abs(dx) <= RANGE && Math.abs(dy) <= RANGE && Math.abs(dz) <= RANGE
                && dx * dx + dy * dy + dz * dz <= RANGE * RANGE && visibleRay(e, body);
    }

    private static boolean targetValid(Expectation e, Target target) {
        return e.level.getEntity(target.id) == target.entity && target.entity.getUUID().equals(target.uuid)
                && target.owner.equals(((Unit) target.entity).getOwnerName()) && eligible(e, target.entity);
    }

    private static Vec3 eye(Expectation e) {
        return Vec3.atCenterOf(new BlockPos(3, 13, 3).rotate(e.rotation).offset(e.origin).above());
    }

    private static boolean visibleRay(Expectation e, LivingEntity target) {
        Vec3 from = eye(e), to = target.getEyePosition();
        if (from.distanceToSqr(to) > 128 * 128) return false;
        LoadedBlocks blocks = new LoadedBlocks(e.level);
        try {
            var hit = blocks.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            return blocks.complete && hit.getType() == HitResult.Type.MISS;
        } catch (RuntimeException unknownShape) {
            return false;
        }
    }

    /** Missing chunks and uninstantiated block-entity collision data are opaque, never transparent air. */
    private static final class LoadedBlocks implements BlockGetter {
        private final ClientLevel world;
        private boolean complete = true;
        LoadedBlocks(ClientLevel world) { this.world = world; }
        private LevelChunk chunk(BlockPos pos) {
            LevelChunk chunk = loadedChunk(world, pos);
            if (chunk == null) complete = false;
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            if (world.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
            LevelChunk chunk = chunk(pos);
            return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(pos);
        }
        @Override public FluidState getFluidState(BlockPos pos) {
            if (world.isOutsideBuildHeight(pos)) return Fluids.EMPTY.defaultFluidState();
            LevelChunk chunk = chunk(pos);
            return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            if (world.isOutsideBuildHeight(pos)) return null;
            LevelChunk chunk = chunk(pos);
            if (chunk == null) return null;
            BlockEntity existing = chunk.getBlockEntities().get(pos);
            if (existing != null && !existing.isRemoved()) return existing;
            if (existing != null || chunk.getBlockState(pos).hasBlockEntity()) complete = false;
            return null;
        }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
    }

    public static void accept(TowerMarkPackets.Snapshot packet, Connection incoming) {
        context();
        Minecraft mc = Minecraft.getInstance();
        if (packet == null || !packet.valid() || incoming != connection || connection == null || level == null
                || mc.player == null || !packet.owner().equals(mc.player.getGameProfile().getName())
                || !packet.dimension().equals(level.dimension().location()) || packet.order() <= highestOrder
                || !ExtendedResourcesClient.ready() || !packet.epoch().equals(ExtendedResourcesClient.epoch())
                || !EconomyActionClient.matchesContext(packet.session(), packet.epoch())) return;
        highestOrder = packet.order();
        Expectation found = null;
        for (Expectation e : ACTIVE.values())
            if (e.origin.equals(packet.origin()) && e.key.instance().equals(packet.instance())
                    && e.key.generation() == packet.generation() && e.key.revision() == packet.revision()
                    && e.key.session().equals(packet.session()) && e.key.epoch().equals(packet.epoch())) {
                if (found != null) return; // Never choose one of two ambiguous local bindings.
                found = e;
            }
        if (found == null) return;
        Expectation e = found;
        if (!sourceValid(e) || !fresh(e) || packet.remaining() == 0 || packet.targets().isEmpty()) {
            ACTIVE.remove(e.host, e);
            return;
        }
        int remaining = e.accepted ? Math.min(packet.remaining(), (int) CLOCK.remaining(e.received, e.remaining)) : packet.remaining();
        if (remaining <= 0) { ACTIVE.remove(e.host, e); return; }
        Set<UUID> present = new HashSet<>();
        for (var row : packet.targets()) {
            present.add(row.uuid());
            if (!e.accepted) e.original.put(row.uuid(), row.id());
            else if (!Integer.valueOf(row.id()).equals(e.original.get(row.uuid()))) {
                ACTIVE.remove(e.host, e); // A server update must be a subset, not discovery of another enemy.
                return;
            }
            if (e.retired.contains(row.uuid())) continue;
            var entity = e.level.getEntity(row.id());
            if (!(entity instanceof LivingEntity body) || !body.getUUID().equals(row.uuid()) || !eligible(e, body)) {
                e.retired.add(row.uuid());
                e.targets.remove(row.uuid());
                continue;
            }
            Target old = e.targets.get(row.uuid());
            if (old != null && (old.entity != body || !targetValid(e, old))) {
                e.retired.add(row.uuid());
                e.targets.remove(row.uuid());
            } else if (old == null) e.targets.put(row.uuid(), new Target(body));
        }
        for (UUID removed : List.copyOf(e.targets.keySet())) if (!present.contains(removed)) {
            e.targets.remove(removed);
            e.retired.add(removed);
        }
        e.accepted = true;
        e.received = CLOCK.now();
        e.remaining = remaining;
        pruneTargets(e);
    }

    private static void pruneTargets(Expectation e) {
        for (Target target : List.copyOf(e.targets.values())) if (!targetValid(e, target)) {
            e.targets.remove(target.uuid);
            e.retired.add(target.uuid); // A later heartbeat never revives this cast's locally lost target.
        }
    }

    private static void prune() {
        for (Expectation e : List.copyOf(ACTIVE.values())) {
            if (!sourceValid(e)) ACTIVE.remove(e.host, e);
            else if (!fresh(e)) {
                ACTIVE.remove(e.host, e);
                // No server result was ever accepted. A later explicit CAST retry may create a new
                // one-second expectation; repeated clicks within this interval never extend it.
                if (!e.accepted) LAST_EXPECTED.remove(e.host, e.key);
            } else if (e.accepted) pruneTargets(e);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        context();
        if (CLOCK.endTick(Minecraft.getInstance().isPaused())) clearViews();
        prune();
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        context();
        // Validate even when RTS rendering is hidden, so visibility loss is permanent for this cast.
        prune();
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.player == null || !OrthoviewClientEvents.isEnabled()) return;
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        Set<UUID> rendered = new HashSet<>();
        for (Expectation e : List.copyOf(ACTIVE.values())) {
            if (!e.accepted || !fresh(e) || !sourceValid(e)) continue;
            for (Target target : List.copyOf(e.targets.values())) {
                if (!targetValid(e, target)) { e.targets.remove(target.uuid); e.retired.add(target.uuid); continue; }
                if (!rendered.add(target.uuid)) continue;
                AABB body = target.entity.getBoundingBox();
                double x = (body.minX + body.maxX) / 2, z = (body.minZ + body.maxZ) / 2;
                pose.pushPose();
                pose.translate(-camera.x, -camera.y, -camera.z);
                // Ordinary depth-tested lines only. Never global glowing or an x-ray entity outline.
                LevelRenderer.renderLineBox(pose, buffer,
                        new AABB(x - .16, body.maxY + .12, z - .16, x + .16, body.maxY + .40, z + .16),
                        .55F, .82F, 1F, .95F);
                pose.popPose();
            }
        }
    }

    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
