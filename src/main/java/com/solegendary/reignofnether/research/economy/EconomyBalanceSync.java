package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** New-economy authoritative snapshots, never a repeated delta and never a broadcast. */
public final class EconomyBalanceSync {
    private EconomyBalanceSync() {}
    public static void send(ServerPlayer actor) {
        var server=actor.getServer();String owner=actor.getName().getString();
        if(server==null||!server.isSameThread()||server.getPlayerList().getPlayerByName(owner)!=actor
                ||PlayerServerEvents.getRTSPlayer(owner)==null)return;
        Resources account=ExtendedResourcesSync.account(owner);
        if(account==null||!account.extendedStateSupported())return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->actor),
            new ResourcesClientboundPacket(ResourcesAction.SYNC,owner,account.food,account.wood,
                account.ore,account.emerald,BlockPos.ZERO,""));
        ExtendedResourcesSync.send(actor);
    }
}
