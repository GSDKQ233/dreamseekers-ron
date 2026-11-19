package com.solegendary.reignofnether.fogofwar;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;

@GameTestHolder("reignofnether_fog_snapshot_lifecycle")
@PrefixGameTestTemplate(false)
public final class FogSnapshotLifecycleGameTests {
    @GameTest(templateNamespace="reignofnether_fog_snapshot_lifecycle", template="ore_mine_test_arena", batch="fog_snapshot_lifecycle", timeoutTicks=200)
    public static void boundedCaptureRestartAndHugeTransition(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var border = level.getWorldBorder();
        double size = border.getSize(), x = border.getCenterX(), z = border.getCenterZ();
        var path = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath()
                .resolve("reignofnether/fogsnapshot");
        try {
            FogChunkSnapshot.onServerStopped(level.getServer());
            border.setSize(59999968);
            FogChunkSnapshot.rebuildIndex(level);
            h.assertTrue(!FogChunkSnapshot.hasAny(level), "fresh huge world does not adopt snapshots");
            long before = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1;
            FogChunkSnapshot.captureFogChunks(level);
            h.assertTrue((Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1) == before,
                    "fresh huge capture performs no directory mutation");
            border.setCenter(8,8);
            border.setSize(16);
            FogChunkSnapshot.captureFogChunks(level);
            ChunkPos pos = new ChunkPos(0,0);
            var file = path.resolve("c.0.0.dat");
            byte[] baseline = Files.readAllBytes(file);
            h.assertTrue(FogChunkSnapshot.hasAny(level) && FogChunkSnapshot.get(level,pos) != null, "tiny real chunk captured/read");
            var other = level.getServer().getLevel(Level.NETHER);
            h.assertTrue(other != null && !FogChunkSnapshot.hasAny(other) && FogChunkSnapshot.get(other,pos) == null,
                    "snapshot is not served to another level");
            var historicalTime = java.nio.file.attribute.FileTime.fromMillis(1_000_000L);
            Files.setLastModifiedTime(file, historicalTime);
            FogChunkSnapshot.requestRecapture(new WorldBorder());
            FogChunkSnapshot.recaptureIfRequested(level);
            h.assertTrue(java.util.Arrays.equals(baseline,Files.readAllBytes(file)), "unowned border cannot dirty snapshot");
            h.assertTrue(Files.getLastModifiedTime(file).equals(historicalTime), "foreign border must not recapture identical chunk bytes");
            FogChunkSnapshot.onServerStopped(level.getServer());
            h.assertTrue(!FogChunkSnapshot.hasAny(level) && Files.exists(file), "shutdown detaches memory but preserves baseline");
            FogChunkSnapshot.rebuildIndex(level);
            h.assertTrue(FogChunkSnapshot.hasAny(level) && FogChunkSnapshot.get(level,pos) != null, "restart adopts persistent baseline");
            border.setSize(59999968);
            FogChunkSnapshot.requestRecapture(border);
            FogChunkSnapshot.recaptureIfRequested(level);
            FogChunkSnapshot.captureFogChunks(level); // direct calls have identical bounds guard
            h.assertTrue(!FogChunkSnapshot.hasAny(level) && FogChunkSnapshot.get(level,pos) == null,
                    "small to huge in same JVM disables snapshot serving");
            h.assertTrue(java.util.Arrays.equals(baseline,Files.readAllBytes(file)), "invalid recapture must not delete old baseline");
            FogChunkSnapshot.rebuildIndex(other); // early-return must clear old world's memory
            border.setSize(16);
            h.assertTrue(!FogChunkSnapshot.hasAny(level), "invalid new-world attach cannot retain old index");
            h.assertTrue(Files.exists(file), "cross-level attach never deletes prior world's disk baseline");
            System.out.println("FOG_SNAPSHOT_LIFECYCLE_PASS tiny_capture scoped_reads foreign_border stop_preserves_disk restart_rebuild fresh_huge small_to_huge_no_generation_or_deletion");
        } finally {
            FogChunkSnapshot.rebuildIndex(level);
            FogChunkSnapshot.clear(level);
            border.setCenter(x,z);
            border.setSize(size);
            FogChunkSnapshot.onServerStopped(level.getServer());
        }
        h.succeed();
    }
}
