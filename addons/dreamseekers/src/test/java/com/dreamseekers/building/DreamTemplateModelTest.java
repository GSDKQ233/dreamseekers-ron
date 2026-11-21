// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.dreamseekers.building.DreamTemplateModel.Revision.*;
import static org.junit.jupiter.api.Assertions.*;

class DreamTemplateModelTest {
    private static final Path ROOT = Path.of("addons/dreamseekers/src/main/resources");
    private static CompoundTag structure(String name) throws Exception {
        String path = "/dreamseekers/structures/" + name + ".nbt";
        assertArrayEquals(Files.readAllBytes(Path.of(ROOT + "/assets" + path)),
                Files.readAllBytes(Path.of(ROOT + "/data" + path)), name);
        try (var in = Files.newInputStream(Path.of(ROOT + "/data" + path))) { return NbtIo.readCompressed(in); }
    }
    private static Map<String, CompoundTag> solids(CompoundTag nbt, boolean foundationOnly) {
        var palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        var result = new HashMap<String, CompoundTag>();
        for (var raw : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
            var cell = (CompoundTag) raw;
            var pos = cell.getList("pos", Tag.TAG_INT);
            var state = palette.getCompound(cell.getInt("state"));
            if ((foundationOnly && pos.getInt(1) != 0) || "minecraft:air".equals(state.getString("Name"))) continue;
            assertNull(result.put(pos.toString(), state), "duplicate NBT cell " + pos);
        }
        return result;
    }

    @Test void descriptorsMatchBothFilesAndTheExactOriginalGroundLayer() throws Exception {
        assertEquals(9, DreamTemplateModel.values().length);
        for (var model : DreamTemplateModel.values()) {
            assertSame(model, DreamTemplateModel.forBuilding(model.resource(ORIGINAL)));
            var old = structure(model.resource(ORIGINAL));
            var current = structure(model.resource(REVIEWED));
            assertEquals(old.get("size"), current.get("size"));
            assertEquals(solids(old, true), solids(current, true), model + " y=0 must retain every original state");
            assertEquals(model.count(ORIGINAL), solids(old, false).size(), model + " original count");
            assertEquals(model.count(REVIEWED), solids(current, false).size(), model + " reviewed count");
            assertNotEquals(solids(old, false), solids(current, false), model.toString());
        }
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.forBuilding("dream_anchor"));
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.forBuilding("shadow_watchtower"));
    }

    private CompoundTag snapshot(DreamTemplateModel model, DreamTemplateModel.Revision rev, UUID token) {
        var t = new CompoundTag();
        t.putInt("schema", 2); t.putInt("revision", rev.id); t.putUUID("instance", token);
        t.putInt("highest", 29); t.putInt("placed", 20); t.putDouble("partial", .25);
        t.putString("kind", "dream_ordinary_building"); t.putString("template", model.resource(rev));
        return t;
    }

    @Test void validOnlyForExactBuildingRevisionAndPhysicalCounts() {
        var m = DreamTemplateModel.HALL;
        var old = snapshot(m, ORIGINAL, UUID.randomUUID());
        assertTrue(m.validSnapshot(old));
        assertFalse(m.validSnapshot(new CompoundTag()));
        assertFalse(DreamTemplateModel.DEPOT.validSnapshot(old));
        var t = old.copy(); t.putInt("revision", 3); assertFalse(m.validSnapshot(t));
        t = old.copy(); t.putString("template", "../structures/dream_hall"); assertFalse(m.validSnapshot(t));
        t = old.copy(); t.putInt("highest", m.count(ORIGINAL) + 1); assertFalse(m.validSnapshot(t));
        t = old.copy(); t.putInt("placed", old.getInt("highest") + 1); assertFalse(m.validSnapshot(t));
        t = old.copy(); t.putDouble("partial", Double.NaN); assertFalse(m.validSnapshot(t));
        t = old.copy(); t.remove("instance"); assertFalse(m.validSnapshot(t));
        t = old.copy(); t.putLong("revision", 1); assertFalse(m.validSnapshot(t));
    }

    @Test void firstPlaceChoosesGeometryExactlyOnceAndDeltasCannotRebind() {
        var m = DreamTemplateModel.DEPOT;
        var old = snapshot(m, ORIGINAL, UUID.randomUUID());
        var current = snapshot(m, REVIEWED, UUID.randomUUID());
        var client = new DreamTemplateModel.State(m, REVIEWED, null, 2);
        var swaps = new ArrayList<DreamTemplateModel.Revision>();
        assertFalse(client.acceptAndBind(old, false, swaps::add), "delta cannot initialize");
        assertTrue(client.acceptAndBind(old, true, swaps::add), "authoritative legacy PLACE");
        assertTrue(client.acceptAndBind(old.copy(), true, swaps::add), "same-instance duplicate");
        assertTrue(client.acceptAndBind(old.copy(), false, swaps::add), "same-instance delta");
        assertFalse(client.acceptAndBind(current, true, swaps::add), "replacement needs new client object");
        assertFalse(client.acceptAndBind(old.copy().merge(current), false, swaps::add));
        var wrongRevision = snapshot(m, REVIEWED, old.getUUID("instance"));
        assertFalse(client.acceptAndBind(wrongRevision, false, swaps::add));
        assertEquals(java.util.List.of(ORIGINAL), swaps);
        var replacement = new DreamTemplateModel.State(m, REVIEWED, null, 2);
        assertTrue(replacement.acceptAndBind(current, true, swaps::add), "default reviewed geometry needs no rebuild");
        assertFalse(replacement.acceptAndBind(old, false, swaps::add));
        assertEquals(java.util.List.of(ORIGINAL), swaps);
    }

    @Test void unknownOrPresentMalformedSavesNeverMasqueradeAsUntaggedOldSaves() {
        var saved = new CompoundTag();
        saved.putInt("schema", 2); saved.putInt("revision", 3); saved.putUUID("instance", UUID.randomUUID());
        var peaks = new CompoundTag(); peaks.putInt("resonance_dais", 47); saved.put("constructionPeak", peaks);
        saved.putString("extra", "retained");
        var decoded = DreamTemplateModel.decodeSavedTag(saved);
        assertEquals(saved, decoded); assertNotSame(saved, decoded);
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.decodeSavedTag(new CompoundTag()));
        for (int id : new int[]{0,2,4,999}) {
            var bad = saved.copy(); bad.putInt("revision", id);
            assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.decodeSavedTag(bad));
        }
        var badSchema = saved.copy(); badSchema.putInt("schema", 1);
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.decodeSavedTag(badSchema));
        var excess = saved.copy(); excess.getCompound("constructionPeak").putInt("resonance_dais", 400);
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.decodeSavedTag(excess));
        var wrongModel = saved.copy(); wrongModel.getCompound("constructionPeak").putInt("../missing", 2);
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.decodeSavedTag(wrongModel));
        var missing = saved.copy(); missing.remove("instance");
        assertThrows(IllegalArgumentException.class, () -> DreamTemplateModel.decodeSavedTag(missing));
    }
}
