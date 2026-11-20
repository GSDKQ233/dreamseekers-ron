package com.solegendary.reignofnether.alliance;

import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class AllianceClientboundPacket {

    // pos is used to identify the building object serverside
    AllianceAction action;
    public String player1;
    public String player2;
    public boolean boolValue;

    public static void addAlliance(String playerName1, String playerName2) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new AllianceClientboundPacket(AllianceAction.ACCEPT_REQUEST, playerName1, playerName2, true));
    }

    static void sendTo(ServerPlayer target, AllianceAction action, String first, String second, boolean value) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target),
                new AllianceClientboundPacket(action, first, second, value));
    }

    private static void sendInvitation(AllianceAction action, String to, String from) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var recipient = server.getPlayerList().getPlayerByName(to);
        var sender = server.getPlayerList().getPlayerByName(from);
        if (recipient != null) sendTo(recipient, action, to, from, true);
        if (sender != null && sender != recipient) sendTo(sender, action, to, from, true);
    }

    public static void addPendingAlliance(String toPlayer, String fromPlayer) {
        sendInvitation(AllianceAction.REQUEST, toPlayer, fromPlayer);
    }

    public static void cancelPendingAlliance(String toPlayer, String fromPlayer) {
        sendInvitation(AllianceAction.CANCEL_REQUEST, toPlayer, fromPlayer);
    }

    public static void removeAlliance(String playerName1, String playerName2) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new AllianceClientboundPacket(AllianceAction.DISBAND, playerName1, playerName2, false));
    }

    public static void resetAlliances() {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new AllianceClientboundPacket(AllianceAction.DISBAND, "", "", true));
    }

    public static void setAllyControl(String playerName1, boolean setValue) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new AllianceClientboundPacket(AllianceAction.SET_ALLY_CONTROL, playerName1, "", setValue));
    }

    public AllianceClientboundPacket(
            AllianceAction action,
            String player1,
            String player2,
            boolean boolValue
    ) {
        this.action = action;
        this.player1 = player1;
        this.player2 = player2;
        this.boolValue = boolValue;
    }

    public AllianceClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(AllianceAction.class);
        this.player1 = buffer.readUtf();
        this.player2 = buffer.readUtf();
        this.boolValue = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.player1);
        buffer.writeUtf(this.player2);
        buffer.writeBoolean(this.boolValue);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        var connection = ctx.get().getNetworkManager();
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                var current = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (current == null || current.getConnection() != connection) return;
                switch (this.action) {

                    case REQUEST -> {
                        // receives a pending alliance request
                        AlliancesClient.addPendingAlliance(player1, player2);
                    }
                    case CANCEL_REQUEST -> {
                        // removes a pending alliance request
                        AlliancesClient.cancelPendingAlliance(player1, player2);
                    }
                    case ACCEPT_REQUEST -> {
                        AlliancesClient.addAlliance(player1, player2);
                    }
                    case DISBAND -> {
                        if (boolValue)
                            AlliancesClient.resetAllAlliances();
                        else
                            AlliancesClient.removeAlliance(player1, player2);
                    }
                    case SET_ALLY_CONTROL -> {
                        if (boolValue)
                            AlliancesClient.playersWithAlliedControl.add(player1);
                        else
                            AlliancesClient.playersWithAlliedControl.remove(player1);
                    }
                }
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
