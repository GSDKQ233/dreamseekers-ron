package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.NonUnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UnitActionServerboundPacket {

    private final String ownerName; // player that is issuing this command
    private final UnitAction action;
    private final int unitId;
    private final int[] unitIds; // units to be controlled
    private final BlockPos preselectedBlockPos;
    private final BlockPos selectedBuildingPos; // for building abilities
    private final boolean shiftQueue; // shift queue actions

    // packet-handler functions
    public UnitActionServerboundPacket(
        String ownerName,
        UnitAction action,
        int unitId,
        int[] unitIds,
        BlockPos preselectedBlockPos,
        BlockPos selectedBuildingPos,
        boolean shiftQueue
    ) {
        this.ownerName = ownerName;
        this.action = action;
        this.unitId = unitId;
        this.unitIds = unitIds;
        this.preselectedBlockPos = preselectedBlockPos;
        this.selectedBuildingPos = selectedBuildingPos;
        this.shiftQueue = shiftQueue;
    }

    public UnitActionServerboundPacket(FriendlyByteBuf buffer) {
        this.ownerName = buffer.readUtf();
        this.action = buffer.readEnum(UnitAction.class);
        this.unitId = buffer.readInt();
        this.unitIds = buffer.readVarIntArray();
        this.preselectedBlockPos = buffer.readBlockPos();
        this.selectedBuildingPos = buffer.readBlockPos();
        this.shiftQueue = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.ownerName);
        buffer.writeEnum(this.action);
        buffer.writeInt(this.unitId);
        buffer.writeVarIntArray(this.unitIds);
        buffer.writeBlockPos(this.preselectedBlockPos);
        buffer.writeBlockPos(this.selectedBuildingPos);
        buffer.writeBoolean(this.shiftQueue);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("Sender for unit action packet was null");
                return;
            }
            String senderName = player.getName().getString();
            boolean senderSandbox = SandboxServer.isSandboxPlayer(senderName);
            if (!senderName.equals(ownerName) && !senderSandbox
                    && !AlliancesServerEvents.canControlAlly(senderName, ownerName)) {
                ReignOfNether.LOGGER.warn("UnitActionServerboundPacket: Tried to process packet from " + senderName + " for " + ownerName);
                return;
            }
            // The authoritative unit dispatcher only processes the Overworld. A
            // caller in another dimension cannot command matching global entity IDs.
            if (player.level().dimension() != Level.OVERWORLD) return;
            ServerLevel level = player.serverLevel();
            boolean fullControl = NonUnitServerEvents.canControlAllMobs(level, senderName);
            // Filter BEFORE enqueue: addActionItem also clears shifted commands.
            // Deferring authority until action() would let enemy IDs cancel queues.
            int[] authorizedIds = Arrays.stream(unitIds).distinct().filter(id -> {
                Entity entity = level.getEntity(id);
                if (entity == null || !entity.isAlive()) return false;
                if (entity instanceof Unit unit)
                    return senderSandbox || fullControl || senderName.equals(unit.getOwnerName())
                            || AlliancesServerEvents.canControlAlly(senderName, unit.getOwnerName());
                // Preserve the existing explicit wouldyoukindly non-RTS control path.
                return fullControl && entity instanceof PathfinderMob;
            }).toArray();
            if (this.action == UnitAction.DEBUG1) UnitServerEvents.debug1(this.preselectedBlockPos);
            if (this.action == UnitAction.DEBUG2) UnitServerEvents.debug2();
            // Existing building abilities legitimately carry no selected unit IDs.
            // Keep that route, but never use an unrelated building as authority.
            BuildingPlacement building = selectedBuildingPos.equals(BlockPos.ZERO) ? null
                    : BuildingUtils.findBuilding(level, selectedBuildingPos);
            boolean authorizedBuilding = building != null && building.getLevel() == level
                    && (senderSandbox || senderName.equals(building.ownerName)
                    || AlliancesServerEvents.canControlAlly(senderName, building.ownerName));
            if (authorizedIds.length == 0 && !authorizedBuilding) return;
            // A client claim is never the authority carried into later dispatch.
            UnitServerEvents.addActionItem(senderName, this.action, this.unitId, authorizedIds,
                    this.preselectedBlockPos, this.selectedBuildingPos, this.shiftQueue);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
