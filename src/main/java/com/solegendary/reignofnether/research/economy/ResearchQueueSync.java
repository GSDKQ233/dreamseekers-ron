package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class ResearchQueueSync {
    private static UUID session=UUID.randomUUID();private static long sequence;
    private ResearchQueueSync() {}
    public static boolean isOptIn(ProductionPlacement p){return p.getBuilding() instanceof ProductionBuilding host
        &&host.productions.get().stream().anyMatch(i->i instanceof ReceiptBackedResearchItem);}
    public static long nextSequence(){return sequence==Long.MAX_VALUE?-1:++sequence;}
    public static UUID snapshotSession(){return session;}
    public static void send(ServerPlayer actor,ProductionPlacement p,String status) {
        var connection=EconomyActionGate.connection(actor);if(connection!=null)send(actor,connection,p,status,0);
    }
    public static void send(ServerPlayer actor,net.minecraft.network.Connection connection,ProductionPlacement p,String status,long requestSequence) {
        if(!isOptIn(p)||!ReceiptResearchService.owns(actor,p)||p.getCaptureRevision()<0||sequence==Long.MAX_VALUE) return;
        var actionToken=EconomyActionGate.query(actor,connection);if(actionToken==null)return;
        var r=ExtendedResourcesSync.account(p.ownerName);
        if(r==null||!r.extendedStateSupported())return;
        try {
            var packet=new ResearchQueueClientboundPacket(session,nextSequence(),status,p,r,actionToken,requestSequence);
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->actor),packet);
        } catch(IllegalArgumentException ignored) { /* Never truncate authoritative queues. */ }
        EconomyBalanceSync.send(actor);
    }
    public static void notifyOwner(MinecraftServer server,String owner,String status) {
        var player=server.getPlayerList().getPlayerByName(owner);
        if(player!=null) {
            EconomyBalanceSync.send(player); // also orphan refunds with no surviving queue/building
            HudClientboundPacket.showTempMessageI18n(owner,"research.economy."+status);
        }
    }
    @SubscribeEvent public static void start(ServerStartedEvent e){session=UUID.randomUUID();sequence=0;}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||e.getServer().getTickCount()%20!=0)return;
        ReceiptResearchService.retryDetachedRefunds(e.getServer());
        for(var building:BuildingServerEvents.getBuildings()) if(building instanceof ProductionPlacement p) {
            // Opt-in hosts only: old production buildings keep their existing snapshot behavior.
            if(!(p.getBuilding() instanceof ProductionBuilding host) || !host.productions.get().stream().anyMatch(i->i instanceof ReceiptBackedResearchItem)) continue;
            var actor=e.getServer().getPlayerList().getPlayerByName(p.ownerName);
            if(actor!=null)send(actor,p,"ready");
        }
    }
}
