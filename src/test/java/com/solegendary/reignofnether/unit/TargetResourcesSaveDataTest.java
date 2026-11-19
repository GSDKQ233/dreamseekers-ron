package com.solegendary.reignofnether.unit;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Startup decoding must not require any loaded building placements or Minecraft world. */
class TargetResourcesSaveDataTest {
    @Test
    void unknownFarmOriginSurvivesDecodeAndResaveUsingExistingKeys() {
        CompoundTag entry = new CompoundTag();
        entry.putString("uuid", "saved-worker");
        entry.putString("targetResourceName", "NONE");
        entry.putInt("farmX", -4096);
        entry.putInt("farmY", 90);
        entry.putInt("farmZ", 8192);
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag root = new CompoundTag();
        root.put("targetResourcesData", entries);

        TargetResourcesSaveData decoded = TargetResourcesSaveData.load(root);
        TargetResourcesSave task = decoded.targetData.get(0);
        assertNull(task.targetFarm, "decoder must not eagerly look up unloaded buildings");
        assertEquals(new BlockPos(-4096, 90, 8192), task.getFarmOrigin());
        assertTrue(task.hasData(), "a pending farm-only command must not be dropped as empty");
        CompoundTag roundTrip = decoded.save(new CompoundTag());
        assertEquals(root, roundTrip, "old farmX/Y/Z save format remains byte-model compatible");
        TargetResourcesSave repeated = TargetResourcesSaveData.load(roundTrip).targetData.get(0);
        assertEquals(task.getFarmOrigin(), repeated.getFarmOrigin());
        assertEquals(task.unitUUID, repeated.unitUUID);

        repeated.delete();
        assertNull(repeated.getFarmOrigin());
        assertFalse(repeated.hasData(), "STOP/deletion must not retain pending farm work");
    }
}
