package com.solegendary.reignofnether.building.workposts;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.shared.MineWorkPosts;
import com.solegendary.reignofnether.building.data.OreMineTraitState;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
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

/** Authoritative, short-lived presentation only. No client goal is evidence of working or income. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class MineWorkPostClient {
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
        MineWorkPostStatusPacket state;
        View(String owner) { this.owner = owner; }
    }
    private MineWorkPostClient() {}
    private static void clearView() { views.clear(); }
    public static void clear() { clearView(); CLOCK.clear(); level = null; connection = null; }
    private static void observeContext() {
        var mc = Minecraft.getInstance();
        var currentConnection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        if (CLOCK.context(mc.level, currentConnection)) {
            clearView(); level = mc.level; connection = currentConnection;
        }
        views.entrySet().removeIf(e -> !own(e.getKey()) || !e.getValue().owner.equals(e.getKey().ownerName));
    }
    private static boolean own(BuildingPlacement mine) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mine.level == mc.level && MineWorkPosts.isMine(mine)
                && mine.isBuilt && mc.player.getName().getString().equals(mine.ownerName)
                && PlayerClientEvents.getRTSPlayer(mine.ownerName) != null
                && BuildingClientEvents.getBuildings().contains(mine);
    }
    private static View query(BuildingPlacement mine) {
        if (!own(mine) || connection == null) return null;
        View view = views.computeIfAbsent(mine, p -> new View(p.ownerName));
        long now = CLOCK.now();
        if (CLOCK.queryDue(view.queryTick, QUERY_INTERVAL)) {
            view.queryTick = now;
            PacketHandler.INSTANCE.sendToServer(new MineWorkPostServerboundPacket(MineWorkPostServerboundPacket.Action.QUERY,
                    level.dimension().location(), mine.originPos, view.nonce, ZERO, 0, ZERO, ZERO, 0));
        }
        return view;
    }
    private static MineWorkPostStatusPacket state(BuildingPlacement mine) {
        View view = views.get(mine);
        if (!own(mine) || view == null || view.state == null || !view.owner.equals(mine.ownerName)
                || level == null || !CLOCK.fresh(view.receivedTick, MAX_STATE_AGE)
                || !ExtendedResourcesClient.ready() || !view.state.epoch().equals(ExtendedResourcesClient.epoch())) return null;
        return view.state;
    }
    public static void accept(MineWorkPostStatusPacket packet, Connection source) {
        observeContext(); var mc = Minecraft.getInstance();
        if (source == null || source != connection || mc.player == null || packet.order() < 0 || packet.request() < 0
                || !packet.validOccupancy()
                || !packet.owner().equals(mc.player.getName().getString())) return;
        // A valid completion must retire pending work even after its UI view was replaced.
        if (!EconomyActionClient.accept(packet.stream(), packet.order(), packet.session(), packet.epoch(), packet.request())) return;
        if (level == null || !packet.dimension().equals(level.dimension().location())) return;
        BuildingPlacement mine = views.keySet().stream().filter(p -> own(p) && p.originPos.equals(packet.origin())
                && views.get(p).nonce.equals(packet.view())).findFirst().orElse(null);
        if (mine == null) return;
        View view = views.get(mine); view.state = packet; view.receivedTick = CLOCK.now();
        if (!packet.status().equals("ready")) mc.player.displayClientMessage(Component.translatable(
                packet.status().equals("mine_workers_released") ? "mine_workposts.released" : "research.economy." + packet.status()), true);
    }
    public static boolean hasAssignment(Unit unit) {
        observeContext();
        if (!(unit instanceof VillagerUnit worker) || !worker.isAlive() || worker.level() != level) return false;
        for (BuildingPlacement mine : views.keySet()) {
            var state = state(mine); if (state == null || !mine.ownerName.equals(unit.getOwnerName())) continue;
            for (var entry : state.workers()) if (entry.entityId() == worker.getId() && entry.uuid().equals(worker.getUUID())) return true;
        }
        return false;
    }
    private static void releaseAll(BuildingPlacement mine) {
        var state = state(mine);
        if (state == null || !EconomyActionClient.ready(state.epoch())) { query(mine); return; }
        var action = MineWorkPostServerboundPacket.Action.RELEASE_ALL;
        var payload = new MineWorkPostServerboundPacket.Payload(action, state.dimension(), state.origin(), state.view(), state.instance(), state.capture());
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("mine_workposts", payload), (session, sequence) ->
                new MineWorkPostServerboundPacket(action, state.dimension(), state.origin(), state.view(), state.instance(), state.capture(), state.epoch(), session, sequence));
    }
    public static List<AbilityButton> append(BuildingPlacement mine, List<AbilityButton> original) {
        observeContext(); if (!own(mine)) return original;
        query(mine); var state = state(mine);
        List<net.minecraft.util.FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("mine_workposts.status").getVisualOrderText());
        if (state == null) tooltip.add(Component.translatable("mine_workposts.waiting").getVisualOrderText());
        else {
            int active = state.activeCount();
            tooltip.add(Component.translatable("mine_workposts.summary", active, state.workers().size() - active, state.capacity()).getVisualOrderText());
            String multiplier = String.format(Locale.ROOT, "%.2f", OreMineTraitState.workerQuarterCredit(active) / 4.0);
            tooltip.add(Component.translatable("mine_workposts.multiplier", multiplier).getVisualOrderText());
            if (!state.producing()) tooltip.add(Component.translatable("mine_workposts.paused").getVisualOrderText());
        }
        tooltip.add(Component.translatable("mine_workposts.rules").getVisualOrderText());
        List<AbilityButton> out = new ArrayList<>(original);
        var status = new AbilityButton(Component.translatable("mine_workposts.status").getString(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_pickaxe.png"), null,
                () -> false, () -> false, () -> true, () -> {}, null, tooltip, null, mine);
        status.bottomLeftText = () -> { var current = state(mine); return current == null ? "?" : current.activeCount() + "/" + current.capacity(); };
        out.add(status);
        out.add(new AbilityButton(Component.translatable("mine_workposts.release_all").getString(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/oak_door.png"), null,
                () -> false, () -> false, () -> { var current = state(mine); return current != null && !current.workers().isEmpty() && EconomyActionClient.ready(current.epoch()); },
                () -> releaseAll(mine), null, List.of(Component.translatable("mine_workposts.release_all").getVisualOrderText(),
                Component.translatable("mine_workposts.release_all.description").getVisualOrderText()), null, mine));
        return out;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        observeContext(); var mc = Minecraft.getInstance();
        if (CLOCK.endTick(mc.isPaused())) clearView();
        if (mc.isPaused() || level == null || connection == null) return;
        // Selected workers need authoritative occupancy even after reconnect, when their local goals have no orders.
        if (UnitClientEvents.getSelectedUnits().stream().anyMatch(e -> e instanceof VillagerUnit))
            for (BuildingPlacement mine : BuildingClientEvents.getBuildings()) if (own(mine)) query(mine);
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
