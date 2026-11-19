// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;
import com.dreamseekers.DreamseekersMod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
/** Original arena entry resets structures and uses parent-class victory scans: unsupported for these units.
 * Portal flags alone cannot block direct entity creation or ordinary saved-world admission.
 */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class AaroncosArenaAdmission {
    private AaroncosArenaAdmission() { }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void denyUnsupportedArena(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof ControlledAaroncosHand
                &&event.getLevel().dimension().location().equals(DreamseekersMod.paster("aaroncos_arena_world")))event.setCanceled(true);
    }
}
