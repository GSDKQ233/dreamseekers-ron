package com.solegendary.reignofnether.unit;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.solegendary.reignofnether.unit.FormationDragMove.Source.MINIMAP;
import static com.solegendary.reignofnether.unit.FormationDragMove.Source.WORLD;
import static org.junit.jupiter.api.Assertions.*;

class FormationDragElevationTest {
    private static BlockPos p(int x, int y) { return new BlockPos(x, y, -7); }

    private static FormationDragMove.DragPlan world(BlockPos start, BlockPos end, int count, Predicate<BlockPos> support) {
        return FormationDragMove.planDrag(start, end, count, WORLD, support, ignored -> {
            throw new AssertionError("World gestures cannot sample the minimap's top column");
        });
    }

    @Test void worldSelectsEitherFloorOrDeckUsingTheExactSameXZLayout() {
        List<BlockPos> lower = List.of(p(1, 3), p(3, 3), p(5, 3), p(7, 3));
        List<BlockPos> deck = lower.stream().map(pos -> pos.above(4)).toList();
        Predicate<BlockPos> terrain = pos -> lower.contains(pos) || deck.contains(pos);
        assertEquals(lower, world(p(0, 3), p(8, 3), 4, terrain).preview());
        assertEquals(deck, world(p(0, 7), p(8, 7), 4, terrain).preview());
    }

    @Test void exactMidpointRoundingAndRampAdjustmentAreIndependentOfDirection() {
        BlockPos start = p(0, -4), end = p(6, -1);
        List<BlockPos> expectedProjection = List.of(p(1, -3), p(3, -2), p(5, -1));
        List<BlockPos> ramp = List.of(p(1, -4), p(3, -1), p(5, -1));
        var forwards = world(start, end, 3, ramp::contains);
        assertEquals(expectedProjection, forwards.slots().stream().map(FormationDragMove.Slot::projected).toList());
        assertEquals(ramp, forwards.preview());
        var reverse = new ArrayList<>(ramp);
        Collections.reverse(reverse);
        assertEquals(reverse, world(end, start, 3, ramp::contains).preview());
    }

    @Test void resolversRespectTheLocalStepBudgetAndPreferLowerOnEqualDistance() {
        // Projected Y values at X 2, 6 and 10 from these endpoints are -1, 1 and 3.
        BlockPos start = p(0, -2), end = p(12, 4);
        Set<BlockPos> terrain = Set.of(p(2, 1), p(6, 0), p(6, 2), p(10, 1));
        List<BlockPos> probed = new ArrayList<>();
        var plan = world(start, end, 3, pos -> {
            probed.add(pos);
            return terrain.contains(pos);
        });
        assertEquals(List.of(p(6, 0), p(10, 1)), plan.preview());
        assertFalse(probed.contains(p(2, 1)), "two blocks up is forbidden");
        assertFalse(probed.contains(p(6, 2)), "use the lower equally near support first");
        assertTrue(probed.contains(p(10, 1)), "two blocks down can absorb a step");
        assertTrue(world(start, end, 3, p(10, 0)::equals).preview().isEmpty(), "three blocks down is forbidden");
        assertEquals(List.of(p(6, 1)), world(start, end, 3, Set.of(p(6, 0), p(6, 1), p(6, 2))::contains).preview());
    }

