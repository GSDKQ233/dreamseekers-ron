package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

/** No ALL broadcast, alliance inference or arbitrary client-chosen owner. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class ExtendedResourcesSync {
    private static UUID session = UUID.randomUUID();
    private static long sequence;
    private ExtendedResourcesSync() {}
    public static Resources account(String owner) {
        Resources found = null;
        for (Resources r : ResourcesServerEvents.resourcesList) if (r.ownerName.equals(owner)) {
            if (found != null) return null;
            found = r;
        }
        return found;
    }
    public static void send(ServerPlayer player) {
        if (sequence == Long.MAX_VALUE) return; // fail closed rather than wrap ordering
        String owner = player.getName().getString();
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new ExtendedResourcesClientboundPacket(session, ++sequence, owner, account(owner)));
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) { session=UUID.randomUUID(); sequence=0; }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0)
            event.getServer().getPlayerList().getPlayers().forEach(ExtendedResourcesSync::send);
    }
}
