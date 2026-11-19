package com.solegendary.reignofnether.sandbox;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SandboxServerboundPacket {
    static final int MAX_SELECTION = 8192;

    public SandboxAction sandboxAction;
    public String playerName;
    public String unitName;
    public BlockPos blockPos;
    public int[] entityIds;

    public static void spawnUnit(SandboxAction sandboxAction, String playerName, String unitName, BlockPos blockPos) {
        if (!unitName.isBlank())
            PacketHandler.INSTANCE.sendToServer(new SandboxServerboundPacket(sandboxAction, playerName, unitName, blockPos,  new int[]{}));
    }
    public static void setAnchor(BlockPos blockPos, int[] entityIds) {
        PacketHandler.INSTANCE.sendToServer(new SandboxServerboundPacket(SandboxAction.SET_ANCHOR, "", "", blockPos, entityIds));
    }
    public static void resetToAnchor(int[] entityIds) {
        PacketHandler.INSTANCE.sendToServer(new SandboxServerboundPacket(SandboxAction.RESET_TO_ANCHOR, "", "", new BlockPos(0,0,0), entityIds));
    }
    public static void removeAnchor(int[] entityIds) {
        PacketHandler.INSTANCE.sendToServer(new SandboxServerboundPacket(SandboxAction.REMOVE_ANCHOR, "", "", new BlockPos(0,0,0), entityIds));
    }
    public static void setUnitOwner(int[] entityIds, String ownerName) {
        PacketHandler.INSTANCE.sendToServer(new SandboxServerboundPacket(SandboxAction.SET_UNIT_OWNER, ownerName, "", new BlockPos(0,0,0), entityIds));
    }
    public static void setBuildingOwner(BlockPos pos, String ownerName) {
        PacketHandler.INSTANCE.sendToServer(new SandboxServerboundPacket(SandboxAction.SET_BUILDING_OWNER, ownerName, "", pos,  new int[]{}));
    }
    public static void removeBuilding(BlockPos pos) {
        PacketHandler.INSTANCE.sendToServer(new SandboxServerboundPacket(SandboxAction.REMOVE_BUILDING, "", "", pos, new int[]{}));
    }

    public SandboxServerboundPacket(SandboxAction sandboxAction, String playerName, String unitName, BlockPos blockPos, int[] entityIds) {
        this.sandboxAction = sandboxAction;
        this.playerName = playerName;
        this.unitName = unitName;
        this.blockPos = blockPos;
        this.entityIds = entityIds;
    }

    public SandboxServerboundPacket(FriendlyByteBuf buffer) {
        this.sandboxAction = buffer.readEnum(SandboxAction.class);
        this.playerName = buffer.readUtf();
        this.unitName = buffer.readUtf();
        this.blockPos = buffer.readBlockPos();
        this.entityIds = buffer.readVarIntArray(MAX_SELECTION);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.sandboxAction);
        buffer.writeUtf(this.playerName);
        buffer.writeUtf(this.unitName);
        buffer.writeBlockPos(this.blockPos);
        buffer.writeVarIntArray(this.entityIds);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> SandboxServer.edit(context.getSender(), context.getNetworkManager(),
                sandboxAction, playerName, unitName, blockPos, entityIds));
        context.setPacketHandled(true);
        return true; // handled by the network; not a promise that a gameplay edit was authorized
    }
}
