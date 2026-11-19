package com.solegendary.reignofnether.unit;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for command payload equality: no world, navigation or mocked movement. */
class UnitActionItemTest {
    private UnitActionItem item(String owner, UnitAction action, int target, int[] selected,
                                BlockPos destination, BlockPos building) {
        return new UnitActionItem(owner, action, target, selected, destination, building);
    }
    @Test void everyPayloadFieldParticipatesIndependently() {
        BlockPos destination = new BlockPos(4, 0, 17), building = BlockPos.ZERO;
        UnitActionItem original = item("owner", UnitAction.MOVE, -1, new int[]{10, 20}, destination, building);
        assertTrue(original.equals(item("owner", UnitAction.MOVE, -1, new int[]{10, 20}, destination, building)));
        assertFalse(original.equals(item("other", UnitAction.MOVE, -1, new int[]{10, 20}, destination, building)));
        assertFalse(original.equals(item("owner", UnitAction.STOP, -1, new int[]{10, 20}, destination, building)));
        assertFalse(original.equals(item("owner", UnitAction.MOVE, 5, new int[]{10, 20}, destination, building)));
        assertFalse(original.equals(item("owner", UnitAction.MOVE, -1, new int[]{10, 21}, destination, building)));
        assertFalse(original.equals(item("owner", UnitAction.MOVE, -1, new int[]{10, 20}, new BlockPos(17, 0, 4), building)));
        assertFalse(original.equals(item("owner", UnitAction.MOVE, -1, new int[]{10, 20}, destination, building.above())));
        assertFalse(original.equals((UnitActionItem) null));
    }
    @Test void nullableFieldsNeverShortCircuitOtherPayloadDifferences() {
        UnitActionItem empty = item(null, null, -1, new int[]{10}, null, null);
        assertTrue(empty.equals(item(null, null, -1, new int[]{10}, null, null)));
        assertFalse(empty.equals(item("owner", null, -1, new int[]{10}, null, null)));
        assertFalse(empty.equals(item(null, UnitAction.MOVE, -1, new int[]{10}, null, null)));
        assertFalse(empty.equals(item(null, null, 4, new int[]{10}, null, null)));
        assertFalse(empty.equals(item(null, null, -1, new int[]{11}, null, null)));
        assertFalse(empty.equals(item(null, null, -1, new int[]{10}, BlockPos.ZERO, null)));
        assertFalse(empty.equals(item(null, null, -1, new int[]{10}, null, BlockPos.ZERO)));
    }
}
