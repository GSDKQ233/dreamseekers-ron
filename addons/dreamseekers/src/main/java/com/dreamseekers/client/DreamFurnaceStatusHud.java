// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.ResourcePanelRenderEvent;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Selected, locally owned furnace only. No balance reads, cached status, requests or mutations. */
@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class DreamFurnaceStatusHud {
    private DreamFurnaceStatusHud() {}

    @SubscribeEvent public static void draw(ResourcePanelRenderEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.options.hideGui)return;
        var placement=HudClientEvents.hudSelectedPlacement;
        // No sandbox/alliance bypass: selecting another account must never show its economy status.
        if(placement==null||!mc.player.getGameProfile().getName().equals(placement.ownerName)
                ||placement.getLevel()!=mc.level||!placement.isBuilt||placement.isDestroyedServerside
                ||placement.shouldBeDestroyed()||!BuildingClientEvents.getBuildings().contains(placement)
                ||!BuildingClientEvents.getSelectedBuildings().contains(placement))return;
        // This is the server-synchronized public owner faction, NOT the server's static player list.
        var owner=PlayerClientEvents.getRTSPlayer(placement.ownerName);
        if(owner==null||owner.faction==null)return;
        String reason=DreamPassiveEconomy.factionPauseReason(placement.getBuilding(),owner);
        if(reason==null)return;

        // Independent of the shadow balance HUD: a captured furnace can pause with a zero balance.
        int available=event.screenWidth()-event.x();
        if(available<=8)return;
        int width=Math.min(310,available);
        var title=Component.translatable("hud.dreamseekers.furnace_paused");
        var lines=mc.font.split(title,width-8);
        int height=8+mc.font.lineHeight*lines.size();
        if(height>256)return;
        int x=event.x(), y=event.bottomY();
        var zone=event.reserve(width,height);
        var gui=event.graphics();
        gui.fill(x,y,x+width,y+height,0xD01B1528);
        for(int i=0;i<lines.size();i++)
            gui.drawString(mc.font,lines.get(i),x+4,y+4+i*mc.font.lineHeight,0xFFFFD184,false);
        if(zone.isMouseOver(event.mouseX(),event.mouseY())) {
            int wrapWidth=event.screenWidth()<=30?1:Math.min(310,event.screenWidth()-30);
            gui.renderTooltip(mc.font,mc.font.split(Component.translatable(reason),wrapWidth),event.mouseX()+5,event.mouseY());
        }
    }
}
