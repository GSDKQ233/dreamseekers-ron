package com.dreamseekers.progression;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import java.util.UUID;
import net.minecraftforge.event.TickEvent;
@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT)
public final class DreamWindClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
 private static Connection connection;private static UUID stream,epoch;private static long order=-1,queryTick=Long.MIN_VALUE/2;
 private static String owner="";private static DreamWindProgression.State state=DreamWindProgression.INVALID;
 private DreamWindClient(){}
 private static void context(){var mc=Minecraft.getInstance();var live=mc.getConnection()==null?null:mc.getConnection().getConnection();if(live!=connection){stream=null;order=-1;}if(CLOCK.context(mc.level,live))clearView();connection=live;}
 private static boolean current(){context();var mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&connection!=null;}
 public static void query(){if(!current())return;if(!CLOCK.queryDue(queryTick,20))return;queryTick=CLOCK.now();DreamWindPackets.CHANNEL.sendToServer(new DreamWindPackets.Query());}
 public static DreamWindProgression.State state(String requestedOwner){query();var mc=Minecraft.getInstance();return current()&&mc.player.getName().getString().equals(requestedOwner)&&owner.equals(requestedOwner)&&ExtendedResourcesClient.ready()&&epoch!=null&&epoch.equals(ExtendedResourcesClient.epoch())?state:DreamWindProgression.INVALID;}
 private static boolean mask(int m){return m>=0&&m<=31&&(m&(m+1))==0;}
 public static void accept(DreamWindPackets.Snapshot p,Connection incoming){
  if(!current()||incoming!=connection||p.order()<0||p.order()<=order||(stream!=null&&!stream.equals(p.stream()))||!Minecraft.getInstance().player.getName().getString().equals(p.owner())||!mask(p.cost())||!mask(p.train())||!mask(p.research())||(!p.wind()&&(p.cost()!=0||p.train()!=0||p.research()!=0)))return;
  if(ExtendedResourcesClient.ready()&&!p.epoch().equals(ExtendedResourcesClient.epoch()))return;
  stream=p.stream();order=p.order();epoch=p.epoch();owner=p.owner();state=new DreamWindProgression.State(p.supported(),p.wind(),p.cost(),p.train(),p.research());
  for(var b:BuildingClientEvents.getBuildings())if(b instanceof com.dreamseekers.building.DreamLaboratoryPlacement&&b.getLevel()==Minecraft.getInstance().level&&b.ownerName.equals(owner))b.updateButtons();
 }
 // A level change or clock rollover clears presentation, not the connection-wide replay watermark.
 private static void clearView(){epoch=null;queryTick=Long.MIN_VALUE/2;owner="";state=DreamWindProgression.INVALID;}
 public static void clear(){connection=null;stream=null;order=-1;CLOCK.clear();clearView();}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();}
 @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
