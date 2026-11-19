package com.solegendary.reignofnether.fogofwar;

import com.solegendary.reignofnether.ReignOfNether;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Pre-game chunk packets inside the world border; served to dark players by ChunkMapInitialSendMixin so
// they can't cheat by reconnecting or leaving/re-entering render range.
//
// Backed by disk, not memory: each captured chunk is encoded to one file under
// <world>/reignofnether/fogsnapshot/ (~50-500 KB per chunk). Only a small in-memory index of which chunks
// exist (plus a bounded LRU of recently-decoded packets) is held on the heap, so even a full 80x80-chunk
// RTS border costs kilobytes of RAM instead of gigabytes. Fog is gated to RTS-optimised maps
// (WorldBorderServerEvents.isRtsOptimisedMap), so the bounded border always fits.
public class FogChunkSnapshot {

    private static final String DIR_NAME = "reignofnether/fogsnapshot";
    private static final int LRU_CAPACITY = 64; // hot chunks served to multiple players / on relog bursts

    // which chunks have a file on disk; cheap membership test so get()/hasAny() never touch disk needlessly
    private static final Set<ChunkPos> index = ConcurrentHashMap.newKeySet();

    private static boolean shouldRecapture = false;
    private static ServerLevel owner;

    // bounded decode cache; accessOrder LRU, synchronised for the (rare) concurrent access
    private static final Map<ChunkPos, ClientboundLevelChunkWithLightPacket> lru =
            Collections.synchronizedMap(new LinkedHashMap<>(LRU_CAPACITY + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkPos, ClientboundLevelChunkWithLightPacket> eldest) {
                    return size() > LRU_CAPACITY;
                }
            });

    private static Path dir = null;

    private static FogSnapshotBounds bounds(ServerLevel level) {
        if (level == null || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return null;
        WorldBorder border = level.getWorldBorder();
        return FogSnapshotBounds.checked(border.getSize(), border.getMinX(), border.getMaxX(),
                border.getMinZ(), border.getMaxZ());
    }

    /** Border callbacks from other worlds/dimensions must never dirty this snapshot. */
    public static void requestRecapture(WorldBorder border) {
        if (owner != null && owner.getWorldBorder() == border && !index.isEmpty()) shouldRecapture = true;
    }

    public static void recaptureIfRequested(ServerLevel level) {
        if (owner != level) {
            resetMemory();
            return;
        }
        if (shouldRecapture) captureFogChunks(level);
    }

    /** Detach only; the persistent pre-match baseline must survive normal server shutdown. */
    public static void onServerStopped(net.minecraft.server.MinecraftServer server) {
        if (owner != null && owner.getServer() == server) resetMemory();
    }

    private static void resetMemory() {
        shouldRecapture = false;
        index.clear();
        lru.clear();
        dir = null;
        owner = null;
    }

    public static void captureFogChunks(ServerLevel level) {
        // Validate before ANY file deletion or chunk generation, even for direct callers.
        FogSnapshotBounds bounds = bounds(level);
        shouldRecapture = false;
        if (owner != level) resetMemory();
        if (bounds == null) return;
        resetMemory();
        owner = level;
        dir = resolveDir(level);
        deleteDir(); // only this validated world's directory, never a previous world's
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to create snapshot dir {}", dir, e);
            resetMemory();
            return;
        }

        int captured = 0;
        for (int cx = bounds.minChunkX(); cx <= bounds.maxChunkX(); cx++) {
            for (int cz = bounds.minChunkZ(); cz <= bounds.maxChunkZ(); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, true);
                if (chunk == null) continue;
                ChunkPos pos = new ChunkPos(cx, cz);
                if (writeChunk(pos, new ClientboundLevelChunkWithLightPacket(
                        chunk, level.getLightEngine(), null, null)))
                    captured++;
            }
        }
        ReignOfNether.LOGGER.info("[FogChunkSnapshot] captured {} chunks inside world border to {}", captured, dir);
    }

    // encode the packet and flush it to its chunk file; register in the index on success
    private static boolean writeChunk(ChunkPos pos, ClientboundLevelChunkWithLightPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            Files.write(fileFor(pos), bytes);
            index.add(pos);
            return true;
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to write chunk {}", pos, e);
            return false;
        } finally {
            buf.release();
        }
    }

    public static ClientboundLevelChunkWithLightPacket get(ServerLevel level, ChunkPos pos) {
        if (owner != level || bounds(level) == null || dir == null || !index.contains(pos)) return null; // no snapshot for this chunk; caller decides fallback
        ClientboundLevelChunkWithLightPacket cached = lru.get(pos);
        if (cached != null) return cached;
        try {
            byte[] bytes = Files.readAllBytes(fileFor(pos));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(buf);
                lru.put(pos, packet);
                return packet;
            } finally {
                buf.release();
            }
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to read chunk {}", pos, e);
            index.remove(pos);
            return null;
        }
    }

    // Repopulate the in-memory index from files left on disk by a prior server session. Called on
    // ServerStartedEvent so a mid-match server restart keeps serving the existing snapshot (hasAny() becomes
    // true, so the first RTS join won't recapture) instead of showing the live world.
    public static void rebuildIndex(ServerLevel level) {
        resetMemory();
        FogSnapshotBounds bounds = bounds(level);
        if (bounds == null) return;
        owner = level;
        Path d = resolveDir(level);
        if (!Files.isDirectory(d)) return;
        Set<ChunkPos> found = new HashSet<>();
        try (var stream = Files.list(d)) {
            stream.forEach(p -> {
                ChunkPos cp = parseChunkFile(p.getFileName().toString());
                if (cp != null && bounds.contains(cp.x, cp.z)) found.add(cp);
            });
        } catch (IOException e) {
            ReignOfNether.LOGGER.error("[FogChunkSnapshot] failed to rebuild index from {}", d, e);
            return;
        }
        if (found.isEmpty()) return;
        index.clear();
        lru.clear();
        index.addAll(found);
        dir = d;
        ReignOfNether.LOGGER.info("[FogChunkSnapshot] rebuilt index of {} chunks from {}", found.size(), d);
    }

    // "c.<x>.<z>.dat" -> ChunkPos, or null if the name isn't a snapshot file (x/z may be negative)
    private static ChunkPos parseChunkFile(String name) {
        String[] parts = name.split("\\.");
        if (parts.length != 4 || !parts[0].equals("c") || !parts[3].equals("dat")) return null;
        try {
            return new ChunkPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean hasAny(ServerLevel level) {
        return owner == level && bounds(level) != null && !index.isEmpty();
    }

    /** Explicit match reset may delete only the current world's adopted snapshot. */
    public static void clear(ServerLevel level) {
        if (owner == level && level != null) deleteDir();
        resetMemory();
    }

    private static Path resolveDir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().resolve(DIR_NAME);
    }

    private static Path fileFor(ChunkPos pos) {
        return dir.resolve("c." + pos.x + "." + pos.z + ".dat");
    }

    private static void deleteDir() {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    ReignOfNether.LOGGER.warn("[FogChunkSnapshot] failed to delete {}", p, e);
                }
            });
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            ReignOfNether.LOGGER.warn("[FogChunkSnapshot] failed to clear snapshot dir {}", dir, e);
        }
    }
}
