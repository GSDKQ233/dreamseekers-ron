package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Deferred login/defeat world snapshots belong to the server tick and exact session. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class UnitLoginSync {
    private record Pending(ServerPlayer player, ServerGamePacketListenerImpl connection, int readyAt) { }
    private static final Map<MinecraftServer,Map<UUID,Pending>> PENDING=new IdentityHashMap<>();
    private record DefeatFog(ServerPlayer player, ServerGamePacketListenerImpl connection, ServerLevel level, int readyAt) { }
    private static final Map<MinecraftServer,Map<UUID,DefeatFog>> DEFEAT_FOG=new IdentityHashMap<>();
    private UnitLoginSync() { }
    static void request(ServerPlayer player) {
        MinecraftServer server=player.getServer();
        if(server==null||!server.isSameThread())return;
        Pending pending=new Pending(player,player.connection,server.getTickCount()+20);
        if(!valid(server,pending))return;
        if(server.isDedicatedServer()){PlayerServerEvents.syncUnits();return;}
        var jobs=PENDING.computeIfAbsent(server,s->new HashMap<>());
        if(jobs.size()<16_384||jobs.containsKey(player.getUUID()))jobs.put(player.getUUID(),pending);
    }
    private static boolean valid(MinecraftServer server,Pending p) {
        return PlayerServerEvents.rtsSyncingEnabled && p.player().getServer()==server && p.connection()!=null
                && p.player().connection==p.connection() && ServerPlayerRequest.isCurrent(p.player(),p.connection().connection);
    }
    static void requestDefeatFog(ServerPlayer player) {
        MinecraftServer server=player.getServer();
        if(server==null||player.connection==null||!ServerPlayerRequest.isCurrent(player,player.connection.connection))return;
        var jobs=DEFEAT_FOG.computeIfAbsent(server,s->new HashMap<>());
        if(jobs.size()<16_384||jobs.containsKey(player.getUUID()))
            jobs.put(player.getUUID(),new DefeatFog(player,player.connection,player.serverLevel(),server.getTickCount()+60));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        MinecraftServer server=event.getServer();if(event.phase!=TickEvent.Phase.END||!server.isSameThread())return;
        var jobs=PENDING.get(server);
        if(jobs!=null){
            for(Pending p:List.copyOf(jobs.values())) {
                UUID id=p.player().getUUID();if(jobs.get(id)!=p)continue;
                if(!valid(server,p)){jobs.remove(id);continue;}
                if(server.getTickCount()-p.readyAt()<0)continue;
                jobs.remove(id);PlayerServerEvents.syncUnits();
            }
            if(jobs.isEmpty())PENDING.remove(server);
        }
        var fog=DEFEAT_FOG.get(server);
        if(fog!=null){
            for(DefeatFog p:List.copyOf(fog.values())) {
                UUID id=p.player().getUUID();if(fog.get(id)!=p)continue;
                if(p.player().getServer()!=server||p.player().serverLevel()!=p.level()
                        ||p.player().connection!=p.connection()||p.connection()==null
                        ||!ServerPlayerRequest.isCurrent(p.player(),p.connection().connection)) {fog.remove(id);continue;}
                if(server.getTickCount()-p.readyAt()<0)continue;
                fog.remove(id); // consume before world access; a newer RTS admission must not receive the old spectator snapshot
                if(!PlayerServerEvents.isRTSPlayer(p.player().getGameProfile().getName()))
                    FogOfWarServerEvents.resendAllTrackedChunks(p.player());
            }
            if(fog.isEmpty())DEFEAT_FOG.remove(server);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getServer()==null)return;
        var jobs=PENDING.get(player.getServer());
        if(jobs!=null){Pending p=jobs.get(player.getUUID());if(p!=null&&p.player()==player)jobs.remove(player.getUUID());
            if(jobs.isEmpty())PENDING.remove(player.getServer());}
        var fog=DEFEAT_FOG.get(player.getServer());
        if(fog!=null){DefeatFog p=fog.get(player.getUUID());if(p!=null&&p.player()==player)fog.remove(player.getUUID());
            if(fog.isEmpty())DEFEAT_FOG.remove(player.getServer());}
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){PENDING.remove(event.getServer());DEFEAT_FOG.remove(event.getServer());}
}
