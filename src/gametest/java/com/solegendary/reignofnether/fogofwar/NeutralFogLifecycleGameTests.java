package com.solegendary.reignofnether.fogofwar;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Real server entity-removal callbacks; serialization simulates a reload, not a process restart. */
@GameTestHolder("reignofnether_neutral_fog_lifecycle")
@PrefixGameTestTemplate(false)
public final class NeutralFogLifecycleGameTests {
    @GameTest(templateNamespace = "reignofnether_neutral_fog_lifecycle", template = "ore_mine_test_arena",
            batch = "neutral_fog_lifecycle", timeoutTicks = 100)
    public static void unloadReloadDestroyAndViewerHistory(GameTestHelper h) {
        var level = h.getLevel().getServer().overworld();
        var previous = NeutralFogSaveData.get(level);
        var data = new NeutralFogSaveData();
        // Isolate the fixture from any existing match; restore its exact SavedData object in finally.
        level.getDataStorage().set("saved-neutral-fog-data", data);
        Pig original = EntityType.PIG.create(level);
        Pig reloaded = null;
        try {
            h.assertTrue(original != null, "fixture entity created");
            BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
            original.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            original.setNoAi(true);
            h.assertTrue(level.addFreshEntity(original), "fixture published through native server path");
            UUID id = original.getUUID();
            int oldRuntimeId = original.getId();
            var originalBounds = original.getBoundingBox();
            data.capture(id, originalBounds);
            data.finishCapture();
            CompoundTag entityTag = original.saveWithoutId(new CompoundTag());

            // Invoke the native entity unload path, not a hand-posted Forge event. No chunk is forced.
            original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(level.getEntity(id) == null, "unloaded entity absent from UUID lookup");
            h.assertTrue(!data.markers().iterator().next().isDestroyed(), "real unload callback is not death");
            h.assertTrue(!data.observeDestroyed(id, new UUID(1, 1), true), "visible unknown is retained");

            // Reconstruct both persisted pieces as on reload; entity runtime ID must change, UUID must not.
            data = NeutralFogSaveData.load(data.save(new CompoundTag()));
            level.getDataStorage().set("saved-neutral-fog-data", data);
            reloaded = EntityType.PIG.create(level);
            h.assertTrue(reloaded != null, "reload entity created");
            reloaded.load(entityTag);
            h.assertTrue(level.addFreshEntity(reloaded), "same UUID reload is accepted after unload");
            h.assertTrue(reloaded.getUUID().equals(id) && reloaded.getId() != oldRuntimeId,
                    "persistent UUID independent of runtime ID");
            h.assertTrue(data.markers().iterator().next().bounds.equals(originalBounds), "original location retained");
            h.assertTrue(!data.markers().iterator().next().isDestroyed(), "reload never retires marker");

            reloaded.discard(); // native destructive removal, runs registered Forge callback
            h.assertTrue(data.markers().iterator().next().isDestroyed(), "actual destruction records tombstone");
            UUID hiddenViewer = new UUID(2, 2), seeingViewer = new UUID(3, 3);
            h.assertTrue(!data.observeDestroyed(id, hiddenViewer, false), "hidden death cannot retire marker");
            h.assertTrue(data.observeDestroyed(id, seeingViewer, true), "visible destruction retires only observer");
            var restored = NeutralFogSaveData.load(data.save(new CompoundTag()));
            var marker = restored.markers().iterator().next();
            h.assertTrue(marker.isRemovedFor(seeingViewer) && !marker.isRemovedFor(hiddenViewer),
                    "serialization preserves viewer-specific information");
            h.assertTrue(!restored.observeDestroyed(id, seeingViewer, true), "relogin does not repeat removal");
            System.out.println("NEUTRAL_FOG_LIFECYCLE_PASS native_unload uuid_reload native_destroy viewer_history_serialization");
        } finally {
            if (original != null && !original.isRemoved()) original.discard();
            if (reloaded != null && !reloaded.isRemoved()) reloaded.discard();
            level.getDataStorage().set("saved-neutral-fog-data", previous);
        }
        h.succeed();
    }
}
