package com.dreamseekers.neutralcontract;

import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.dreamseekers.unit.FireflyWorker;
import com.dreamseekers.client.TargetModeButtons;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Target mode uses the ordinary real-world cursor; no editor marker or free-spawn route. */
@Mod.EventBusSubscriber(modid="dreamseekers",value=Dist.CLIENT)
public final class ContractClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static final Map<FireflyWorker,View> VIEWS=new WeakHashMap<>();
    private static Level level;private static Connection connection;private static UUID armed;private static int swallow=-1,swallowKey=-1;
    private static final class View {final UUID nonce=UUID.randomUUID();final String owner;long queried=Long.MIN_VALUE,received;ContractPackets.Snapshot state;View(String owner){this.owner=owner;}}
    private ContractClient(){}
    private static void clearView(){VIEWS.clear();armed=null;swallow=-1;swallowKey=-1;}
    private static void clear(){level=null;connection=null;CLOCK.clear();clearView();}
    private static void context(){var mc=Minecraft.getInstance();var c=mc.getConnection()==null?null:mc.getConnection().getConnection();if(CLOCK.context(mc.level,c))clearView();level=mc.level;connection=c;}
    private static boolean own(FireflyWorker w){var mc=Minecraft.getInstance();return w!=null&&mc.player!=null&&w.level()==mc.level&&w.isAlive()&&!w.isRemoved()&&w.getOwnerName().equals(mc.player.getGameProfile().getName());}
    public static void query(FireflyWorker w){context();if(!own(w)||connection==null)return;var v=VIEWS.get(w);if(v==null||!v.owner.equals(w.getOwnerName())){v=new View(w.getOwnerName());VIEWS.put(w,v);}
        if(!CLOCK.queryDue(v.queried,20))return;v.queried=CLOCK.now();
        ContractPackets.CHANNEL.sendToServer(new ContractPackets.Request(new ContractPackets.Payload(ContractPackets.Action.QUERY,level.dimension().location(),w.getId(),w.getUUID(),0,v.nonce,-1,ContractPackets.ZERO),ContractPackets.ZERO,ContractPackets.ZERO,0));}
    private static ContractPackets.Snapshot current(FireflyWorker w){context();var v=VIEWS.get(w);return own(w)&&v!=null&&v.owner.equals(w.getOwnerName())&&v.state!=null&&CLOCK.fresh(v.received,60)
        &&ExtendedResourcesClient.ready()&&ExtendedResourcesClient.epoch().equals(v.state.epoch())?v.state:null;}
    public static boolean available(FireflyWorker w){query(w);var s=current(w);return s!=null&&s.available()&&EconomyActionClient.ready(s.epoch());}
    public static boolean active(FireflyWorker w){var s=current(w);return s!=null&&s.active();}
    public static boolean armed(FireflyWorker w){return w.getUUID().equals(armed);}
    public static String label(FireflyWorker w){query(w);var s=current(w);return s==null?"?":s.active()?(s.remaining()<0?"→":((s.remaining()+19)/20)+"s"):s.used()+"/"+s.capacity();}
    public static void arm(FireflyWorker w){if(!available(w))return;var mc=Minecraft.getInstance();if(!(mc.screen instanceof TopdownGui)||!OrthoviewClientEvents.isEnabled())return;
        UUID next=TargetModeButtons.toggle(armed,w.getUUID());
        if(next==null){armed=null;return;}if(active(w)){cancel(w);return;}
        CursorClientEvents.setLeftClickAction(null);CursorClientEvents.setLeftClickSandboxAction(null);armed=next;message("select");}
    private static void send(FireflyWorker w,ContractPackets.Action action,LivingEntity target){var s=current(w);if(s==null||!EconomyActionClient.ready(s.epoch()))return;
        var payload=new ContractPackets.Payload(action,s.dimension(),s.id(),s.uuid(),s.ownerRevision(),s.view(),target==null?-1:target.getId(),target==null?ContractPackets.ZERO:target.getUUID());
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("neutral_contract",payload),(session,sequence)->new ContractPackets.Request(payload,s.epoch(),session,sequence),ContractPackets.CHANNEL::sendToServer);}
    public static void cancel(FireflyWorker w){armed=null;if(active(w))send(w,ContractPackets.Action.CANCEL,null);}
    public static void accept(ContractPackets.Snapshot packet,Connection from){context();var mc=Minecraft.getInstance();if(connection==null||connection!=from||mc.player==null||!packet.valid()||packet.request()<0||!packet.owner().equals(mc.player.getGameProfile().getName()))return;
        // Shared completions survive a closed/replaced view or dimension transition.
        if(!EconomyActionClient.accept(packet.stream(),packet.order(),packet.session(),packet.epoch(),packet.request()))return;
        if(level==null||!packet.dimension().equals(level.dimension().location()))return;
        var w=VIEWS.keySet().stream().filter(x->own(x)&&x.getId()==packet.id()&&x.getUUID().equals(packet.uuid())&&VIEWS.get(x).nonce.equals(packet.view())).findFirst().orElse(null);
        if(w==null)return;
        var v=VIEWS.get(w);v.state=packet;v.received=CLOCK.now();if(!packet.status().equals("ready"))message(packet.status());}
    private static FireflyWorker caster(){return UnitClientEvents.getSelectedUnits().stream().filter(e->e instanceof FireflyWorker&&e.getUUID().equals(armed)).map(e->(FireflyWorker)e).filter(ContractClient::own).findFirst().orElse(null);}
    private static void message(String key){var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.translatable("neutral_contract."+key),true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(ScreenEvent.MouseButtonPressed.Pre e){if(armed==null||!(e.getScreen() instanceof TopdownGui))return;
        if(e.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){armed=null;swallow=e.getButton();e.setCanceled(true);return;}
        if(e.getButton()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return;
        CursorClientEvents.preparePress(e);
        if(HudClientEvents.isMouseOverAnyButtonOrHud()){
            // Post dispatch owns the same-button toggle. Other controls cancel this mode first.
            armed=TargetModeButtons.beforeHudClick(armed,
                    HudClientEvents.getMousedOverButton() instanceof com.solegendary.reignofnether.hud.buttons.AbilityButton button
                            && button.ability==ContractAbility.INSTANCE && button.isEnabled.get());
            return;
        }
        var worker=caster();armed=null;swallow=e.getButton();e.setCanceled(true);if(worker==null)return;
        LivingEntity target=UnitClientEvents.getPreselectedUnits().stream().filter(x->NeutralContracts.nativeKind(x)!=null&&FogOfWarClientEvents.isInBrightChunk(x)).findFirst().orElse(null);
        if(target==null){message("invalid_target");return;}send(worker,ContractPackets.Action.START,target);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void release(ScreenEvent.MouseButtonReleased.Pre e){if(e.getButton()==swallow){swallow=-1;e.setCanceled(true);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(ScreenEvent.KeyPressed.Pre e){if(armed==null)return;if(e.getKeyCode()==GLFW.GLFW_KEY_ESCAPE){armed=null;swallowKey=e.getKeyCode();e.setCanceled(true);}
        else if(e.getKeyCode()!=Keybindings.abilitySlot2.getKey()&&e.getKeyCode()!=GLFW.GLFW_KEY_LEFT_SHIFT&&e.getKeyCode()!=GLFW.GLFW_KEY_RIGHT_SHIFT)armed=null;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void keyRelease(ScreenEvent.KeyReleased.Pre e){if(e.getKeyCode()==swallowKey){swallowKey=-1;e.setCanceled(true);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(CLOCK.endTick(Minecraft.getInstance().isPaused()))clearView();for(var unit:UnitClientEvents.getSelectedUnits())if(unit instanceof FireflyWorker w)query(w);if(armed!=null&&caster()==null)armed=null;}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
}
