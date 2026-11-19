package com.dreamseekers.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GardenDecorationStateTest {
    @Test void unknownTagSurvivesTwoReadsAndWritesWithoutSharingMutableChildren() {
        var input = new CompoundTag();
        input.putInt("version", 37);
        var nested = new CompoundTag();
        nested.putLongArray("cells", new long[]{Long.MIN_VALUE, 0, 9007199254740993L});
        var names = new ListTag();
        names.add(StringTag.valueOf("b"));
        names.add(StringTag.valueOf("a"));
        nested.put("order", names);
        input.put("layout", nested);
        var expected = input.copy();

        var first = GardenDecorationState.read(input);
        nested.remove("cells");
        assertEquals(-1, first.knownVersion());
        assertThrows(IllegalStateException.class, first::migrated);
        assertEquals(expected, first.write());
        var output = first.write();
        output.getCompound("layout").remove("order");
        var second = GardenDecorationState.read(first.write());
        assertEquals(expected, second.write());
    }

    @Test void presentButEmptyOrWrongVersionTypeIsNotTheAbsentKeyDefault() {
        var wrongNumberType = new CompoundTag();
        wrongNumberType.putLong("version", 0L);
        var wrongTextType = new CompoundTag();
        wrongTextType.putString("version", "1");
        var negative = new CompoundTag();
        negative.putInt("version", -1);
        negative.putByte("diagnostic", (byte) 9);
        for (var raw : List.of(new CompoundTag(), wrongNumberType, wrongTextType, negative)) {
            var state = GardenDecorationState.read(raw);
            assertEquals(-1, state.knownVersion());
            assertEquals(raw, state.write());
        }
        assertEquals(0, GardenDecorationState.legacy().knownVersion());
        assertEquals(1, GardenDecorationState.current().knownVersion());
    }

    @Test void knownLegacyMigrationChangesOnlyItsVersion() {
        var raw = new CompoundTag();
        raw.putInt("version", 0);
        raw.putByteArray("note", new byte[]{3, 1, 4});
        var legacy = GardenDecorationState.read(raw);
        var expected = raw.copy();
        expected.putInt("version", 1);
        var current = legacy.migrated();
        assertEquals(raw, legacy.write());
        assertEquals(expected, current.write());
        assertEquals(1, GardenDecorationState.read(expected).knownVersion());
        assertThrows(IllegalStateException.class, current::migrated);
    }
}
