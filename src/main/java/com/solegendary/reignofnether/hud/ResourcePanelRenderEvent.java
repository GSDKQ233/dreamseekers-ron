package com.solegendary.reignofnether.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.eventbus.api.Event;
import java.util.ArrayList;
import java.util.List;

/** Client render extension; fired only inside the active top-down RTS resource panel.
 * No account data is exposed. A listener must reserve its row before drawing.
 * Reservations also prevent clicks from reaching the world behind the row.
 */
public final class ResourcePanelRenderEvent extends Event {
    private final GuiGraphics graphics;
    private final int x, screenWidth, mouseX, mouseY;
    private int bottomY;
    private final List<RectZone> zones = new ArrayList<>();
    public ResourcePanelRenderEvent(GuiGraphics graphics, int x, int y, int screenWidth, int mouseX, int mouseY) {
        this.graphics=graphics; this.x=x; this.bottomY=y; this.screenWidth=screenWidth;
        this.mouseX=mouseX; this.mouseY=mouseY;
    }
    public GuiGraphics graphics(){return graphics;}
    public int x(){return x;}
    public int bottomY(){return bottomY;}
    public int screenWidth(){return screenWidth;}
    public int mouseX(){return mouseX;}
    public int mouseY(){return mouseY;}
    public List<RectZone> zones(){return List.copyOf(zones);}
    public RectZone reserve(int width,int height){
        if(width<1||height<1||width>Math.max(1,screenWidth-x)||height>256)
            throw new IllegalArgumentException("Invalid resource row size");
        RectZone zone=RectZone.getZoneByLW(x,bottomY,width,height);
        zones.add(zone);bottomY+=height;return zone;
    }
}
