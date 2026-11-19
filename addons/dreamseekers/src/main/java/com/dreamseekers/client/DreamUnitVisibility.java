package com.dreamseekers.client;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.DreamUnitVisibilitySubject;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.event.GeoRenderEvent;

/** Gecko renderers bypass vanilla RenderLivingEvent.Pre; preserve the same fog/border policy. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DreamUnitVisibility {
    private DreamUnitVisibility() {}

    @SubscribeEvent
    public static void beforeGeoEntity(GeoRenderEvent.Entity.Pre event) {
        var entity = event.getEntity();
        if (!DreamUnitVisibilitySubject.matches(entity)) return; // Never change natural PasterDream creatures.
        var level = Minecraft.getInstance().level;
        boolean insideBorder = level == null || level.getWorldBorder().isWithinBounds(entity.getOnPos());
        if (DreamUnitVisibilityPolicy.cancelModel(true, level != null, insideBorder,
                FogOfWarClientEvents.isInBrightChunk(entity))) {
            event.setCanceled(true);
        }
    }
}
