package com.dreamseekers.resonance;

import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Short-lived owner-private view. Late responses cannot target a replaced building or connection. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID,value=Dist.CLIENT)
public final class ResonanceClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static final Map<BuildingPlacement,View> VIEWS=new WeakHashMap<>();
    private static Level level;private static Connection connection;
    private static final class View{final UUID nonce=UUID.randomUUID();final String owner;long queried=Long.MIN_VALUE,received;ResonancePackets.Snapshot snapshot;View(String owner){this.owner=owner;}}
    private ResonanceClient(){}
    @Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup{@SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->com.solegendary.reignofnether.api.BuildingUnitOrderHooks.registerButtons(ResonanceClient::append));}}
    private static void clearView(){VIEWS.clear();}
    public static void clear(){level=null;connection=null;CLOCK.clear();clearView();}
    private static boolean own(BuildingPlacement p){var mc=Minecraft.getInstance();return p!=null&&mc.player!=null&&mc.level!=null&&p.getLevel()==mc.level
        &&ResonanceNetwork.isDais(p)&&p.isBuilt&&p.ownerName.equals(mc.player.getName().getString())&&BuildingClientEvents.getBuildings().contains(p)
        &&PlayerClientEvents.getRTSPlayer(p.ownerName)!=null;}
    private static void context(){var mc=Minecraft.getInstance();Connection c=mc.getConnection()==null?null:mc.getConnection().getConnection();if(CLOCK.context(mc.level,c))clearView();level=mc.level;connection=c;
        VIEWS.entrySet().removeIf(e->!own(e.getKey())||!e.getValue().owner.equals(e.getKey().ownerName));}
    private static void query(BuildingPlacement p){if(!own(p)||connection==null)return;var v=VIEWS.computeIfAbsent(p,key->new View(p.ownerName));
        if(CLOCK.queryDue(v.queried,20)){v.queried=CLOCK.now();ResonancePackets.CHANNEL.sendToServer(new ResonancePackets.Request(
            new ResonancePackets.Payload(ResonancePackets.Action.QUERY,level.dimension().location(),p.originPos,v.nonce,ResonancePackets.ZERO,0,0,0),ResonancePackets.ZERO,ResonancePackets.ZERO,0));}}
    private static ResonancePackets.Snapshot state(BuildingPlacement p){var v=VIEWS.get(p);return own(p)&&v!=null&&v.snapshot!=null&&CLOCK.fresh(v.received,60)&&ExtendedResourcesClient.ready()&&ExtendedResourcesClient.epoch().equals(v.snapshot.epoch())?v.snapshot:null;}
    private static long cooldown(BuildingPlacement p){var s=state(p);if(s==null)return 0;return CLOCK.remaining(VIEWS.get(p).received,s.cooldown());}
    public static void accept(ResonancePackets.Snapshot packet,Connection source){context();var mc=Minecraft.getInstance();if(connection==null||source!=connection||mc.player==null||!packet.valid()||packet.request()<0
        ||!mc.player.getName().getString().equals(packet.owner()))return;
        // Shared completions survive a closed/replaced view or dimension transition.
        if(!EconomyActionClient.accept(packet.stream(),packet.order(),packet.session(),packet.epoch(),packet.request()))return;
        if(level==null||!level.dimension().location().equals(packet.dimension()))return;
        var host=VIEWS.keySet().stream().filter(p->own(p)&&p.originPos.equals(packet.origin())&&VIEWS.get(p).nonce.equals(packet.view())).findFirst().orElse(null);
        if(host==null)return;
        var v=VIEWS.get(host);v.snapshot=packet;v.received=CLOCK.now();if(!"ready".equals(packet.status()))mc.player.displayClientMessage(Component.translatable("resonance."+packet.status()),true);}
    private static void pulse(BuildingPlacement p){var s=state(p);if(s==null||!EconomyActionClient.ready(s.epoch())){query(p);return;}
        var payload=new ResonancePackets.Payload(ResonancePackets.Action.PULSE,s.dimension(),s.origin(),s.view(),s.instance(),s.ownerRevision(),s.capture(),s.generation());
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("resonance_pulse",payload),(session,sequence)->new ResonancePackets.Request(payload,s.epoch(),session,sequence),ResonancePackets.CHANNEL::sendToServer);}
    private static ResourceLocation icon(String name){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/"+name+".png");}
    private static List<net.minecraft.util.FormattedCharSequence> tooltip(BuildingPlacement p,boolean pulse){var out=new ArrayList<net.minecraft.util.FormattedCharSequence>();var s=state(p);
        out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable(pulse?"resonance.pulse":"resonance.network")));
        if(s==null)out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("resonance.waiting")));else{
            out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("resonance.summary",s.rank(),s.active(),s.limit(),s.total())));
            if(!s.supported())out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("resonance.unsupported")));else if(s.rank()==0)out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("resonance.disconnected")));
            if(pulse){out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("resonance.actual_range",s.pulseRange())));out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("resonance.quote",ResonanceNetwork.COST,s.tuned()?15:10,s.tuned()?8:5)));if(cooldown(p)>0)out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("resonance.cooldown_seconds",(cooldown(p)+19)/20)));}
        }
        out.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable(pulse?"resonance.pulse_rules":"resonance.network_rules")));return out;}
    public static List<AbilityButton> append(BuildingPlacement p,List<AbilityButton> original){context();if(!own(p))return original;query(p);var out=new ArrayList<>(original);
        var info=new AbilityButton(Component.translatable("resonance.network").getString(),icon("ender_eye"),null,()->false,()->false,()->true,()->{},null,tooltip(p,false),null,p){
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY){tooltipLines=tooltip(p,false);super.renderTooltip(graphics,mouseX,mouseY);}};
        info.bottomLeftText=()->{var s=state(p);return s==null?"?":s.rank()>0?s.rank()+"/"+s.limit():"-";};out.add(info);
        var button=new AbilityButton(Component.translatable("resonance.pulse").getString(),icon("amethyst_shard"),null,()->false,()->false,
            ()->{var s=state(p);return s!=null&&s.supported()&&s.rank()>0&&cooldown(p)==0&&EconomyActionClient.ready(s.epoch());},()->pulse(p),null,tooltip(p,true),null,p){
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY){tooltipLines=tooltip(p,true);super.renderTooltip(graphics,mouseX,mouseY);}};
        button.bottomLeftText=()->cooldown(p)>0?com.dreamseekers.client.DreamTooltipLayout.cooldownBadge(cooldown(p)):Integer.toString(ResonanceNetwork.COST);out.add(button);return out;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();if(level!=null)for(var p:BuildingClientEvents.getSelectedBuildings())query(p);}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
