package com.dreamseekers.ecologyworkposts;
import com.solegendary.reignofnether.client.ActiveClientTickClock;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Private short-lived server views. A stale view can never identify a replacement building. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID,value=Dist.CLIENT)
public final class EcologyClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static final Map<BuildingPlacement,View> VIEWS=new WeakHashMap<>();
    private static Level level;private static Connection connection;
    private static final class View {
        final UUID nonce=UUID.randomUUID();final String owner;long queried=Long.MIN_VALUE,received;
        EcologyPackets.Snapshot snapshot;
        View(String owner){this.owner=owner;}
    }
    private EcologyClient(){}
    public static void registerHooks(){
        com.solegendary.reignofnether.api.BuildingUnitOrderHooks.registerRightClick(EcologyClient::tryAssign);
        com.solegendary.reignofnether.api.BuildingUnitOrderHooks.registerButtons(EcologyClient::append);
    }
    @Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(EcologyClient::registerHooks);}
    }
    private static void clearView(){VIEWS.clear();}
    public static void clear(){level=null;connection=null;CLOCK.clear();clearView();}
    private static void context(){var mc=Minecraft.getInstance();Connection c=mc.getConnection()==null?null:mc.getConnection().getConnection();
        if(CLOCK.context(mc.level,c))clearView();level=mc.level;connection=c;
        VIEWS.entrySet().removeIf(e->!own(e.getKey())||!e.getValue().owner.equals(e.getKey().ownerName));
    }
    private static boolean own(BuildingPlacement p){var mc=Minecraft.getInstance();return p!=null&&mc.player!=null&&mc.level!=null&&p.level==mc.level
        &&EcologyWorkPosts.isCourt(p)&&p.isBuilt&&p.ownerName.equals(mc.player.getName().getString())
        &&PlayerClientEvents.getRTSPlayer(p.ownerName)!=null&&BuildingClientEvents.getBuildings().contains(p);}
    private static void query(BuildingPlacement p){if(!own(p)||connection==null)return;View v=VIEWS.computeIfAbsent(p,b->new View(b.ownerName));
        if(CLOCK.queryDue(v.queried,20)){v.queried=CLOCK.now();
            EcologyPackets.CHANNEL.sendToServer(new EcologyPackets.Request(new EcologyPackets.Payload(EcologyPackets.Action.QUERY,level.dimension().location(),p.originPos,v.nonce,
                EcologyPackets.ZERO,0,EcologyPackets.ZERO,List.of()),EcologyPackets.ZERO,EcologyPackets.ZERO,0));}}
    private static EcologyPackets.Snapshot state(BuildingPlacement p){View v=VIEWS.get(p);return own(p)&&v!=null&&v.snapshot!=null&&CLOCK.fresh(v.received,60)&&ExtendedResourcesClient.ready()&&ExtendedResourcesClient.epoch().equals(v.snapshot.epoch())?v.snapshot:null;}
    public static void accept(EcologyPackets.Snapshot packet,Connection source){context();var mc=Minecraft.getInstance();
        if(connection==null||source!=connection||mc.player==null||!packet.valid()||packet.request()<0||!mc.player.getName().getString().equals(packet.owner()))return;
        // Complete the authenticated shared stream even after the originating view closes.
        if(!EconomyActionClient.accept(packet.stream(),packet.order(),packet.session(),packet.epoch(),packet.request()))return;
        if(level==null||!level.dimension().location().equals(packet.dimension()))return;
        BuildingPlacement court=VIEWS.keySet().stream().filter(p->own(p)&&p.originPos.equals(packet.origin())&&VIEWS.get(p).nonce.equals(packet.view())).findFirst().orElse(null);
        if(court==null)return;
        View view=VIEWS.get(court);view.snapshot=packet;view.received=CLOCK.now();
        if(!packet.status().equals("ready"))com.dreamseekers.client.DreamTooltipText.hudMessage(Component.translatable("ecology_workposts."+packet.status()));
    }
    private static void send(BuildingPlacement p,EcologyPackets.Action action,UUID lease,List<EcologyPackets.Target> targets){var s=state(p);
        if(s==null||!EconomyActionClient.ready(s.epoch())){query(p);return;}
        var payload=new EcologyPackets.Payload(action,s.dimension(),s.origin(),s.view(),s.instance(),s.capture(),lease,targets);
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("ecology_workposts",payload),
            (session,sequence)->new EcologyPackets.Request(payload,s.epoch(),session,sequence),EcologyPackets.CHANNEL::sendToServer);
    }
    /** Call only in ordinary world right-click branch, before native move/garrison; shift queues stay native. */
    public static boolean tryAssign(BuildingPlacement p,List<LivingEntity> selected){context();if(!own(p)||selected.isEmpty()||Keybindings.shiftMod.isDown()
        ||com.solegendary.reignofnether.cursor.CursorClientEvents.getLeftClickAction()!=null)return false;
        var mc=Minecraft.getInstance();var targets=selected.stream().filter(e->e instanceof Unit unit&&e.isAlive()&&e.level()==level&&!unit.isFlyingUnit()
            &&unit.getOwnerName().equals(p.ownerName)).limit(64).map(e->new EcologyPackets.Target(e.getId(),e.getUUID())).toList();
        if(targets.isEmpty())return false;query(p);var s=state(p);
        if(s==null||!EconomyActionClient.ready(s.epoch())){com.dreamseekers.client.DreamTooltipText.hudMessage(Component.translatable("ecology_workposts.waiting"));return true;}
        send(p,EcologyPackets.Action.ASSIGN,EcologyPackets.ZERO,targets);return true;
    }
    /** Read-only codex bridge. This returns only the existing fresh, actual-owner view. */
    public static EcologyPackets.Snapshot codexView(BuildingPlacement court) {
        context();
        if(!own(court))return null;
        query(court);
        return state(court);
    }
    /** A codex row is not lease authority: resolve the current original ecology lease again. */
    public static boolean releaseOwn(BuildingPlacement court,UUID unitUuid) {
        var current=codexView(court);
        if(current==null||unitUuid==null||!EconomyActionClient.ready(current.epoch()))return false;
        var entity=current.occupants().stream().filter(entry->entry.uuid().equals(unitUuid)).findFirst().orElse(null);
        if(entity==null)return false;
        send(court,EcologyPackets.Action.RELEASE,entity.lease(),List.of());
        return true;
    }
    private static ResourceLocation icon(String name){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/"+name+".png");}
    public static List<AbilityButton> append(BuildingPlacement p,List<AbilityButton> original){context();if(!own(p))return original;query(p);var s=state(p);
        List<net.minecraft.util.FormattedCharSequence> tip=new ArrayList<>();tip.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("ecology_workposts.status")));
        if(s==null)tip.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("ecology_workposts.waiting")));else {
            long arrived=s.occupants().stream().filter(EcologyWorkPosts.Occupant::arrived).count();
            tip.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("ecology_workposts.summary",arrived,s.occupants().size()-arrived,EcologyWorkPosts.CAPACITY)));
            if(!s.researched())tip.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("ecology_workposts.requires_research")));
        }
        tip.addAll(com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("ecology_workposts.rules")));
        var out=new ArrayList<>(original);var info=new AbilityButton(Component.translatable("ecology_workposts.status").getString(),icon("golden_apple"),null,
            ()->false,()->false,()->true,()->{},null,tip,null,p);
        info.bottomLeftText=()->{var current=state(p);return current==null?"?":current.occupants().stream().filter(EcologyWorkPosts.Occupant::arrived).count()+"/"+EcologyWorkPosts.CAPACITY;};out.add(info);
        out.add(new AbilityButton(Component.translatable("ecology_workposts.release_all").getString(),icon("oak_door"),null,()->false,()->false,
            ()->{var current=state(p);return current!=null&&!current.occupants().isEmpty()&&EconomyActionClient.ready(current.epoch());},
            ()->send(p,EcologyPackets.Action.RELEASE_ALL,EcologyPackets.ZERO,List.of()),null,com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("ecology_workposts.release_all")),null,p));
        if(s!=null)for(var entry:s.occupants()) {
            var entity=level.getEntity(entry.id());String name=entity!=null&&entity.getUUID().equals(entry.uuid())?entity.getName().getString():"#"+entry.id();
            out.add(new AbilityButton(Component.translatable("ecology_workposts.release_one",name).getString(),icon("leather_boots"),null,()->false,()->false,
                ()->{var current=state(p);return current!=null&&current.occupants().stream().anyMatch(o->o.lease().equals(entry.lease()));},
                ()->send(p,EcologyPackets.Action.RELEASE,entry.lease(),List.of()),null,
                com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("ecology_workposts.release_one",name)),null,p));
        }
        return out;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();if(level==null)return;
        if(!UnitClientEvents.getSelectedUnits().isEmpty())query(BuildingClientEvents.getPreselectedBuilding());
        for(var court:BuildingClientEvents.getSelectedBuildings())query(court);
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
