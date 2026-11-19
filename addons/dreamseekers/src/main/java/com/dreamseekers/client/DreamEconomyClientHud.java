package com.dreamseekers.client;
import com.solegendary.reignofnether.client.ActiveClientTickClock;

import com.dreamseekers.building.DreamBuildingSupport;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.hud.ResourcePanelRenderEvent;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;

/** Always renders the authenticated local account, never a selected ally/observed player's balance. */
@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class DreamEconomyClientHud {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private DreamEconomyClientHud(){}
    private static com.solegendary.reignofnether.hud.RectZone refillZone;
    private static net.minecraft.client.gui.screens.Screen refillScreen;
    private static net.minecraft.client.multiplayer.ClientLevel refillLevel;
    private static long refillTick;
    private static void clearView(){refillZone=null;refillScreen=null;refillLevel=null;refillTick=0;}
    private static void context(){var mc=Minecraft.getInstance();var connection=mc.getConnection()==null?null:mc.getConnection().getConnection();if(CLOCK.context(mc.level,connection))clearView();}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();}
    @SubscribeEvent public static void login(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e){CLOCK.clear();clearView();}
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){CLOCK.clear();clearView();}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void refill(net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        context();Minecraft mc=Minecraft.getInstance();
        if(event.getButton()!=0||refillZone==null||mc.player==null||mc.level==null||mc.options.hideGui
            ||mc.level!=refillLevel||mc.screen!=refillScreen||event.getScreen()!=refillScreen
            ||!CLOCK.fresh(refillTick,1)
            ||!com.solegendary.reignofnether.orthoview.OrthoviewClientEvents.isEnabled()
            ||!com.solegendary.reignofnether.sandbox.SandboxClientEvents.isSandboxPlayer()
            ||PlayerClientEvents.getPlayerFaction(mc.player.getName().getString())!=com.solegendary.reignofnether.faction.Factions.NONE
            ||!ExtendedResourcesClient.ready()||!refillZone.isMouseOver((int)event.getMouseX(),(int)event.getMouseY()))return;
        com.dreamseekers.economy.DreamSandboxEconomy.CHANNEL.sendToServer(new com.dreamseekers.economy.DreamSandboxEconomy.Refill());
        event.setCanceled(true);
    }
    @SubscribeEvent public static void draw(ResourcePanelRenderEvent event){
        context();clearView();
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.options.hideGui)return;
        var faction=PlayerClientEvents.getPlayerFaction(mc.player.getName().getString());
        boolean dream=com.dreamseekers.economy.DreamFactionAccess.allows(PlayerClientEvents.getRTSPlayer(mc.player.getName().getString()));
        boolean sandbox=faction==com.solegendary.reignofnether.faction.Factions.NONE&&com.solegendary.reignofnether.sandbox.SandboxClientEvents.isSandboxPlayer();
        int balance=ExtendedResourcesClient.balance(DreamPassiveEconomy.SHADOW);
        if(!ShadowHudPresentation.visible(ExtendedResourcesClient.ready(),dream,balance))return;
        int cap=DreamPassiveEconomy.SHADOW_CAP;
        Component title=Component.translatable(sandbox?"hud.dreamseekers.shadow_sandbox":dream?"hud.dreamseekers.shadow_own":"hud.dreamseekers.shadow_own_readonly");
        String amount=ShadowHudPresentation.amount(balance,cap);
        int width=ShadowHudPresentation.width(event.screenWidth(),mc.font.width(title),mc.font.width(amount));
        int x=event.x(), y=event.bottomY();
        var zone=event.reserve(width,32);
        if(sandbox){refillZone=zone;refillScreen=mc.screen;refillLevel=mc.level;refillTick=CLOCK.now();}
        var gui=event.graphics();
        gui.fill(x,y,x+width,y+32,0xD01B1528);
        gui.fill(x,y,x+2,y+32,dream?0xFF9774DB:0xFF80778B);
        gui.fill(x+2,y+31,x+width,y+32,0xFF49345E);
        gui.blit(DreamPassiveEconomy.SHADOW_ICON,x+5,y+8,0,0,16,16,16,16);
        // Capacity is an income ceiling, not a display clamp: legitimate refunds remain visible above it.
        gui.drawString(mc.font,title,x+26,y+5,dream?0xFFCBB6F1:0xFFB7AFC3,false);
        gui.drawString(mc.font,amount,x+26,y+18,ShadowHudPresentation.overflow(balance,cap)?0xFFFFD184:0xFFF2ECFF,false);
        if(zone.isMouseOver(event.mouseX(),event.mouseY())){
            var lines=new ArrayList<Component>();
            lines.add(Component.translatable("hud.dreamseekers.shadow_own"));
            lines.add(Component.translatable("hud.dreamseekers.shadow_sources"));
            if(sandbox)lines.add(Component.translatable("hud.dreamseekers.shadow_sandbox_refill",cap));
            lines.add(Component.translatable("hud.dreamseekers.shadow_capacity",cap));
            if(ShadowHudPresentation.overflow(balance,cap))lines.add(Component.translatable("hud.dreamseekers.shadow_overflow"));
            if(!dream)lines.add(Component.translatable("hud.dreamseekers.shadow_readonly_reason"));
            lines.add(Component.translatable("hud.dreamseekers.shadow_private"));
            gui.renderComponentTooltip(mc.font,lines,event.mouseX()+5,event.mouseY());
        }
    }
}
