// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.scout;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.PinkChickenScout;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID)
public final class ScoutMarkLifecycle {
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof PinkChickenScout scout && !scout.level().isClientSide()) scout.scoutMark().clear();
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof PinkChickenScout scout && !event.getLevel().isClientSide()) scout.scoutMark().detach();
    }
}
