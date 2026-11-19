package com.dreamseekers;

import com.dreamseekers.economy.DreamGardenPlacement;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder("dreamseekers_passive_c")
@PrefixGameTestTemplate(false)
public final class GardenDecorationQuarantineGameTests {
    private static final String BATCH = "garden_decoration_quarantine";
    private static final String KEY = "dreamseekers:garden_decoration_schema";
    private static DreamGardenPlacement active;
    private static BlockPos marker;
    private static BlockState originalMarker;

    private static ListTag storage(CompoundTag decoration) {
        var entry = new CompoundTag();
        entry.putString("key", KEY);
        entry.put("data", decoration);
        var storage = new ListTag();
        storage.add(entry);
        return storage;
    }

    private static CompoundTag extension(ListTag storage, String key) {
        for (int i = 0; i < storage.size(); i++) {
            var entry = storage.getCompound(i);
            if (entry.getString("key").equals(key)) return entry.getCompound("data");
        }
        return null;
    }

    private static DreamGardenPlacement restore(ServerLevel level, BlockPos origin, ListTag wire) {
        var saved = DataStorage.read(wire, level.getServer());
        var garden = (DreamGardenPlacement) DreamseekersMod.REVERIE_GARDEN.createRestoredBuildingPlacement(
            level, origin, Rotation.NONE, "GardenDowngradeFixture", saved);
        garden.isBuilt = true;
        garden.afterRestoredFromSave();
        return garden;
    }

    private static void detach() {
        if (active != null) {
            active.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
            BuildingServerEvents.getBuildings().remove(active);
            active = null;
        }
    }

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        detach();
        if (marker != null) level.setBlock(marker, originalMarker, 2);
        marker = null;
        originalMarker = null;
    }

    private static ListTag checkAfterTick(GameTestHelper h, long priorAge,
                                          Map<BlockPos, BlockState> before, CompoundTag expected) {
        h.assertTrue(BuildingServerEvents.getBuildings().contains(active), "survives the native pre-tick destruction scan");
        h.assertTrue(!active.migrationReady() && !active.shouldBeDestroyed() && !active.isAttackable(),
            "opaque geometry cannot authorize migration or native damage");
        h.assertTrue(active.tickAge == priorAge, "no ordinary construction, farm or passive-income tick");
        before.forEach((pos, state) -> h.assertTrue(h.getLevel().getBlockState(pos).equals(state),
            "no change to the physical footprint at " + pos));
        var wire = active.getDataStorage().write();
        h.assertTrue(expected.equals(extension(wire, KEY)), "decoration NBT preserved, not normalized or migrated");
        h.assertTrue(extension(wire, "dreamseekers:passive_production") == null, "no new income clock was created");
        return wire;
    }

    /** Two ordinary save-codec / factory cycles in one JVM, not a two-process restart test. */
    @GameTest(templateNamespace="dreamseekers_passive_c", template="ore_mine_test_arena", batch=BATCH, timeoutTicks=30)
    public static void futureDecorationRoundTripsWithoutTickingOrDemolishing(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(12, 3, 12));
        var fallback = BuildingUtils.getAbsoluteBlockData(DreamGardenPlacement.decoratedTemplate(), level, origin, Rotation.NONE);
        marker = fallback.stream().filter(b -> !b.getBlockState().isAir()).findFirst().orElseThrow().getBlockPos();
        originalMarker = level.getBlockState(marker);
        level.setBlock(marker, Blocks.MAGENTA_CONCRETE.defaultBlockState(), 2);
        var before = new LinkedHashMap<BlockPos, BlockState>();
        for (var b : fallback) before.put(b.getBlockPos(), level.getBlockState(b.getBlockPos()));

        var known = new CompoundTag();
        known.putInt("version", 1);
        var supported = restore(level, origin, storage(known));
        h.assertTrue(supported.migrationReady() && supported.isAttackable() && supported.shouldBeDestroyed(),
            "positive control: this same footprint really is damaged under the known schema");
        h.assertTrue(new DataStorage().getData(DreamGardenPlacement.DECOR_SCHEMA).knownVersion() == 0
                && DataStorage.read(storage(new CompoundTag()), level.getServer())
                    .getData(DreamGardenPlacement.DECOR_SCHEMA).knownVersion() == -1,
            "only a missing extension implies a legacy garden; a present empty payload is opaque");

        var expected = new CompoundTag();
        expected.putInt("version", 52);
        var nested = new CompoundTag();
        nested.putLongArray("templateCells", new long[]{47L, 1L, Long.MAX_VALUE});
        nested.putString("palette", "future_garden");
        expected.put("geometry", nested);
        active = restore(level, origin, storage(expected.copy()));
        BuildingServerEvents.getBuildings().add(active);
        long firstAge = active.tickAge;
        long[] secondAge = new long[1];
        h.startSequence().thenIdle(4).thenExecute(() -> {
            var firstSave = checkAfterTick(h, firstAge, before, expected);
            detach();
            active = restore(level, origin, firstSave);
            BuildingServerEvents.getBuildings().add(active);
            secondAge[0] = active.tickAge;
        }).thenIdle(4).thenExecute(() -> checkAfterTick(h, secondAge[0], before, expected)).thenSucceed();
    }
}
