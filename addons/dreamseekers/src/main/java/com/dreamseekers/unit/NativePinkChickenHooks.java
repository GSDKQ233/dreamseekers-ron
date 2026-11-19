// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.entity.PinkChickenEntity;
import net.pasterdream.procedures.PinkChickenPr0Procedure;

/** Entity-scoped isolation of the original mod's coordinate-only egg procedure. */
public final class NativePinkChickenHooks {
    private NativePinkChickenHooks() { }

    /** Wild chickens keep their original passive eggs; paid scouts never produce them. */
    public static boolean mayProduceNativeEgg(Entity entity) {
        return entity instanceof PinkChickenEntity && !(entity instanceof PinkChickenScout);
    }

    public static void invokeNativeEgg(PinkChickenEntity entity, LevelAccessor world,
                                       double x, double y, double z) {
        if (mayProduceNativeEgg(entity)) PinkChickenPr0Procedure.execute(world, x, y, z);
    }
}
