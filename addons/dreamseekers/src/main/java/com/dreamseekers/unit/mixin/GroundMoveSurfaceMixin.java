// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.skill.SkillUnit;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The order retains the clicked support block. Only the grid's endpoint is a feet position. */
@Mixin(value = MoveToTargetBlockGoal.class, remap = false)
public abstract class GroundMoveSurfaceMixin {
    @Shadow @Final protected Mob mob;

    @ModifyArg(method = "start", remap = true, index = 1, require = 1, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/solegendary/reignofnether/unit/pathfinding/RtsPathfinder;requestPath(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/core/BlockPos;ILcom/solegendary/reignofnether/unit/pathfinding/MobilityClass;Ljava/util/function/BooleanSupplier;Lcom/solegendary/reignofnether/unit/pathfinding/PathCallback;)V"))
    private BlockPos dreamseekers$preferClickedSurface(BlockPos click) {
        if (!(mob instanceof SkillUnit unit) || unit.getMoveGoal() != (Object) this
                || !(mob.getNavigation() instanceof GroundPathNavigation)) return click;

        Level level = mob.level();
        if (!level.hasChunkAt(click) || !level.getWorldBorder().isWithinBounds(click)) return click;
        BlockState support = level.getBlockState(click);
        if (!support.isFaceSturdy(level, click, Direction.UP) || !support.getFluidState().isEmpty()
                || support.is(BlockTags.LEAVES) || WalkabilityBuilder.isFenceLike(support)) return click;

        // Normally the grid snaps a solid click to nearby feet. At a terrace lip a lower
        // neighbour is equally close and may win merely by being north/west. Prefer the
        // exact top only when the body fits; otherwise retain ordinary snapping and backoff.
        BlockPos feet = click.above();
        AABB body = mob.getBoundingBox().move(feet.getX() + .5D - mob.getX(), feet.getY() - mob.getY(),
                feet.getZ() + .5D - mob.getZ());
        if (body.maxY > level.getMaxBuildHeight() || !level.hasChunksAt(
                BlockPos.containing(body.minX, body.minY, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))
                || level.getBlockCollisions(mob, body).iterator().hasNext()) return click;
        return feet;
    }
}
