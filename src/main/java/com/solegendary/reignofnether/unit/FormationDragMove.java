package com.solegendary.reignofnether.unit;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class FormationDragMove {

    public enum Source { WORLD, MINIMAP }

    // Nearest first, lower on a tie. In addition to this local step bound,
    // the two clicked surfaces bound the actual layer range.
    private static final int[] WORLD_STEP_OFFSETS = {0, -1, 1, -2};
    private static BlockPos dragStartBp = null;
    private static BlockPos dragEndBp = null;
    @Nullable private static Source source;
    private static DragPlan plan = DragPlan.EMPTY;

    public static boolean isDragging() {
        return source != null;
    }

    public static boolean isDragging(Source origin) {
        return source != null && source == origin;
    }

    /** Legacy callers without a 3D hit keep the original top-surface policy. */
    public static void startDrag(BlockPos bp) {
        if (isDragging(Source.MINIMAP)) cancelDrag();
        startDrag(bp, Source.MINIMAP);
    }

    public static void startDrag(BlockPos bp, Source origin) {
        // In particular, crossing between the main view and minimap must not
        // change the origin (and selected layer) of an in-progress gesture.
        if (isDragging()) return;
        dragStartBp = bp;
        dragEndBp = bp;
        source = origin;
        plan = DragPlan.EMPTY;
    }

    public static void updateDrag(BlockPos currentBp, int unitCount, Level level) {
        updateDrag(currentBp, unitCount, level, Source.MINIMAP);
    }

    public static void updateDrag(BlockPos currentBp, int unitCount, Level level, Source origin) {
        if (!isDragging(origin)) return;
        dragEndBp = currentBp;
        plan = planDrag(dragStartBp, dragEndBp, unitCount, origin,
                pos -> isWorldSupport(level, pos), pos -> getHeightAdjustedPos(level, pos));
    }

    public static List<BlockPos> getFormationTargets() {
        return plan.preview();
    }

    public static Vec3 getLineStart() {
        if (dragStartBp == null) return Vec3.ZERO;
        return new Vec3(dragStartBp.getX() + 0.5, dragStartBp.getY() + 1.0, dragStartBp.getZ() + 0.5);
    }

    public static Vec3 getLineEnd() {
        if (dragEndBp == null) return Vec3.ZERO;
        return new Vec3(dragEndBp.getX() + 0.5, dragEndBp.getY() + 1.0, dragEndBp.getZ() + 0.5);
    }

    public static List<Pair<LivingEntity, BlockPos>> endDrag(List<LivingEntity> units) {
        return endDrag(units, Source.MINIMAP);
    }

    public static List<Pair<LivingEntity, BlockPos>> endDrag(List<LivingEntity> units, Source origin) {
        if (!isDragging(origin)) return List.of();
        List<Pair<LivingEntity, BlockPos>> result = plan.assign(units, LivingEntity::getOnPos);
        cancelDrag();
        return result;
    }

    public static void cancelDrag() {
        dragStartBp = null;
        dragEndBp = null;
        plan = DragPlan.EMPTY;
        source = null;
    }

    static DragPlan planDrag(BlockPos dragStart, BlockPos dragEnd, int unitCount, Source origin,
                             Predicate<BlockPos> worldSupport, UnaryOperator<BlockPos> minimapSurface) {
        if (unitCount <= 0) return DragPlan.EMPTY;

        if (unitCount == 1) {
            BlockPos target = origin == Source.WORLD ? (worldSupport.test(dragEnd) ? dragEnd : null)
                    : minimapSurface.apply(dragEnd);
            return new DragPlan(List.of(new Slot(dragEnd, target)));
        }

        Vec3 start = Vec3.atCenterOf(dragStart);
        Vec3 end = Vec3.atCenterOf(dragEnd);
        int fromY = dragStart.getY(), toY = dragEnd.getY();
        int minY = Math.min(fromY, toY), maxY = Math.max(fromY, toY);
        List<Slot> slots = new ArrayList<>(unitCount);

        for (int i = 0; i < unitCount; i++) {
            double t = (i + 0.5) / unitCount;
            double x = start.x + (end.x - start.x) * t;
            double z = start.z + (end.z - start.z) * t;
            // Exact rounded interpolation so half-block ties use the same
            // height regardless of drag direction, including below zero.
            long denominator = 2L * unitCount, numerator = 2L * i + 1;
            int y = origin == Source.WORLD
                    ? (int) Math.floorDiv((denominator - numerator) * fromY + numerator * toY + unitCount, denominator)
                    : fromY;
            BlockPos projected = new BlockPos((int) Math.floor(x), y, (int) Math.floor(z));
            BlockPos target = origin == Source.WORLD ? resolveWorldSupport(projected, minY, maxY, worldSupport)
                    : minimapSurface.apply(projected);
            slots.add(new Slot(projected, target));
        }
        return new DragPlan(slots);
    }

    @Nullable
    private static BlockPos resolveWorldSupport(BlockPos projected, int minY, int maxY, Predicate<BlockPos> worldSupport) {
        for (int step : WORLD_STEP_OFFSETS) {
            int y = projected.getY() + step;
            if (y < minY || y > maxY) continue;
            BlockPos candidate = step == 0 ? projected : projected.offset(0, step, 0);
            if (worldSupport.test(candidate)) return candidate;
        }
        return null;
    }

    static boolean isWorldSupport(Level level, BlockPos support) {
        // Classify feet, not the support itself. Both feet/head are in the
        // same loaded column; never use the world's heightmap for a world drag.
        if (level.isOutsideBuildHeight(support) || level.isOutsideBuildHeight(support.above(2))
                || !level.getWorldBorder().isWithinBounds(support) || !level.hasChunkAt(support))
            return false;
        if (!MiscUtil.isSolidBlocking(level, support) && level.getFluidState(support).isEmpty()) return false;
        return WalkabilityBuilder.classify(level, support.getX(), support.getY() + 1, support.getZ())
                != WalkabilityBuilder.KIND_BLOCKED;
    }

	private static BlockPos getHeightAdjustedPos(Level level, BlockPos bp) {
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bp.getX(), bp.getZ()) - 1;
		BlockPos checkBp = new BlockPos(bp.getX(), groundY, bp.getZ());
		while (groundY > level.getMinBuildHeight()) {
			BlockState bs = level.getBlockState(checkBp);
			boolean isLeaves = bs.getBlock() instanceof LeavesBlock;
			boolean isPassable = !MiscUtil.isSolidBlocking(level, checkBp) && bs.getFluidState().isEmpty();
			if (!isLeaves && !isPassable) break;
			groundY--;
			checkBp = new BlockPos(bp.getX(), groundY, bp.getZ());
		}
		return new BlockPos(bp.getX(), groundY, bp.getZ());
	}

    record Slot(BlockPos projected, @Nullable BlockPos target) {}

    /** Immutable preview and assignment use the same resolved positions. */
    record DragPlan(List<Slot> slots, List<BlockPos> preview) {
        private static final DragPlan EMPTY = new DragPlan(List.of());

        private DragPlan(List<Slot> slots) {
            this(List.copyOf(slots), slots.stream().map(Slot::target).filter(Objects::nonNull).toList());
        }

        <T> List<Pair<T, BlockPos>> assign(List<T> units, Function<T, BlockPos> position) {
            List<Pair<T, BlockPos>> result = new ArrayList<>();
            List<T> unassigned = new ArrayList<>(units);
            for (Slot slot : slots) {
                // A missing support still occupies its original slot. It does not
                // transfer the next unit's order or issue an order to its own unit.
                BlockPos reference = slot.target() != null ? slot.target() : slot.projected();
                T closest = null;
                double closestDist = Double.MAX_VALUE;
                for (T unit : unassigned) {
                    double dist = position.apply(unit).distSqr(reference);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = unit;
                    }
                }
                if (closest == null) break;
                if (slot.target() != null) result.add(Pair.of(closest, slot.target()));
                unassigned.remove(closest);
            }
            return result;
        }
    }
}
