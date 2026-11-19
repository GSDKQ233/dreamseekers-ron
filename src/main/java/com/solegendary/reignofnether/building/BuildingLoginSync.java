package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Session-bound login snapshots, always dispatched on the owning server thread. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class BuildingLoginSync {
    private static final int MAX_PENDING_PER_SERVER = 16_384;
    private static final Map<MinecraftServer, Map<UUID, Pending>> PENDING = new IdentityHashMap<>();
    private record Pending(ServerPlayer player, ServerGamePacketListenerImpl connection, int readyAt) { }
    private BuildingLoginSync() { }

    static void request(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || !PlayerServerEvents.rtsSyncingEnabled) return;
        Pending pending = new Pending(player, player.connection, server.getTickCount() + 20);
        if (!valid(server, pending)) return;
        if (server.isDedicatedServer()) {
            synchronize(pending);
            return;
        }
        Map<UUID, Pending> jobs = PENDING.computeIfAbsent(server, ignored -> new HashMap<>());
        if (jobs.size() < MAX_PENDING_PER_SERVER || jobs.containsKey(player.getUUID()))
            jobs.put(player.getUUID(), pending); // latest login replaces, never stacks
    }

    private static boolean valid(MinecraftServer server, Pending pending) {
        ServerPlayer player = pending.player();
        return PlayerServerEvents.rtsSyncingEnabled && player.getServer() == server
                && server.getPlayerList().getPlayer(player.getUUID()) == player
                && server.getPlayerList().getPlayers().stream().anyMatch(candidate -> candidate == player)
                && player.connection == pending.connection() && pending.connection() != null
                && pending.connection().connection.isConnected()
                && pending.connection().connection.getPacketListener() == pending.connection();
    }

    private static void synchronize(Pending pending) {
        // Keep existing packet audiences (including fog filtering and definition broadcasts).
        // A placement referring to a custom type must never precede its registration.
        CustomBuildingServerEvents.syncCustomBuildings(pending.player().getGameProfile().getName());
        BuildingServerEvents.placeBuildingsClientside(pending.player());
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || !server.isSameThread()) return;
        Map<UUID, Pending> jobs = PENDING.get(server);
        if (jobs == null) return;
        for (Pending pending : List.copyOf(jobs.values())) {
            UUID id = pending.player().getUUID();
            if (jobs.get(id) != pending) continue;
            if (!valid(server, pending)) { jobs.remove(id); continue; }
            if (server.getTickCount() - pending.readyAt() < 0) continue;
            jobs.remove(id); // consume before sending
            synchronize(pending);
        }
        if (jobs.isEmpty()) PENDING.remove(server);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) return;
        Map<UUID, Pending> jobs = PENDING.get(server);
        if (jobs == null) return;
        Pending pending = jobs.get(player.getUUID());
        if (pending != null && pending.player() == player) jobs.remove(player.getUUID());
        if (jobs.isEmpty()) PENDING.remove(server);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        PENDING.remove(event.getServer());
    }
}
