package com.dreamseekers.worldtree;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.dreamseekers.building.DreamBuildingSupport;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Only snapshots for the actual current placement and current local connection enable actions. */
@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT)
public final class WorldTreeClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private record Cache(WorldTreePackets.Snapshot value,long tick){}
    private static final Map<DreamAnchorPlacement,Cache> states=new WeakHashMap<>();
    private static final Map<DreamAnchorPlacement,Long> queries=new WeakHashMap<>();
    private static final Map<DreamAnchorPlacement,WorldTreeActionFeedback> feedback=new WeakHashMap<>();
    private static Connection liveConnection;
    private static void clearView(){states.clear();queries.clear();feedback.clear();}
    private static void clear(){liveConnection=null;CLOCK.clear();clearView();}
    private static void context(){var mc=Minecraft.getInstance();var connection=mc.getConnection()==null?null:mc.getConnection().getConnection();
        if(CLOCK.context(mc.level,connection))clearView();liveConnection=connection;}
    private static boolean current(){context();var mc=Minecraft.getInstance();return liveConnection!=null&&mc.level!=null&&mc.player!=null;}
    public static void accept(WorldTreePackets.Snapshot snapshot,Connection connection){
        context();var mc=Minecraft.getInstance();
        if(liveConnection==null||connection!=liveConnection||mc.player==null||snapshot.order()<0||snapshot.completed()<0||snapshot.remaining()<0||snapshot.remaining()>2400||snapshot.capture()<0||snapshot.model()==null||!mc.player.getName().getString().equals(snapshot.owner()))return;
        // The account ACK is independent of a currently visible placement/dimension.
        boolean newer=EconomyActionClient.accept(snapshot.stream(),snapshot.order(),snapshot.session(),snapshot.epoch(),snapshot.completed());
        if(!newer&&!EconomyActionClient.matchesContext(snapshot.session(),snapshot.epoch()))return;
        if(mc.level==null||!mc.level.dimension().location().equals(snapshot.dimension()))return;
        DreamAnchorPlacement p=null;for(var b:BuildingClientEvents.getBuildings())if(b instanceof DreamAnchorPlacement candidate&&candidate.getLevel()==mc.level&&b.originPos.equals(snapshot.origin())){p=candidate;break;}
        if(p==null||snapshot.model()!=p.model()||!snapshot.instance().equals(p.clientInstance())||!p.ownerName.equals(snapshot.owner()))return;
        // Presentation completion is separate from snapshot freshness: an exact own completion
        // may follow a newer QUERY, but can never replace its cached building state.
        var local=feedback.get(p);var cached=states.get(p);
        boolean currentIdentity=newer||cached==null||feedbackContext(cached.value()).equals(feedbackContext(snapshot));
        if(local!=null&&currentIdentity){String reason=local.accept(feedbackContext(snapshot),snapshot.completed(),snapshot.status(),CLOCK.now());
            if(reason!=null)mc.player.displayClientMessage(Component.translatable("worldtree.dreamseekers.action_failed",Component.translatable(reason)),true);}
        if(newer)states.put(p,new Cache(snapshot,CLOCK.now()));
    }
    public static void query(DreamAnchorPlacement p){if(!current())return;var mc=Minecraft.getInstance();if(p==null||p.getLevel()!=mc.level||!BuildingClientEvents.getBuildings().contains(p)||!p.ownerName.equals(mc.player.getName().getString()))return;
        if(!CLOCK.queryDue(queries.getOrDefault(p,Long.MIN_VALUE),20))return;queries.put(p,CLOCK.now());
        WorldTreePackets.CHANNEL.sendToServer(new WorldTreePackets.Request(WorldTreePackets.Action.QUERY,p.getLevel().dimension().location(),p.originPos,WorldTreePackets.ZERO,0,WorldTreePackets.ZERO,WorldTreePackets.ZERO,0));
    }
    private static WorldTreePackets.Snapshot ready(DreamAnchorPlacement p){if(!current())return null;var mc=Minecraft.getInstance();if(p==null||p.getLevel()!=mc.level||!BuildingClientEvents.getBuildings().contains(p))return null;var cached=states.get(p);if(cached==null)return null;var s=cached.value();
        if(!CLOCK.fresh(cached.tick(),60)||!s.instance().equals(p.clientInstance())||!p.ownerName.equals(mc.player.getName().getString())||!EconomyActionClient.ready(s.epoch()))return null;return s;}
    private static boolean dream(){var mc=Minecraft.getInstance();if(mc.player==null)return false;var owner=PlayerClientEvents.getRTSPlayer(mc.player.getName().getString());return com.dreamseekers.economy.DreamFactionAccess.allows(owner);}
    public static void act(DreamAnchorPlacement p,WorldTreePackets.Action action){var s=ready(p);if(s==null)return;
        record Fingerprint(WorldTreePackets.Action action,ResourceLocation dimension,net.minecraft.core.BlockPos origin,UUID instance,long capture){}
        var fingerprint=new Fingerprint(action,s.dimension(),s.origin(),s.instance(),s.capture());
        EconomyActionClient.submit(fingerprint,(session,sequence)->{
            feedback.computeIfAbsent(p,key->new WorldTreeActionFeedback()).submitted(
                new WorldTreeActionFeedback.Context(s.stream(),session,s.epoch(),s.instance(),s.capture()),sequence);
            return new WorldTreePackets.Request(action,s.dimension(),s.origin(),s.instance(),s.capture(),s.epoch(),session,sequence);
        },WorldTreePackets.CHANNEL::sendToServer);
    }
    private static WorldTreeActionFeedback.Context feedbackContext(WorldTreePackets.Snapshot s){
        return new WorldTreeActionFeedback.Context(s.stream(),s.session(),s.epoch(),s.instance(),s.capture());
    }
    private static void appendFeedback(List<net.minecraft.util.FormattedCharSequence> lines,DreamAnchorPlacement p,WorldTreePackets.Snapshot s){
        if(s==null)return;
        if(!s.reason().isBlank())lines.add(Component.translatable("worldtree.dreamseekers.blocked",Component.translatable(WorldTreeActionFeedback.reasonKey(s.reason()))).getVisualOrderText());
        var local=feedback.get(p);String reason=local==null?null:local.visible(feedbackContext(s),CLOCK.now());
        if(reason!=null)lines.add(Component.translatable("worldtree.dreamseekers.action_failed",Component.translatable(reason)).getVisualOrderText());
    }
    public static List<AbilityButton> buttons(DreamAnchorPlacement p){query(p);var result=new ArrayList<AbilityButton>();
        var s=ready(p);boolean pending=s!=null&&(s.phase()==WorldTreeState.Phase.RUNNING||s.phase()==WorldTreeState.Phase.BLOCKED||s.phase()==WorldTreeState.Phase.REFUND_PENDING);
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();lines.add(Component.translatable("worldtree.dreamseekers.upgrade").getVisualOrderText());lines.add(Component.translatable("worldtree.dreamseekers.cost").getVisualOrderText());
        lines.add(Component.translatable("worldtree.dreamseekers.income").getVisualOrderText());
        lines.add(Component.translatable("worldtree.dreamseekers.income_rules").getVisualOrderText());
        if(s!=null)lines.add(Component.translatable("worldtree.dreamseekers.progress",WorldTreeActionFeedback.progress(s.phase().name(),s.remaining()),2400,Component.translatable("worldtree.dreamseekers.phase."+s.phase().name().toLowerCase(Locale.ROOT))).getVisualOrderText());
        appendFeedback(lines,p,s);
        result.add(new AbilityButton("world_tree_upgrade",ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/dream_anchor_shadow.png"),Keybindings.abilitySlot4,()->false,()->p.model().branch!=WorldTreeModel.Branch.BASE,()->ready(p)!=null&&ready(p).phase()==WorldTreeState.Phase.IDLE&&dream(),()->act(p,WorldTreePackets.Action.START),null,lines,null,p));
        var windLines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        windLines.add(Component.translatable("worldtree.dreamseekers.wind.upgrade").getVisualOrderText());
        windLines.add(Component.translatable("worldtree.dreamseekers.wind.cost").getVisualOrderText());
        if(s!=null)windLines.add(Component.translatable("worldtree.dreamseekers.progress",WorldTreeActionFeedback.progress(s.phase().name(),s.remaining()),2400,Component.translatable("worldtree.dreamseekers.phase."+s.phase().name().toLowerCase(Locale.ROOT))).getVisualOrderText());
        appendFeedback(windLines,p,s);
        result.add(new AbilityButton("world_tree_wind",ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/dream_anchor_wind.png"),Keybindings.abilitySlot5,()->false,()->p.model().branch!=WorldTreeModel.Branch.BASE,()->ready(p)!=null&&ready(p).phase()==WorldTreeState.Phase.IDLE&&dream(),()->act(p,WorldTreePackets.Action.START_WIND),null,windLines,null,p));
        result.add(new AbilityButton("world_tree_cancel",ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/barrier.png"),Keybindings.abilitySlot6,()->false,()->!pending,()->ready(p)!=null&&pending,()->act(p,WorldTreePackets.Action.CANCEL),null,List.of(Component.translatable("worldtree.dreamseekers.cancel").getVisualOrderText()),null,p));
        return result;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
