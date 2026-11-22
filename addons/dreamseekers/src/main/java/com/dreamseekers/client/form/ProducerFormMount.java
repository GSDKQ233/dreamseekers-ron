// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client.form;

import com.dreamseekers.DreamseekersMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.building.BuildingPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/** Coordinates are the actual NBT cells, not the corner of a rotated bounding box. */
enum ProducerFormMount {
    GARDEN("moonflower", new BlockPos(10,6,5),
            new BlockPos(8,2,2), new BlockPos(12,2,2), new BlockPos(8,2,8), new BlockPos(12,2,8)),
    MINE("crystal_windlass", new BlockPos(5,6,2),
            new BlockPos(2,0,1), new BlockPos(8,0,1), new BlockPos(2,0,3), new BlockPos(8,0,3)),
    CAULDRON("eclipse_collar", new BlockPos(5,10,7),
            new BlockPos(5,8,3), new BlockPos(9,8,7), new BlockPos(5,8,10), new BlockPos(1,8,7)),
    CHIMNEY("eclipse_chimney", new BlockPos(6,13,8),
            new BlockPos(5,10,6), new BlockPos(7,10,8), new BlockPos(6,10,9), new BlockPos(4,10,7));

    final ResourceLocation model;
    final BlockPos light;
    private final BlockPos[] feet;

    ProducerFormMount(String mesh, BlockPos light, BlockPos... feet) {
        this.model = ResourceLocation.fromNamespaceAndPath(DreamseekersMod.MOD_ID, "producer_form/" + mesh);
        this.light = light;
        this.feet = feet;
    }

    static BlockPos worldCell(BlockPos origin, Rotation rotation, BlockPos local) {
        // Same order as BuildingUtils.getAbsoluteBlockData: rotate the cell about [0,0,0],
        // translate by origin in X/Z and by origin + 1 in Y.
        return origin.above().offset(local.rotate(rotation));
    }

    static float yaw(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> -90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 90;
        };
    }

    static void place(PoseStack poses, BlockPos origin, Rotation rotation, Vec3 camera) {
        // The first cell rotates in place: a continuous mesh therefore pivots about the
        // cell CENTER, not the corner. Using the corner shifts NW/SE forms by a full block.
        poses.translate(origin.getX() + .5 - camera.x, origin.getY() + 1 - camera.y,
                origin.getZ() + .5 - camera.z);
        poses.mulPose(Axis.YP.rotationDegrees(yaw(rotation)));
        poses.translate(-.5, 0, -.5);
    }

    boolean isSupported(BuildingPlacement p) {
        // Not a height map: use only positions actually matching THIS template/placement.
        // A destroyed pedestal never elevates the art onto a player's replacement block.
        for (BlockPos foot : feet)
            if (!p.isPosPartOfBuilding(worldCell(p.originPos, p.rotation, foot), true)) return false;
        return true;
    }
}
