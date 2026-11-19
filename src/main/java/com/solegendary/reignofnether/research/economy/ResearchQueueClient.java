package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
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
public final class ResearchQueueClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static UUID session;private static long sequence=-1;
    private static final Map<ProductionPlacement,ResearchQueueClientboundPacket> queues=new WeakHashMap<>();
    private static final Map<ProductionPlacement,Long> queried=new WeakHashMap<>();
    private ResearchQueueClient() {}
    private static void observeContext() {
        var mc=Minecraft.getInstance();
        var connection=mc.getConnection()==null?null:mc.getConnection().getConnection();
        if(CLOCK.context(mc.level,connection))clearView();
    }
    public static void accept(ResearchQueueClientboundPacket packet,Connection connection) {
        observeContext();var mc=Minecraft.getInstance();
        if(mc.player==null||connection==null||mc.getConnection()==null||mc.getConnection().getConnection()!=connection
            ||!mc.player.getName().getString().equals(packet.owner)||packet.sequence<0||packet.requestSequence<0||packet.revision<0||packet.capture<0
            ||packet.entries.size()>ResearchQueueClientboundPacket.MAX_QUEUE)return;
        Set<UUID> receipts=new HashSet<>();
        for(var entry:packet.entries) {
            var item=ReignOfNetherRegistries.PRODUCTION_ITEM.get(entry.item());
            if(item==null||!Float.isFinite(entry.ticks())||entry.ticks()<0
                ||((item instanceof ReceiptBackedResearchItem)!=(entry.receipt()!=null))
                ||(entry.receipt()!=null&&!receipts.add(entry.receipt())))return;
        }
        // Validate the authenticated envelope before ACK; transient UI filters follow it.
        if(!EconomyActionClient.accept(packet.session,packet.sequence,packet.actionSession,packet.epoch,packet.requestSequence))return;
        if(mc.level==null||!mc.level.dimension().location().equals(packet.dimension)
            ||packet.sequence<=sequence||(session!=null&&!session.equals(packet.session)))return;
        ProductionPlacement p=null;
        for(var b:BuildingClientEvents.getBuildings())if(b instanceof ProductionPlacement candidate
            &&candidate.originPos.equals(packet.origin)&&candidate.ownerName.equals(packet.owner)&&candidate.getLevel()==mc.level){p=candidate;break;}
        if(p==null)return;
        var previous=queues.get(p);
        if(previous!=null&&previous.epoch.equals(packet.epoch)&&packet.revision<previous.revision)return;
        List<ActiveProduction> replacement=new ArrayList<>();
        for(var entry:packet.entries) {
            var item=ReignOfNetherRegistries.PRODUCTION_ITEM.get(entry.item());
            var a=new ActiveProduction(item,packet.owner,entry.queueEntryId());a.ticksLeft=entry.ticks();
            if(entry.receipt()!=null)a.receiptResearch=new ReceiptResearchBinding(entry.receipt(),packet.owner,packet.epoch,packet.instance,packet.capture);
            replacement.add(a);
        }
        if(!replacement.isEmpty()&&packet.headPause.revision()!=0) {
            var head=replacement.get(0);
            head.acceptPause(packet.entries.get(0).queueEntryId(),packet.headPause,head.ticksLeft);
        }
        session=packet.session;sequence=packet.sequence;queues.put(p,packet);
        p.productionQueue.clear();p.productionQueue.addAll(replacement);
        if(!packet.status.equals("ready"))mc.player.displayClientMessage(Component.translatable("research.economy."+packet.status),true);
    }
    public static boolean ready(ProductionPlacement p) {
        observeContext();var mc=Minecraft.getInstance();
        return mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&p.getLevel()==mc.level
            &&mc.player.getName().getString().equals(p.ownerName)&&queues.containsKey(p)
            &&ExtendedResourcesClient.ready()&&queues.get(p).epoch.equals(ExtendedResourcesClient.epoch())
            &&EconomyActionClient.ready(ExtendedResourcesClient.epoch());
    }
    public static void query(ProductionPlacement p) {
        observeContext();var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getConnection()==null
            ||p.getLevel()!=mc.level||!mc.player.getName().getString().equals(p.ownerName))return;
        long now=CLOCK.now();Long last=queried.get(p);if(last!=null&&!CLOCK.queryDue(last,20))return;
        queried.put(p,now);
        var zero=ResearchEconomyServerboundPacket.ZERO;
        PacketHandler.INSTANCE.sendToServer(new ResearchEconomyServerboundPacket(ResearchEconomyServerboundPacket.Action.QUERY,
            p.getLevel().dimension().location(),p.originPos,ResourceLocation.fromNamespaceAndPath("reignofnether","query"),zero,zero,0,zero,zero,0));
    }
    public static void start(ProductionPlacement p,ReceiptBackedResearchItem item) {
        if(!ready(p)){query(p);return;}send(p,item.id(),ResearchEconomyServerboundPacket.ZERO,ResearchEconomyServerboundPacket.Action.START);
    }
    public static void cancel(ProductionPlacement p,ReceiptBackedResearchItem item,boolean first) {
        if(!ready(p)){query(p);return;}
        ActiveProduction a=first&&!p.productionQueue.isEmpty()?p.productionQueue.get(0):null;
        if(!first)for(var candidate:p.productionQueue)if(candidate.item==item){a=candidate;break;}
        if(a==null||a.item!=item||a.receiptResearch==null)return;
        send(p,item.id(),a.receiptResearch.receiptId(),ResearchEconomyServerboundPacket.Action.CANCEL);
    }
    private static void send(ProductionPlacement p,ResourceLocation item,UUID receipt,ResearchEconomyServerboundPacket.Action action) {
        var snapshot=queues.get(p);
        var payload=new ResearchEconomyServerboundPacket.Payload(action,p.getLevel().dimension().location(),p.originPos,item,receipt,snapshot.instance,snapshot.capture);
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("research",payload),(actionSession,n)->
            new ResearchEconomyServerboundPacket(action,payload.dimension(),payload.origin(),item,receipt,snapshot.instance,snapshot.capture,snapshot.epoch,actionSession,n));
    }
    public static void acceptResult(EconomyActionResultPacket packet,Connection connection) {
        observeContext();var mc=Minecraft.getInstance();
        if(mc.player==null||connection==null||mc.getConnection()==null||mc.getConnection().getConnection()!=connection
            ||!mc.player.getName().getString().equals(packet.owner())||packet.order()<0||packet.request()<0)return;
        if(EconomyActionClient.accept(packet.stream(),packet.order(),packet.session(),packet.epoch(),packet.request()))
            mc.player.displayClientMessage(Component.translatable("research.economy."+packet.status()),true);
    }
    private static void clearView(){session=null;sequence=-1;queues.clear();queried.clear();}
    public static void clear(){EconomyActionClient.clear();clearView();CLOCK.clear();}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        observeContext();var mc=Minecraft.getInstance();
        if(CLOCK.endTick(mc.isPaused()))clearView();
        if(mc.isPaused()||mc.level==null||mc.getConnection()==null)return;
        if(CLOCK.now()>0&&CLOCK.now()%40==0)
            EconomyActionClient.retryPending(); // identical nonce/payload only; never silently creates a new purchase
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
