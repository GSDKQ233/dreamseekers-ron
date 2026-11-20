package com.dreamseekers.encounter;

import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.client.TargetModeButtons;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.sandbox.SandboxMenuType;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT)
public final class EncounterClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static Level level;private static Connection connection;private static UUID view=UUID.randomUUID();
    private static EncounterPackets.Snapshot state;private static NeutralEncounters.Kind armed;private static long queried=Long.MIN_VALUE,received;
    private static int swallow=-1,swallowKey=-1;private static final Map<NeutralEncounters.Kind,UnitSpawnButton> BUTTONS=new EnumMap<>(NeutralEncounters.Kind.class);
    private static void clearView(){state=null;view=UUID.randomUUID();armed=null;queried=Long.MIN_VALUE;received=0;swallow=-1;swallowKey=-1;}
    private static void clear(){level=null;connection=null;CLOCK.clear();clearView();}
    private static void context(){var mc=Minecraft.getInstance();var c=mc.getConnection()==null?null:mc.getConnection().getConnection();if(CLOCK.context(mc.level,c))clearView();level=mc.level;connection=c;}
    private static boolean editor(){return Minecraft.getInstance().player!=null&&SandboxClientEvents.isSandboxPlayer()&&SandboxClientEvents.getFaction()==DreamseekersMod.DREAMSEEKERS&&SandboxClientEvents.sandboxMenuType==SandboxMenuType.UNITS&&UnitClientEvents.getSelectedUnits().isEmpty()&&BuildingClientEvents.getSelectedBuildings().isEmpty();}
    public static List<UnitSpawnButton> append(List<UnitSpawnButton> original){context();if(!editor())return original;query();var result=new ArrayList<>(original);
        for(var kind:NeutralEncounters.Kind.values()){var b=BUTTONS.computeIfAbsent(kind,k->{var button=new UnitSpawnButton("map_encounter_"+k.name().toLowerCase(Locale.ROOT),new ResourceLocation("dreamseekers","textures/icons/"+(k==NeutralEncounters.Kind.BEETLE?"skill_beetle_advance":"skill_snail_shell_formation")+".png"),List.of());
            button.onLeftClick=()->arm(k);button.onRightClick=()->armed=null;button.isSelected=()->armed==k;button.isEnabled=EncounterClient::ready;button.bottomLeftText=()->state==null?"?":!state.supported()?"!":Integer.toString(state.count());return button;});
            b.tooltipLines=com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("map_encounter."+kind.name().toLowerCase(Locale.ROOT)),Component.translatable("map_encounter.description"),(state!=null&&!state.supported()?Component.translatable("map_encounter.unsupported"):Component.translatable("map_encounter.limit",state==null?"?":state.count())),Component.translatable("map_encounter.controls"));result.add(b);}return result;}
    private static boolean ready(){return editor()&&state!=null&&state.supported()&&state.count()<EncounterLedger.LIMIT&&CLOCK.fresh(received,60)&&EconomyActionClient.ready(state.epoch());}
    private static void query(){if(!editor()||level==null||connection==null)return;if(!CLOCK.queryDue(queried,20))return;queried=CLOCK.now();
        EncounterPackets.CHANNEL.sendToServer(new EncounterPackets.Request(new EncounterPackets.Payload(true,level.dimension().location(),NeutralEncounters.Kind.BEETLE,BlockPos.ZERO,view),EncounterPackets.ZERO,EncounterPackets.ZERO,0));}
    private static void arm(NeutralEncounters.Kind kind){if(!ready()||!(Minecraft.getInstance().screen instanceof TopdownGui)||!OrthoviewClientEvents.isEnabled())return;
        var next=TargetModeButtons.toggle(armed,kind);
        if(next==null){armed=null;return;}BuildingClientEvents.setBuildingToPlace(null);CursorClientEvents.setLeftClickAction(null);CursorClientEvents.setLeftClickSandboxAction(null);armed=next;message("select");}
    private static void place(NeutralEncounters.Kind kind,BlockPos floor){if(!ready())return;var p=new EncounterPackets.Payload(false,level.dimension().location(),kind,floor.immutable(),view);
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("map_encounter",p),(session,sequence)->new EncounterPackets.Request(p,state.epoch(),session,sequence),EncounterPackets.CHANNEL::sendToServer);}
    public static void accept(EncounterPackets.Snapshot p,Connection from){context();var mc=Minecraft.getInstance();
        // Authenticated account ACKs belong to the shared economy stream, not this transient editor view.
        // Switching page/selection/dimension after send must not strand its global pending request.
        if(connection==null||connection!=from||mc.player==null||p.count()<0||p.count()>128||p.order()<0||p.request()<0||!p.owner().equals(mc.player.getGameProfile().getName()))return;
        if(!EconomyActionClient.accept(p.stream(),p.order(),p.session(),p.epoch(),p.request()))return;
        if(!editor()||level==null||!p.view().equals(view)||!p.dimension().equals(level.dimension().location()))return;
        state=p;received=CLOCK.now();if(!p.status().equals("ready"))message(p.status());}
    private static void message(String key){var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("map_encounter."+key),true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(ScreenEvent.MouseButtonPressed.Pre e){if(armed==null||!(e.getScreen() instanceof TopdownGui))return;
        if(e.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){armed=null;swallow=e.getButton();e.setCanceled(true);return;}
        if(e.getButton()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return;
        CursorClientEvents.preparePress(e);
        if(HudClientEvents.isMouseOverAnyButtonOrHud()){
            var button=HudClientEvents.getMousedOverButton();
            armed=TargetModeButtons.beforeHudClick(armed,button!=null&&button==BUTTONS.get(armed)&&button.isEnabled.get());
            return;
        }
        var kind=armed;armed=null;swallow=e.getButton();e.setCanceled(true);var floor=CursorClientEvents.getPreselectedBlockPos();if(floor!=null)place(kind,floor);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void release(ScreenEvent.MouseButtonReleased.Pre e){if(e.getButton()==swallow){swallow=-1;e.setCanceled(true);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(ScreenEvent.KeyPressed.Pre e){if(armed==null)return;if(e.getKeyCode()==GLFW.GLFW_KEY_ESCAPE){armed=null;swallowKey=e.getKeyCode();e.setCanceled(true);}else if(e.getKeyCode()!=GLFW.GLFW_KEY_LEFT_SHIFT&&e.getKeyCode()!=GLFW.GLFW_KEY_RIGHT_SHIFT)armed=null;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void keyRelease(ScreenEvent.KeyReleased.Pre e){if(e.getKeyCode()==swallowKey){swallowKey=-1;e.setCanceled(true);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();if(!editor()){armed=null;state=null;return;}if(!(Minecraft.getInstance().screen instanceof TopdownGui)||!OrthoviewClientEvents.isEnabled())armed=null;if(armed!=null)query();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
