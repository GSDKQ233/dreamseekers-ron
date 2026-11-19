// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.gametest.mixin;

import com.dreamseekers.skill.MotherAuraNonLoadingSightGameTests;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only tripwire: unarmed calls are untouched. No cache query, cancellation or state repair.
 * Direct LOS tests arm a same-thread, try/finally-bounded scope around the actual production helper.
 */
@Mixin(ServerChunkCache.class)
public abstract class MotherAuraNoCreateTripwireMixin {
    @Inject(method="getChunkFutureMainThread",at=@At("HEAD"))
    private void dreamseekers$noCreateTripwire(int x,int z,ChunkStatus status,boolean create,CallbackInfoReturnable<?> result) {
        MotherAuraNonLoadingSightGameTests.rejectCreate(x,z,create);
        com.dreamseekers.BuildingAbilityNoLoadGameTests.rejectCreate(x,z,create);
    }
}
