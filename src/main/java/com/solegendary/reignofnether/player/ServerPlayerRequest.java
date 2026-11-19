package com.solegendary.reignofnether.player;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** A queued request is authority only for this exact live authenticated connection. */
public final class ServerPlayerRequest {
    private ServerPlayerRequest() { }
    public static boolean isCurrent(NetworkEvent.Context context) {
        return context != null && isCurrent(context.getSender(), context.getNetworkManager());
    }
    public static boolean isCurrent(ServerPlayer player, Connection connection) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()
                || player.isRemoved() || connection == null || !connection.isConnected()
                || player.connection == null || player.connection.connection != connection
                || connection.getPacketListener() != player.connection) return false;
        return player.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.getServer().getPlayerList().getPlayerByName(player.getGameProfile().getName()) == player;
    }
}
