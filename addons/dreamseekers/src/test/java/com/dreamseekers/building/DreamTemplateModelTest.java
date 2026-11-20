// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pure revision rules and template files; placement, save and client behaviour are not covered here. */
class DreamTemplateModelTest {
    private static final Path ROOT = Path.of("addons/dreamseekers");
    private static final Path DATA = ROOT.resolve("src/main/resources/data/dreamseekers/structures");
    private static final Set<String> VANILLA_SHELL = Set.of("minecraft:stone_bricks", "minecraft:deepslate_tiles",
            "minecraft:deepslate_bricks", "minecraft:cobbled_deepslate", "minecraft:polished_blackstone_bricks", "minecraft:spruce_planks");

    private static CompoundTag read(String name) throws Exception {
        try (var stream = Files.newInputStream(DATA.resolve(name + ".nbt"))) { return NbtIo.readCompressed(stream); }
    }
    private static Set<String> solidCells(CompoundTag structure) {
        var palette = structure.getList("palette", 10);
        Set<String> cells = new LinkedHashSet<>();
        for (var raw : structure.getList("blocks", 10)) {
            var block = (CompoundTag) raw;
            if (!palette.getCompound(block.getInt("state")).getString("Name").equals("minecraft:air"))
                cells.add(block.getList("pos", 3).toString());
        }
        return cells;
    }

    @Test void templateNamesFollowTheRevision() {
        assertEquals("dream_hall", DreamTemplateModel.template("dream_hall", DreamTemplateModel.LEGACY));
        assertEquals("dream_hall_dyedream_v2", DreamTemplateModel.template("dream_hall", DreamTemplateModel.DYEDREAM));
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.template("dream_hall", 3));
    }

    @Test void onlyAnExactCurrentTagSelectsTheRestyledTemplate() {
        assertEquals(DreamTemplateModel.LEGACY, DreamTemplateModel.restoredRevision(null));
        var tag = DreamTemplateModel.tag(DreamTemplateModel.DYEDREAM);
        assertEquals(1, tag.getInt("schema"));
        assertEquals(DreamTemplateModel.DYEDREAM, tag.getInt("revision"));
    }

    @Test void clientsSwapOnlyWhenTheServerNamesTheOtherRevision() {
        var legacyOnServer = new DreamTemplateModel.State("dream_depot", DreamTemplateModel.LEGACY);
        var snapshot = legacyOnServer.snapshot(false, 40, 30, 0.5);
        assertTrue(DreamTemplateModel.validLegacySnapshot(snapshot));
        assertTrue(legacyOnServer.snapshot(true, 40, 30, 0.5).isEmpty());
        var current = new DreamTemplateModel.State("dream_depot", DreamTemplateModel.DYEDREAM);
        assertTrue(current.snapshot(false, 40, 30, 0.5).isEmpty(), "new construction keeps the unchanged empty snapshot");
        assertTrue(current.accepts(new CompoundTag()));
        assertTrue(current.accepts(snapshot));
        assertFalse(current.switchFor(new CompoundTag(), true));
        assertTrue(current.switchFor(snapshot, false));
        assertEquals(DreamTemplateModel.LEGACY, current.revision());
        assertFalse(current.switchFor(snapshot, false));
        assertFalse(current.switchFor(new CompoundTag(), false), "a periodic empty snapshot never reverts a legacy placement");
        assertTrue(current.switchFor(new CompoundTag(), true));
        assertEquals(DreamTemplateModel.DYEDREAM, current.revision());
        var forged = snapshot.copy();
        forged.putDouble("partial", 1);
        assertFalse(DreamTemplateModel.validLegacySnapshot(forged));
        assertFalse(current.accepts(forged));
    }

    @Test void restyledTemplatesKeepThePlateBoundsAndApproachOfTheOriginal() throws Exception {
        var manifest = JsonParser.parseString(Files.readString(ROOT.resolve("structure_manifest.json"))).getAsJsonObject();
        int restyled = 0;
        for (var raw : manifest.getAsJsonArray("structures")) {
            var item = raw.getAsJsonObject();
            if (!item.has("runtime_templates")) continue;
            var templates = item.getAsJsonObject("runtime_templates");
            String current = templates.get("new_construction").getAsString();
            if (!current.endsWith("_dyedream_v2")) continue;
            String base = item.get("id").getAsString();
            assertEquals(base, templates.get("legacy_saved_model").getAsString());
            assertEquals(DreamTemplateModel.template(base, DreamTemplateModel.DYEDREAM), current);
            // The tower's current-geometry predecessor is its garrison model, not the solid legacy column.
            var original = read(templates.has("garrison_saved_model") ? templates.get("garrison_saved_model").getAsString() : base);
            var next = read(current);
            assertEquals(original.getList("size", 3), next.getList("size", 3), base + " keeps its bounding box");
            Set<String> plate = new LinkedHashSet<>(), nextPlate = new LinkedHashSet<>(), solid = solidCells(next);
            for (String cell : solidCells(original)) if (cell.matches("\\[\\d+,0,\\d+]")) plate.add(cell);
            for (String cell : solid) if (cell.matches("\\[\\d+,0,\\d+]")) nextPlate.add(cell);
            assertEquals(plate, nextPlate, base + " keeps its ground plate");
            for (var access : item.getAsJsonArray("access_xz")) {
                var xz = access.getAsJsonArray();
                for (int y = 1; y <= 3; y++)
                    assertFalse(solid.contains("[" + xz.get(0).getAsInt() + "," + y + "," + xz.get(1).getAsInt() + "]"),
                            current + " blocks the approach at " + xz + " y=" + y);
            }
            var palette = next.getList("palette", 10);
            for (int i = 0; i < palette.size(); i++)
                assertFalse(VANILLA_SHELL.contains(palette.getCompound(i).getString("Name")), current + " still uses " + palette.getCompound(i));
            restyled++;
        }
        assertEquals(10, restyled);
    }

    @Test void restyledWatchtowerKeepsTheGarrisonDeckSeatsAndExits() throws Exception {
        Set<String> solid = solidCells(read("shadow_watchtower_dyedream_v2"));
        assertEquals(216, solid.size(), "DreamWatchtowerPlacement.count(2, true)");
        for (int x = 1; x <= 5; x++) {
            assertTrue(solid.contains("[" + x + ",7,5]"), "seat row floor at x=" + x);
            for (int z = 3; z <= 5; z++)
                for (int y = 8; y <= (x >= 2 && x <= 4 ? 10 : 9); y++) assertFalse(solid.contains("[" + x + "," + y + "," + z + "]"), "garrison headroom");
        }
        for (int x = 2; x <= 4; x++) {
            assertTrue(solid.contains("[" + x + ",0,1]"), "exit floor");
            for (int y = 1; y <= 2; y++) assertFalse(solid.contains("[" + x + "," + y + ",1]"), "exit clearance");
        }
    }
}
