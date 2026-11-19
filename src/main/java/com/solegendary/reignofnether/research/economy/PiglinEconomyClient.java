package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.resources.ResourcesClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID,value=Dist.CLIENT)
public final class PiglinEconomyClient {
    private static final Map<BuildingPlacement,PiglinEconomyStatusPacket> states=new WeakHashMap<>();
    private static final Map<BuildingPlacement,Long> queryTicks=new WeakHashMap<>();
    private static final Map<BuildingPlacement,Long> receivedTicks=new WeakHashMap<>();
    private static final EconomyViewIdentity<BuildingPlacement> views=new EconomyViewIdentity<>();
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static net.minecraft.world.level.Level observedLevel;
    private static Connection observedConnection;
    private PiglinEconomyClient(){}
    private static void observeContext() {
        var m=Minecraft.getInstance();
        var connection=m.getConnection()==null?null:m.getConnection().getConnection();
        if(CLOCK.context(m.level,connection)) {
            clearView();observedLevel=m.level;observedConnection=connection;
        }
        views.retain(PiglinEconomyClient::own);
    }
    private static boolean own(BuildingPlacement p) {
        var m=Minecraft.getInstance();
        if(m.player==null||m.level!=p.level||!p.isBuilt
                ||!p.ownerName.equals(m.player.getName().getString())
                ||!BuildingClientEvents.getBuildings().contains(p))return false;
        var player=PlayerClientEvents.getRTSPlayer(p.ownerName);
        return player!=null&&EconomyFactionAccess.permits(player.faction,Factions.PIGLINS);
    }
    public static void accept(PiglinEconomyStatusPacket s,Connection c) {
        observeContext();var m=Minecraft.getInstance();if(m.player==null||c==null||c!=observedConnection
            ||!s.owner().equals(m.player.getName().getString())||s.order()<0||s.request()<0||s.capture()<0
            ||s.boost()<0||s.boost()>400||s.cooldown()<0||s.cooldown()>1200||s.exchangeCooldown()<0||s.exchangeCooldown()>20)return;
        // ACK retirement is independent of the current dimension, placement and view token.
        if(!EconomyActionClient.accept(s.stream(),s.order(),s.session(),s.epoch(),s.request()))return;
        if(m.level==null||!s.dimension().equals(m.level.dimension().location()))return;
        BuildingPlacement p=BuildingClientEvents.getBuildings().stream().filter(b->b.getBuilding()==Buildings.PIGLIN_ORE_MINE&&b.level==m.level&&b.originPos.equals(s.origin())&&b.ownerName.equals(s.owner())).findFirst().orElse(null);
        if(p==null||!own(p)||!views.matches(p,s.view()))return;
        states.put(p,s);receivedTicks.put(p,CLOCK.now());if(!s.status().equals("ready"))m.player.displayClientMessage(Component.translatable("research.economy."+s.status()),true);
    }
    private static void query(BuildingPlacement p) {
        var m=Minecraft.getInstance();if(!own(p)||observedConnection==null)return;
        long now=CLOCK.now();Long last=queryTicks.get(p);if(last!=null&&!CLOCK.queryDue(last,20))return;
        queryTicks.put(p,now);var z=ResearchEconomyServerboundPacket.ZERO;
        PacketHandler.INSTANCE.sendToServer(new PiglinEconomyServerboundPacket(PiglinEconomyServerboundPacket.Action.QUERY,p.level.dimension().location(),p.originPos,z,0,z,z,0,views.token(p)));
    }
    private static boolean ready(BuildingPlacement p) {
        var s=states.get(p);Long received=receivedTicks.get(p);
        return own(p)&&s!=null&&views.matches(p,s.view())&&received!=null&&CLOCK.fresh(received,60)
                &&ExtendedResourcesClient.ready()&&s.epoch().equals(ExtendedResourcesClient.epoch())
                &&EconomyActionClient.ready(s.epoch());
    }
    private static boolean canPay(boolean exchange) {
        var resources=ResourcesClientEvents.getOwnResources();
        return resources!=null&&resources.food>=(exchange?PiglinEconomyService.EXCHANGE_FOOD:20)
                &&(!exchange||(resources.ore>=0&&resources.ore<=Integer.MAX_VALUE-PiglinEconomyService.EXCHANGE_ORE));
    }
    private static void submit(BuildingPlacement p,PiglinEconomyServerboundPacket.Action action) {
        if(!ready(p)){query(p);return;}var s=states.get(p);
        var payload=new PiglinEconomyServerboundPacket.Payload(action,s.dimension(),s.origin(),s.instance(),s.capture(),s.view());
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("piglin_mine",payload),(session,n)->new PiglinEconomyServerboundPacket(action,s.dimension(),s.origin(),s.instance(),s.capture(),s.epoch(),session,n,s.view()));
    }
    public static List<AbilityButton> append(BuildingPlacement p,List<AbilityButton> original) {
        observeContext();if(p.getBuilding()!=Buildings.PIGLIN_ORE_MINE||!own(p))return original;
        query(p);List<AbilityButton> out=new ArrayList<>(original);
        out.add(button(p,false));out.add(button(p,true));return out;
    }
    private static AbilityButton button(BuildingPlacement p,boolean exchange) {
        String key=exchange?"piglin_exchange":"piglin_overload";var s=states.get(p);
        var name=Component.translatable("research.economy."+key);
        List<net.minecraft.util.FormattedCharSequence> lines=new ArrayList<>();lines.add(name.getVisualOrderText());
        lines.add(Component.translatable("research.economy."+key+".description").getVisualOrderText());
        if(!ready(p))lines.add(Component.translatable("research.economy.waiting_server").getVisualOrderText());
        else if(exchange&&!s.researched())lines.add(Component.translatable("research.economy.research_required").getVisualOrderText());
        else {
            lines.add(Component.translatable(exchange?"research.economy.exchange_state":"research.economy.overload_state",
                    exchange?s.exchangeCooldown():s.boost(),s.cooldown()).getVisualOrderText());
            if(!exchange&&!s.active())lines.add(Component.translatable("research.economy.production_paused").getVisualOrderText());
            if(!canPay(exchange))lines.add(Component.translatable("research.economy.cannot_pay_action").getVisualOrderText());
        }
        var b=new AbilityButton(name.getString(),ResourceLocation.fromNamespaceAndPath("minecraft",exchange?"textures/item/iron_ingot.png":"textures/item/blaze_powder.png"),null,()->false,()->false,
            ()->{var v=states.get(p);return ready(p)&&canPay(exchange)&&v!=null&&(exchange?v.researched()&&v.exchangeCooldown()==0:v.active()&&v.cooldown()==0&&v.boost()==0);},
            ()->submit(p,exchange?PiglinEconomyServerboundPacket.Action.EXCHANGE:PiglinEconomyServerboundPacket.Action.OVERLOAD),null,lines,null,p);
        b.bottomLeftText=()->{var v=states.get(p);if(v==null||!ready(p))return "?";int t=exchange?v.exchangeCooldown():v.cooldown();return t>0?Integer.toString((t+19)/20):"";};return b;
    }
    private static void clearView(){states.clear();queryTicks.clear();receivedTicks.clear();views.clear();}
    public static void clear(){clearView();CLOCK.clear();observedLevel=null;observedConnection=null;}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        observeContext();
        if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();
        // Retire captured, removed and old-world objects, including unselected mines.
        states.keySet().removeIf(p->!own(p));
        queryTicks.keySet().removeIf(p->!own(p));
        receivedTicks.keySet().removeIf(p->!own(p));
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
