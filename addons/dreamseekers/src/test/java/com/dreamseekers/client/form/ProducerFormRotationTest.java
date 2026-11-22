// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client.form;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The actual client matrix must line up with the native cell transform, not rotate about
 * the unshifted corner of the zero cell. Includes either side of zero and non-integral cameras. */
class ProducerFormRotationTest {
    @Test void meshCentersMatchEveryNativeCellAfterEachQuarterTurn() {
        for (var origin : new BlockPos[] {new BlockPos(-80, 34, 253), new BlockPos(1024, -55, -1511)})
            for (var rotation : Rotation.values()) {
                var camera = new Vec3(origin.getX()-.375, origin.getY()+24.125, origin.getZ()+16.75);
                var nativeOffset = origin.above();
                var poses = new PoseStack();
                ProducerFormMount.place(poses, origin, rotation, camera);
                for (int x=0; x<13; x++) for (int z=0; z<11; z++) for (int y=0; y<15; y++) {
                    var cell = new BlockPos(x,y,z);
                    var world = nativeOffset.offset(cell.rotate(rotation));
                    assertEquals(world, ProducerFormMount.worldCell(origin,rotation,cell));
                    var rendered = poses.last().pose().transformPosition(new Vector3f(x+.5f,y+.5f,z+.5f));
                    assertEquals(world.getX()+.5-camera.x, rendered.x, .0001);
                    assertEquals(world.getY()+.5-camera.y, rendered.y, .0001);
                    assertEquals(world.getZ()+.5-camera.z, rendered.z, .0001);
                }
            }
    }
}
