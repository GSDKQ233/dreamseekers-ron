// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamBuildingSupport;
import com.dreamseekers.building.DreamTemplateModel;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.DataStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dreamseekers.building.DreamTemplateModel.Revision.*;

/** Local real registry / template / DataStorage factories, not a two-process restart or a rendering test. */
@GameTestHolder("dreamseekers_shell_functions")
@PrefixGameTestTemplate(false)
public final class ReviewedOrdinaryBuildingGameTests {
    private static Map<BlockPos, BlockState> geometry(List<BuildingBlock> blocks) {
        var result = new HashMap<BlockPos, BlockState>();
        for (var b : blocks) result.put(b.getBlockPos(), b.getBlockState());
        return result;
    }

    @GameTest(template = "dreamseekers_test_arena", batch = "reviewed_ordinary_buildings")
    public static void newAndOldFactoriesSurviveSavedDataAndSnapshotRoundTrips(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(2, 1, 2));
        int idx = 0;
        for (var model : DreamTemplateModel.values()) {
            var definition = ReignOfNetherRegistries.BUILDING.get(DreamseekersMod.id(model.resource(ORIGINAL)));
            var rotation = Rotation.values()[idx++ % 4];
            var fresh = definition.createBuildingPlacement(level, origin, rotation, "ReviewedOrdinary");
            var legacy = definition.createRestoredBuildingPlacement(level, origin, rotation, "ReviewedOrdinary", new DataStorage());
            for (var entry : Map.of(fresh, REVIEWED, legacy, ORIGINAL).entrySet()) {
                var p = entry.getKey();
                var revision = entry.getValue();
                var expected = BuildingUtils.getAbsoluteBlockData(model.blocks(revision), level, origin, rotation);
                h.assertTrue(geometry(expected).equals(geometry(p.getBlocks())), "exact blocks: " + model + "/" + revision);
                h.assertTrue(p.getBlocksTotal() == model.count(revision), "authoritative count: " + model);
                var initial = p.structureSnapshot();
                h.assertTrue(definition.acceptsInitialStructureSnapshot(initial) && p.acceptsStructureSnapshot(initial)
                        && model.resource(revision).equals(initial.getString("template")), "closed initial model: " + model);
                h.assertTrue(!p.acceptsStructureSnapshot((p == fresh ? legacy : fresh).structureSnapshot()),
                        "no crossed instance binding");
                // Queue one block which only this revision can build. Loading against the other
                // version would silently discard it (or build a different block at that position).
                var otherGeometry = geometry((p == fresh ? legacy : fresh).getBlocks());
                var queued = p.getBlocks().stream().filter(b -> !b.getBlockState().isAir() && !b.isPlaced(level)
                        && !b.getBlockState().equals(otherGeometry.get(b.getBlockPos()))).findFirst().orElseThrow();
                p.addToBlockPlaceQueue(queued);
                // Exercise the native SavedData fields, not just a version-rule unit test.
                var data = new BuildingSaveData();
                var save = new BuildingSave(origin, level, definition, p.ownerName, rotation, origin, false, false,
                        0, null, BlockPos.ZERO, -1, p.getDataStorage(), .375d, new ListTag());
                save.pendingConstruction = p.savePendingConstruction();
                data.buildings.add(save);
                var loaded = BuildingSaveData.load(data.save(new CompoundTag()), level.getServer());
                h.assertTrue(loaded.buildings.size() == 1, "row survived real SavedData codec");
                var row = loaded.buildings.get(0);
                var restored = row.building.createRestoredBuildingPlacement(level, row.originPos, row.rotation, row.ownerName, row.dataStorage);
                restored.isBuilt = row.isBuilt; restored.partialBlocksDestroyed = row.partialBlocksDestroyed;
                restored.afterRestoredFromSave();
                restored.restorePendingConstruction(row.pendingConstruction);
                h.assertTrue(restored.savePendingConstruction().equals(save.pendingConstruction), "exact queued work survives");
                var after = restored.structureSnapshot();
                h.assertTrue(geometry(restored.getBlocks()).equals(geometry(expected)) && initial.getUUID("instance").equals(after.getUUID("instance"))
                        && after.getDouble("partial") == .375d && restored.acceptsStructureSnapshot(initial),
                        "geometry, physical instance and fractional damage survived: " + model + "/" + revision);
                // A damaged completed save keeps the full denominator. Do not heal, fill gaps or infer extra work.
                int actualPlaced = restored.getBlocksPlaced();
                restored.isBuilt = true; restored.afterRestoredFromSave();
                h.assertTrue(restored.getHighestBlockCountReached() == model.count(revision) && restored.getBlocksPlaced() == actualPlaced
                        && restored.savePendingConstruction().equals(save.pendingConstruction), "completion flag must not reconstruct blocks");
                if (model == DreamTemplateModel.GARDEN)
                    h.assertTrue(restored.getBlocks().stream().filter(b -> b.getBlockState().is(DreamPassiveEconomy.DECORATIVE_WHEAT.get())).count() == 16
                            && restored.getBlocks().stream().noneMatch(b -> b.getBlockState().is(Blocks.WHEAT)), "16 non-harvestable garden markers");
                if (p == fresh) {
                    var preview = BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level), level, origin, rotation);
                    h.assertTrue(geometry(preview).equals(geometry(expected)), "preview and newly built model agree");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "dreamseekers_test_arena", batch = "reviewed_ordinary_buildings")
    public static void buriedSanctuarySoilsAreTheOnlyRuntimeNormalizations(GameTestHelper h) {
        var model = DreamTemplateModel.SANCTUARY;
        var buried = Set.of(new BlockPos(1,0,8), new BlockPos(2,0,9));
        for (var rev : List.of(ORIGINAL, REVIEWED)) {
            var raw = geometry(DreamBuildingSupport.load(model.resource(rev)));
            var runtime = geometry(model.blocks(rev));
            h.assertTrue(raw.keySet().equals(runtime.keySet()), "same physical cells");
            for (var pos : raw.keySet()) {
                if (rev == REVIEWED && buried.contains(pos)) {
                    h.assertTrue(raw.get(pos).is(Blocks.GRASS_BLOCK) && runtime.get(pos).is(Blocks.DIRT),
                            "only the permanently covered ground is constructed as stable dirt");
                    h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(raw.get(pos.above()).getBlock())
                            .toString().equals("pasterdream:chiseled_dyedreamquartz_block"), "the reviewed step covers the soil");
                } else h.assertTrue(runtime.get(pos).equals(raw.get(pos)), "unchanged visible and legacy geometry at " + pos);
            }
        }
        h.succeed();
    }

    @GameTest(template = "dreamseekers_test_arena", batch = "reviewed_ordinary_buildings")
    public static void unknownGeometryRowsAreParkedWithoutConstructingAnyFallback(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(2,1,2));
        var building = DreamseekersMod.RESONANCE_DAIS;
        var fresh = building.createBuildingPlacement(level, origin, Rotation.NONE, "ReviewedOrdinary");
        var saved = new BuildingSaveData();
        saved.buildings.add(new BuildingSave(origin, level, building, fresh.ownerName, Rotation.NONE, origin,
                false, false, 0, null, BlockPos.ZERO, -1, fresh.getDataStorage(), 0, new ListTag()));
        var root = saved.save(new CompoundTag());
        var row = root.getList("buildings", Tag.TAG_COMPOUND).getCompound(0);
        var extensions = row.getList("dataStorage", Tag.TAG_COMPOUND);
        var entry = extensions.stream().map(t -> (CompoundTag)t)
                .filter(t -> "dreamseekers:template_model".equals(t.getString("key"))).findFirst().orElseThrow();
        var valid = entry.getCompound("data").copy();
        var badPeak = valid.copy(); badPeak.getCompound("constructionPeak").putInt("resonance_dais", 400);
        var future = valid.copy(); future.putInt("revision", 173); future.putString("notes", "keep me");
        for (var unsupported : List.of(new CompoundTag(), badPeak, future)) {
            entry.put("data", unsupported);
            var parked = BuildingSaveData.load(root, level.getServer());
            h.assertTrue(parked.buildings.isEmpty(), "malformed/unknown is not reinterpreted as an old or new building");
            h.assertTrue(row.equals(parked.save(new CompoundTag()).getList("buildings", Tag.TAG_COMPOUND).getCompound(0)),
                    "the complete unsupported row is preserved");
        }
        h.succeed();
    }
}
