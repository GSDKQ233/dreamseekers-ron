package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.UUID;

/** New balances are private to the local owner; old F/W/O protocol is unchanged. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class ExtendedResourcesClient {
    private static final ResourceSnapshotState STATE = new ResourceSnapshotState();
    private ExtendedResourcesClient() {}
    public static void accept(ExtendedResourcesClientboundPacket packet, net.minecraft.network.Connection connection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getConnection().getConnection() != connection
                || !mc.player.getName().getString().equals(packet.owner)) return;
        STATE.accept(packet);
    }
    public static int balance(ResourceLocation id) { return STATE.balance(id); }
    public static UUID epoch() { return STATE.epoch(); }
    public static long revision() { return STATE.revision(); }
    public static boolean ready() { return STATE.ready(); }
    public static void clear() { STATE.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }
}