    @Test void aHoleOrUnloadedColumnConsumesItsSlotWithoutSendingThatUnitAnywhere() {
        BlockPos a = p(1, 8), hole = p(3, 8), c = p(5, 8);
        var plan = world(p(0, 8), p(6, 8), 3, pos -> {
            // The hole's column is unavailable at floor height, even if its
            // deck would be usable. The chosen equal-height layer is decisive.
            assertEquals(8, pos.getY());
            return pos.equals(a) || pos.equals(c);
        });
        assertEquals(List.of(a, c), plan.preview());
        assertNull(plan.slots().get(1).target());
        List<BlockPos> unitPositions = List.of(p(0, 8), hole, p(10, 8));
        var assigned = plan.assign(unitPositions, pos -> pos);
        assertEquals(List.of(Pair.of(unitPositions.get(0), a), Pair.of(unitPositions.get(2), c)), assigned);
        assertEquals(plan.preview(), assigned.stream().map(Pair::getSecond).toList());
        var allInvalid = world(p(0, 8), p(6, 8), 3, pos -> pos.equals(hole.above()));
        assertTrue(allInvalid.preview().isEmpty());
        assertTrue(allInvalid.assign(unitPositions, pos -> pos).isEmpty());
    }

    @Test void singleUnitUsesOnlyTheReleasedWorldSurfaceButMinimapKeepsTopSurface() {
        BlockPos start = p(0, 8), end = p(8, 3);
        assertEquals(List.of(end), world(start, end, 1, end::equals).preview());
        assertTrue(world(start, end, 1, end.above()::equals).preview().isEmpty());
        var map = FormationDragMove.planDrag(start, end, 1, MINIMAP, pos -> {
            throw new AssertionError("Minimap must not sample a world layer");
        }, pos -> pos.atY(21));
        assertEquals(List.of(end.atY(21)), map.preview());
        var mapLine = FormationDragMove.planDrag(p(0, 3), p(6, 8), 3, MINIMAP, pos -> false, pos -> pos.atY(21));
        assertEquals(List.of(p(1, 21), p(3, 21), p(5, 21)), mapLine.preview());
    }

    @Test void anOppositeViewCannotTakeOverUpdateOrConsumeTheGesture() {
        try {
            FormationDragMove.startDrag(p(2, 5), WORLD);
            FormationDragMove.startDrag(p(2, 20)); // legacy caller has only a top-surface intent
            assertTrue(FormationDragMove.isDragging(WORLD));
            assertFalse(FormationDragMove.isDragging(MINIMAP));
            FormationDragMove.updateDrag(p(8, 20), 3, null, MINIMAP);
            assertEquals(6, FormationDragMove.getLineEnd().y);
            assertTrue(FormationDragMove.endDrag(List.of(), MINIMAP).isEmpty());
            assertTrue(FormationDragMove.endDrag(List.of()).isEmpty());
            assertTrue(FormationDragMove.isDragging(WORLD));
            FormationDragMove.endDrag(List.of(), WORLD);
            assertFalse(FormationDragMove.isDragging());
            FormationDragMove.startDrag(p(2, 20), MINIMAP);
            assertTrue(FormationDragMove.endDrag(List.of(), WORLD).isEmpty());
            assertTrue(FormationDragMove.isDragging(MINIMAP));
        } finally {
            FormationDragMove.cancelDrag();
        }
    }

    @Test void plannedSupportIsUnchangedInTheNativePacketCodecWithOrWithoutShift() {
        BlockPos selectedSupport = p(8, -4);
        var plan = world(p(0, 8), selectedSupport, 1, selectedSupport::equals);
        BlockPos target = plan.assign(List.of(42), id -> BlockPos.ZERO).get(0).getSecond();
        assertEquals(plan.preview().get(0), target);
        for (boolean queue : new boolean[]{false, true}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new UnitActionServerboundPacket("owner", UnitAction.MOVE, -1, new int[]{42},
                        target, BlockPos.ZERO, queue).encode(buf);
                assertEquals("owner", buf.readUtf());
                assertEquals(UnitAction.MOVE, buf.readEnum(UnitAction.class));
                assertEquals(-1, buf.readInt());
                assertArrayEquals(new int[]{42}, buf.readVarIntArray());
                assertEquals(selectedSupport, buf.readBlockPos());
                assertEquals(BlockPos.ZERO, buf.readBlockPos());
                assertEquals(queue, buf.readBoolean());
                assertEquals(0, buf.readableBytes());
            } finally {
                buf.release();
            }
        }
    }
}
