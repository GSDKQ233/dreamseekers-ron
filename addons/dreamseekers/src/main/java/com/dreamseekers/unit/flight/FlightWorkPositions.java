package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Endpoint selection only; movement always remains ordinary collision-aware navigation. */
public final class FlightWorkPositions {
    private FlightWorkPositions() { }

    public static Vec3 feet(BlockPos pos) { return new Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5); }

    public static boolean isClear(Mob mob, Vec3 feet) {
        double half = mob.getBbWidth() / 2d;
        AABB body = new AABB(feet.x - half, feet.y + .001, feet.z - half,
                feet.x + half, feet.y + mob.getBbHeight() - .001, feet.z + half);
        BlockPos pos = BlockPos.containing(feet);
        return mob.level().hasChunkAt(pos) && mob.level().getWorldBorder().isWithinBounds(pos)
                && feet.y >= mob.level().getMinBuildHeight()
                && feet.y + mob.getBbHeight() < mob.level().getMaxBuildHeight()
                && feet.y <= mob.level().getGameRules().getInt(GameRuleRegistrar.FLYING_MAX_Y_LEVEL)
                && mob.level().noCollision(mob, body)
                && mob.level().getFluidState(pos).isEmpty();
    }

    public static boolean isClear(Mob mob, BlockPos pos) { return isClear(mob, feet(pos)); }

    public static boolean clearLine(Mob mob, Vec3 from, Vec3 to, @Nullable BlockPos allowedHit) {
        var hit = mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || allowedHit != null && hit.getBlockPos().equals(allowedHit);
    }

    public static boolean atApproach(Mob mob, @Nullable BlockPos pos) {
        return pos != null && isClear(mob, pos) && mob.position().distanceToSqr(feet(pos)) <= 1.5 * 1.5
                && clearLine(mob, mob.getEyePosition(), feet(pos).add(0, mob.getEyeHeight(), 0), null);
    }


    public static boolean canWorkAtBuilding(Mob mob, BuildingPlacement building, BlockPos approach) {
        if (building == null || building.getLevel() != mob.level()) return false;
        if (!atApproach(mob, approach)) return false;
        return clearBuildingFace(mob, building, mob.getEyePosition());
    }

    private static boolean clearBuildingFace(Mob mob, BuildingPlacement building, Vec3 eye) {
        if (building == null || building.getLevel() != mob.level()) return false;
        Vec3 face = new Vec3(
                Math.max(building.minCorner.getX() + .5, Math.min(building.maxCorner.getX() + .5, eye.x)),
                Math.max(building.minCorner.getY() + .5, Math.min(building.maxCorner.getY() + .5, eye.y)),
                Math.max(building.minCorner.getZ() + .5, Math.min(building.maxCorner.getZ() + .5, eye.z)));
        var hit = mob.level().clip(new ClipContext(eye, face, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (hit.getType() == HitResult.Type.MISS) return true;
        // An actual outer block of the intended structure is an accessible face;
        // an unrelated wall between the worker and that structure is not.
        return building.getBlocks().stream().anyMatch(block -> block.getBlockPos().equals(hit.getBlockPos())
                && !block.getBlockState().isAir() && block.isPlaced(mob.level()));
    }

    /** Ground clicks resolve to feet one block above the surface, never +10. */
    @Nullable public static BlockPos move(Mob mob, @Nullable BlockPos clicked) {
        if (clicked == null) return null;
        BlockPos floor = clicked;
        // An air click (follow/attack-move/explore) still gets a local low endpoint.
        for (int down = 0; down < 8 && mob.level().getBlockState(floor).getCollisionShape(mob.level(), floor).isEmpty()
                && mob.level().getFluidState(floor).isEmpty(); down++) floor = floor.below();
        List<BlockPos> candidates = new ArrayList<>();
        for (int dy = 1; dy <= 3; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = floor.offset(dx, dy, dz);
                    if (isClear(mob, p)) candidates.add(p);
                }
        BlockPos preferred = floor.above();
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(preferred)));
        return pickReachable(mob, candidates);
    }

    /** Resource reach is unchanged (five blocks); use a close exposed work face, not farm-wide range. */
    public static List<BlockPos> resourceCandidates(Mob mob, BlockPos resource) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int dy = -3; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = resource.offset(dx, dy, dz);
                    if (isClear(mob, p)
                            && clearLine(mob, feet(p).add(0, mob.getEyeHeight(), 0), resource.getCenter(), resource))
                        candidates.add(p);
                }
        candidates.sort(Comparator.comparingDouble(p -> mob.position().distanceToSqr(feet(p))));
        return candidates;
    }

    @Nullable public static BlockPos resource(Mob mob, BlockPos resource) {
        // Close resources must have a genuinely reachable exposed face. Distant
        // manual orders may use partial routes until inside the path search range.
        boolean allowPartial = mob.position().distanceToSqr(resource.getCenter())
                > Math.pow(mob.getAttributeValue(Attributes.FOLLOW_RANGE) * .75, 2);
        return pickReachable(mob, resourceCandidates(mob, resource), allowPartial);
    }

    @Nullable public static BlockPos building(Mob mob, BuildingPlacement building) {
        if (building == null || building.getLevel() != mob.level()) return null;
        List<BlockPos> candidates = new ArrayList<>();
        for (int y = building.minCorner.getY(); y <= building.minCorner.getY() + 2; y++)
            for (int x = building.minCorner.getX() - 1; x <= building.maxCorner.getX() + 1; x++)
                for (int z = building.minCorner.getZ() - 1; z <= building.maxCorner.getZ() + 1; z++) {
                    if (x >= building.minCorner.getX() && x <= building.maxCorner.getX()
                            && z >= building.minCorner.getZ() && z <= building.maxCorner.getZ()) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    if (isClear(mob, p) && clearBuildingFace(mob, building, feet(p).add(0, mob.getEyeHeight(), 0)))
                        candidates.add(p);
                }
        candidates.sort(Comparator.comparingDouble(p -> mob.position().distanceToSqr(feet(p))));
        return pickReachable(mob, candidates);
    }

    @Nullable private static BlockPos pickReachable(Mob mob, List<BlockPos> candidates) {
        return pickReachable(mob, candidates, true);
    }

    @Nullable private static BlockPos pickReachable(Mob mob, List<BlockPos> candidates, boolean allowPartial) {
        if (mob.level().isClientSide()) return candidates.isEmpty() ? null : candidates.get(0);
        // Keep work endpoints stable after selection and limit expensive path probes.
        // A partial path is not evidence of arrival; failed endpoints cannot gather/deposit/build.
        for (int i = 0; i < Math.min(12, candidates.size()); i++) {
            BlockPos p = candidates.get(i);
            if (mob.position().distanceToSqr(feet(p)) < .25) return p;
            Path path = mob.getNavigation().createPath(p, 0);
            if (path != null && path.canReach()) return p;
        }
        // Allow a long journey to progress by vanilla partial paths; proximity/LOS
        // gates still forbid working at an unreached or wall-separated endpoint.
        return !allowPartial || candidates.isEmpty() ? null : candidates.get(0);
    }
}
