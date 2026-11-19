package com.solegendary.reignofnether.building.garrison;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Authoritative, short-lived presentation only. No client goal is evidence of occupancy. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class TowerGarrisonClient {
    private static final int QUERY_INTERVAL = 20, MAX_STATE_AGE = 60;
    private static final UUID ZERO = new UUID(0, 0);
    private static final Map<BuildingPlacement, View> views = new WeakHashMap<>();
    private static final ActiveClientTickClock CLOCK = new ActiveClientTickClock();
    private static Level level;
    private static Connection connection;
    private static final class View {
        final UUID nonce = UUID.randomUUID();
        final String owner;
        long queryTick = Long.MIN_VALUE, receivedTick;
        TowerGarrisonStatusPacket state;
        View(String owner) { this.owner = owner; }
    }
    private TowerGarrisonClient() {}
    private static void clearView() { views.clear(); }
    public static void clear() { clearView(); CLOCK.clear(); level = null; connection = null; }
    private static void observeContext() {
        var mc = Minecraft.getInstance();
        var currentConnection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        if (CLOCK.context(mc.level, currentConnection)) {
            clearView(); level = mc.level; connection = currentConnection;
        }
        views.entrySet().removeIf(e -> !canManage(e.getKey()) || !e.getValue().owner.equals(e.getKey().ownerName));
    }
    public static boolean canManage(BuildingPlacement mine) {
        var mc = Minecraft.getInstance();
        return mine != null && mc.player != null && mc.level != null && mine.level == mc.level && TowerGarrisonService.managed(mine)
                && mine.isBuilt && AlliancesClient.isAlliedOrOwned(mc.player.getName().getString(), mine.ownerName)
                && PlayerClientEvents.getRTSPlayer(mc.player.getName().getString()) != null
                && PlayerClientEvents.getRTSPlayer(mine.ownerName) != null
                && BuildingClientEvents.getBuildings().contains(mine);
    }
    private static View query(BuildingPlacement mine) {
        if (!canManage(mine) || connection == null) return null;
        View view = views.computeIfAbsent(mine, p -> new View(p.ownerName));
        long now = CLOCK.now();
        if (CLOCK.queryDue(view.queryTick, QUERY_INTERVAL)) {
            view.queryTick = now;
            PacketHandler.INSTANCE.sendToServer(new TowerGarrisonServerboundPacket(TowerGarrisonServerboundPacket.Action.QUERY,
                    level.dimension().location(), mine.originPos, view.nonce, ZERO, 0, ZERO, ZERO, 0));
        }
        return view;
    }
    private static TowerGarrisonStatusPacket state(BuildingPlacement mine) {
        View view = views.get(mine);
        if (!canManage(mine) || view == null || view.state == null || !view.owner.equals(mine.ownerName)
                || level == null || !CLOCK.fresh(view.receivedTick, MAX_STATE_AGE)
                || !ExtendedResourcesClient.ready() || !view.state.epoch().equals(ExtendedResourcesClient.epoch())) return null;
        return view.state;
    }
    public static void accept(TowerGarrisonStatusPacket packet, Connection source) {
        observeContext(); var mc = Minecraft.getInstance();
        if (source == null || source != connection || mc.player == null || packet.order() < 0 || packet.request() < 0
                || !packet.validOccupancy()
                || !packet.viewer().equals(mc.player.getName().getString())) return;
        // A valid completion must retire pending work even after its UI view was replaced.
        if (!EconomyActionClient.accept(packet.stream(), packet.order(), packet.session(), packet.epoch(), packet.request())) return;
        if (level == null || !packet.dimension().equals(level.dimension().location())) return;
        BuildingPlacement mine = views.keySet().stream().filter(p -> canManage(p) && p.originPos.equals(packet.origin()) && p.ownerName.equals(packet.owner())
                && views.get(p).nonce.equals(packet.view())).findFirst().orElse(null);
        if (mine == null) return;
        View view = views.get(mine); view.state = packet; view.receivedTick = CLOCK.now();
        if (!packet.status().equals("ready")) mc.player.displayClientMessage(Component.translatable(
                packet.status().startsWith("tower_") ? "tower_garrison." + packet.status() : "research.economy." + packet.status()), true);
    }
    private static void releaseAll(BuildingPlacement mine) {
        var state = state(mine);
        if (state == null || !EconomyActionClient.ready(state.epoch())) { query(mine); return; }
        var action = TowerGarrisonServerboundPacket.Action.RELEASE_ALL;
        var payload = new TowerGarrisonServerboundPacket.Payload(action, state.dimension(), state.origin(), state.view(), state.instance(), state.capture());
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("tower_garrison", payload), (session, sequence) ->
                new TowerGarrisonServerboundPacket(action, state.dimension(), state.origin(), state.view(), state.instance(), state.capture(), state.epoch(), session, sequence));
    }
    public static List<AbilityButton> append(BuildingPlacement mine, List<AbilityButton> original) {
        observeContext(); if (!canManage(mine)) return original;
        query(mine); var state = state(mine);
        List<net.minecraft.util.FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("tower_garrison.status").getVisualOrderText());
        if (state == null) tooltip.add(Component.translatable("tower_garrison.waiting").getVisualOrderText());
        else {
            tooltip.add(Component.translatable("tower_garrison.summary", state.occupied(), state.capacity(), state.mine(), state.approaching()).getVisualOrderText());
        }
        tooltip.add(Component.translatable("tower_garrison.rules").getVisualOrderText());
        List<AbilityButton> out = new ArrayList<>(original);
        var status = new AbilityButton(Component.translatable("tower_garrison.status").getString(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/crossbow_standby.png"), null,
                () -> false, () -> false, () -> true, () -> {}, null, tooltip, null, mine);
        status.bottomLeftText = () -> { var current = state(mine); return current == null ? "?" : current.occupied() + "/" + current.capacity(); };
        out.add(status);
        out.add(new AbilityButton(Component.translatable("tower_garrison.release_all").getString(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/oak_door.png"), null,
                () -> false, () -> false, () -> { var current = state(mine); return current != null && (current.mine() > 0 || current.approaching() > 0) && EconomyActionClient.ready(current.epoch()); },
                () -> releaseAll(mine), null, List.of(Component.translatable("tower_garrison.release_all").getVisualOrderText(),
                Component.translatable("tower_garrison.release_all.description").getVisualOrderText()), null, mine));
        return out;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        observeContext();
        if (CLOCK.endTick(Minecraft.getInstance().isPaused())) clearView();
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
