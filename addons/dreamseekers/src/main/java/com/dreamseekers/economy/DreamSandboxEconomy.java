package com.dreamseekers.economy;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Sandbox-only shadow-energy initialization and manual refill.
 * No normal account funding, client-supplied owner or automatic replenishment. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class DreamSandboxEconomy {
    public static final String INITIALIZED="dreamseekers:sandbox_shadow_v1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("sandbox_shadow"),()->"1","1"::equals,"1"::equals);
    private static final Map<net.minecraft.network.Connection,Long> requested=new WeakHashMap<>();
    private static boolean registered;
    private DreamSandboxEconomy() {}
    public static void bootstrap() {
        if(registered)return;registered=true;
        CHANNEL.messageBuilder(Refill.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->{}).decoder(b->new Refill()).consumerMainThread(Refill::handle).add();
    }
    /** Also initializes existing API45 sandbox saves, once per persisted resource account epoch. */
    public static boolean initialize(MinecraftServer server,String owner) {
        if(server==null||!server.isSameThread()||!SandboxServer.isSandboxPlayer(owner))return false;
        Resources r=ExtendedResourcesSync.account(owner);
        if(r==null||!r.extendedStateSupported()||!r.addonDataSupported()||r.addonData.contains(INITIALIZED))return false;
        var marker=new CompoundTag();marker.putInt("schema",1);
        var next=r.addonData.copy();next.put(INITIALIZED,marker);
        if(!ResourceAddonData.valid(next))return false;
        if(!refill(server,owner))return false;
        r.addonData.put(INITIALIZED,marker);
        return true;
    }
    /** Trusted server call; packet handler derives owner from the authenticated connection, never payload. */
    public static boolean refill(MinecraftServer server,String owner) {
        if(server==null||!server.isSameThread()||!SandboxServer.isSandboxPlayer(owner))return false;
        Resources r=ExtendedResourcesSync.account(owner);
        if(r==null||!r.extendedStateSupported()||!r.addonDataSupported()||!ResourceAddonData.valid(r.addonData))return false;
        int balance=r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0);
        if(balance<0)return false;
        var result=ResourceTransaction.creditIncome(server,r,r.accountEpoch,r.revision,
            new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,Math.max(0,DreamPassiveEconomy.SHADOW_CAP-balance))));
        if(!result.applied())return false;
        ServerPlayer player=server.getPlayerList().getPlayerByName(owner);
        if(player!=null)ExtendedResourcesSync.send(player);
        return true;
    }
    public record Refill() {
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            var context=supplier.get();var actor=context.getSender();var connection=context.getNetworkManager();
            context.enqueueWork(()->{
                if(actor==null||actor.getServer()==null||actor.getServer().getPlayerList().getPlayerByName(actor.getName().getString())!=actor)return;
                long now=actor.serverLevel().getGameTime();Long last=requested.get(connection);
                if(last!=null&&now>=last&&now-last<10)return;
                requested.put(connection,now);
                refill(actor.getServer(),actor.getName().getString());
            });context.setPacketHandled(true);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.getServer().getTickCount()%20!=0)return;
        for(ServerPlayer actor:event.getServer().getPlayerList().getPlayers())
            initialize(event.getServer(),actor.getName().getString());
    }
}
