// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;
import com.dreamseekers.production.pagination.ProductionPageState;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** KeyReleased.Pre cancellation prevents the core Post listener from selecting a new-page recipe. */
@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class DreamProductionPageInput {
    private DreamProductionPageInput(){}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onRelease(ScreenEvent.KeyReleased.Pre event){
        // Retire held releases even after switching away. Only the current legal Hall may cancel.
        var contexts=new java.util.ArrayList<Context>();
        for(var building:com.solegendary.reignofnether.building.BuildingClientEvents.getBuildings())
            if(building instanceof ProductionPlacement p&&p.productionButtons instanceof DreamProductionPages.PageButtons buttons)
                contexts.add(new Context(buttons.state,buttons.current()));
        route(event,contexts);
    }
    public record Context(ProductionPageState state,boolean current){}
    public static void route(ScreenEvent.KeyReleased.Pre event,java.util.List<Context> contexts){
        for(var context:contexts)gate(event,context.state(),context.current());
    }

    /** Real Forge event adapter, also exercised headlessly without pretending to render a client. */
    public static boolean gate(ScreenEvent.KeyReleased.Pre event,ProductionPageState state,boolean currentHallContext){
        boolean stale=state.consumeStaleRelease(event.getKeyCode());
        if(stale&&currentHallContext){event.setCanceled(true);return true;}
        return false;
    }
}
