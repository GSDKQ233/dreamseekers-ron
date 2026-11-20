package com.solegendary.reignofnether.building;

import net.minecraft.gametest.framework.*;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("api65_repair3_scenario")
@PrefixGameTestTemplate(false)
public class Repair3OpaqueBuildingGameTests {
    private static CompoundTag row(String key, int x) {
        var row = new CompoundTag();
        row.putString("buildingKey", key);
        row.putString("ownerName", "Old");
        row.putInt("x", x); row.putInt("y", 70); row.putInt("z", 9);
        row.putString("rotation", "NONE"); row.putString("portalType", "BASIC");
        row.putString("customUnrecognizedField", "preserve me");
        return row;
    }

    private static CompoundTag diskRoundTrip(CompoundTag tag) throws IOException {
        var file = Files.createTempFile("repair3-opaque-fixture-", ".nbt");
        try {
            NbtIo.writeCompressed(tag, file.toFile());
            return NbtIo.readCompressed(file.toFile());
        } finally { Files.deleteIfExists(file); }
    }

    @GameTest(templateNamespace="api65_repair3_scenario", template="ore_mine_test_arena", batch="repair3_opaque_rows")
    public static void activeEditsAndOpaqueRecordsSurviveRepeatedCodecRoundTrips(GameTestHelper h) throws IOException {
        var server = h.getLevel().getServer();
        var known = row("reignofnether:villager_house", 3);
        var extra = new CompoundTag(); extra.putString("key", "absentmod:clock");
        var payload = new CompoundTag(); payload.putIntArray("calibration", new int[]{7, -1, 29});
        extra.put("data", payload); extra.putBoolean("unknownFlag", true);
        var storage = new ListTag(); storage.add(extra); known.put("dataStorage", storage);
        var unknown = row("absentmod:building", 4);
        var conflict = row("absentmod:other", 3);
        var future = row("reignofnether:villager_house", 5); future.putString("rotation", "FUTURE_ROTATION");
        var rows = new ListTag();
        rows.add(known); rows.add(unknown); rows.add(conflict); rows.add(future);
        var root = new CompoundTag(); root.put("buildings", rows); root.putLong("futureRoot", 9223372036854775807L);
        var loaded = BuildingSaveData.load(root, server);
        h.assertTrue(loaded.buildings.size() == 1 && loaded.buildings.get(0).building == Buildings.VILLAGER_HOUSE,
                "only known and supported building materializes");
        loaded.buildings.get(0).ownerName = "New";
        for (int n = 0; n < 2; n++) {
            var saved = diskRoundTrip(loaded.save(new CompoundTag()));
            var serial = saved.getList("buildings", Tag.TAG_COMPOUND);
            h.assertTrue(serial.size() == 3 && serial.contains(unknown) && serial.contains(future),
                    "unsupported definitions and enum versions remain byte-equivalent NBT, not fabricated buildings");
            h.assertTrue(saved.getLong("futureRoot") == Long.MAX_VALUE && serial.getCompound(0).getString("ownerName").equals("New")
                    && serial.getCompound(0).getString("customUnrecognizedField").equals("preserve me")
                    && serial.getCompound(0).getList("dataStorage", Tag.TAG_COMPOUND).contains(extra),
                    "current live change, extra top-level property and absent extension payload survive together");
            var parked = saved.getList("repair3RetainedBuildingConflicts", Tag.TAG_COMPOUND);
            h.assertTrue(parked.size() == 1 && parked.getCompound(0).equals(conflict),
                    "same-origin unknown record parked once; never discarded or loaded over current building");
            loaded = BuildingSaveData.load(saved, server);
        }
        var neverUnderstood = new CompoundTag(); neverUnderstood.putString("buildings", "future-envelope");
        neverUnderstood.putIntArray("extension", new int[]{3, 2, 1});
        h.assertTrue(BuildingSaveData.load(neverUnderstood, server).save(new CompoundTag()).equals(neverUnderstood),
                "refuse to replace an incompatible whole container with an empty world");
        h.succeed();
    }
}
