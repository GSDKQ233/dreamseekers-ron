// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.dreamseekers.unit.TerrorbeakUnit;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
/** Source-object view and server quote; a stale body never addresses a replacement entity. */
@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT)
public final class TerrorbeakUpgradeClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static Level level;private static Connection connection;
    private static final Map<TerrorbeakUnit,View> VIEWS=new WeakHashMap<>();
    // Retirement may remove every world/selection reference before its acknowledgement arrives.
    private static final Map<UUID,PendingUpgradeView<TerrorbeakUnit>> PENDING_VIEWS=new HashMap<>();
    private static final class View {final UUID nonce=UUID.randomUUID();final String owner;long queried=Long.MIN_VALUE,received;TerrorbeakUpgradePackets.Snapshot state;View(String owner){this.owner=owner;}}
    private TerrorbeakUpgradeClient(){}
    private static void clearView(){PENDING_VIEWS.clear();VIEWS.clear();}
    private static void clear(){level=null;connection=null;CLOCK.clear();clearView();}
    private static void context(){var mc=Minecraft.getInstance();var live=mc.getConnection()==null?null:mc.getConnection().getConnection();if(CLOCK.context(mc.level,live))clearView();level=mc.level;connection=live;}
    private static boolean owned(TerrorbeakUnit source){var mc=Minecraft.getInstance();return source!=null&&mc.player!=null&&source.level()==mc.level&&source.isAlive()&&!source.isRemoved()&&source.getOwnerName().equals(mc.player.getGameProfile().getName());}
    public static void query(TerrorbeakUnit source){context();if(!owned(source)||connection==null)return;View view=VIEWS.get(source);if(view==null||!view.owner.equals(source.getOwnerName())){view=new View(source.getOwnerName());VIEWS.put(source,view);}
        if(!CLOCK.queryDue(view.queried,20))return;view.queried=CLOCK.now();
        var payload=new TerrorbeakUpgradePackets.Payload(true,level.dimension().location(),source.getId(),source.getUUID(),0,view.nonce);
        TerrorbeakUpgradePackets.CHANNEL.sendToServer(new TerrorbeakUpgradePackets.Request(payload,TerrorbeakUpgradePackets.ZERO,TerrorbeakUpgradePackets.ZERO,0));}
    private static TerrorbeakUpgradePackets.Snapshot current(TerrorbeakUnit source){context();View v=VIEWS.get(source);return owned(source)&&v!=null&&v.state!=null&&v.owner.equals(source.getOwnerName())&&CLOCK.fresh(v.received,60)&&ExtendedResourcesClient.ready()&&ExtendedResourcesClient.epoch().equals(v.state.epoch())?v.state:null;}
    public static boolean available(TerrorbeakUnit source){query(source);var s=current(source);return s!=null&&s.available()&&s.ownerRevision()>=0&&EconomyActionClient.ready(s.epoch());}
    public static void upgrade(TerrorbeakUnit source){if(!available(source))return;var s=current(source);
        if(PENDING_VIEWS.size()>=64&&!PENDING_VIEWS.containsKey(s.view()))return;
        var payload=new TerrorbeakUpgradePackets.Payload(false,s.dimension(),s.id(),s.uuid(),s.ownerRevision(),s.view());
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("terrorbeak_upgrade",payload),(session,sequence)->{PENDING_VIEWS.put(s.view(),new PendingUpgradeView<>(source,s.stream(),session,s.epoch(),sequence,payload.id(),payload.uuid()));return new TerrorbeakUpgradePackets.Request(payload,s.epoch(),session,sequence);},TerrorbeakUpgradePackets.CHANNEL::sendToServer);}
    public static void accept(TerrorbeakUpgradePackets.Snapshot packet,Connection incoming){context();var mc=Minecraft.getInstance();if(connection==null||connection!=incoming||mc.player==null||packet.order()<0||packet.request()<0||packet.ownerRevision()< -1||!packet.owner().equals(mc.player.getGameProfile().getName()))return;
        // Retirement, selection changes and dimension transitions cannot strand the shared request.
        boolean accepted=EconomyActionClient.accept(packet.stream(),packet.order(),packet.session(),packet.epoch(),packet.request());
        // A newer query may obsolete the UI snapshot after the shared ACK retires this completion.
        // Bind retirement to the submitted identity, not just a nonce reused in a later account epoch.
        var pending=PENDING_VIEWS.get(packet.view());
        if(pending!=null&&pending.completedBy(packet.stream(),packet.session(),packet.epoch(),packet.request(),packet.id(),packet.uuid()))PENDING_VIEWS.remove(packet.view(),pending);
        if(!accepted)return;
        if(level==null||!packet.dimension().equals(level.dimension().location()))return;
        TerrorbeakUnit source=VIEWS.keySet().stream().filter(s->s.getId()==packet.id()&&s.getUUID().equals(packet.uuid())&&VIEWS.get(s).nonce.equals(packet.view())&&VIEWS.get(s).owner.equals(packet.owner())).findFirst().orElse(null);
        if(source==null)return;
        View view=VIEWS.get(source);view.state=packet;view.received=CLOCK.now();
        if(!packet.status().equals("ready"))mc.player.displayClientMessage(Component.translatable("dreamseekers.terrorbeak_upgrade."+packet.status()),true);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
