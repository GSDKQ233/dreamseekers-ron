package com.solegendary.reignofnether.building.workposts;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Requests carry only placement identity and replay/view tokens, never occupancy or income. */
public record MineWorkPostServerboundPacket(Action action, ResourceLocation dimension, BlockPos origin,
        UUID view, UUID instance, long capture, UUID epoch, UUID session, long sequence) {
    public enum Action { QUERY, RELEASE_ALL }
    public record Payload(Action action, ResourceLocation dimension, BlockPos origin, UUID view, UUID instance, long capture) {}
    public MineWorkPostServerboundPacket(FriendlyByteBuf b) {
        this(b.readEnum(Action.class), b.readResourceLocation(), b.readBlockPos(), b.readUUID(),
                b.readUUID(), b.readLong(), b.readUUID(), b.readUUID(), b.readLong());
    }
    public void encode(FriendlyByteBuf b) {
        b.writeEnum(action); b.writeResourceLocation(dimension); b.writeBlockPos(origin); b.writeUUID(view);
        b.writeUUID(instance); b.writeLong(capture); b.writeUUID(epoch); b.writeUUID(session); b.writeLong(sequence);
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var actor = context.get().getSender(); var connection = context.get().getNetworkManager();
        context.get().enqueueWork(() -> {
            if (actor == null || !actor.level().dimension().location().equals(dimension)) return;
            BuildingPlacement mine = BuildingServerEvents.getBuildings().stream()
                    .filter(p -> p.level == actor.level() && p.originPos.equals(origin)).findFirst().orElse(null);
            if (action == Action.QUERY) {
                if (MineWorkPostService.owns(actor, mine))
                    MineWorkPostStatusPacket.send(actor, connection, mine, view, "ready", 0);
                return;
            }
            var fingerprint = new EconomyActionGate.Fingerprint("mine_workposts",
                    new Payload(action, dimension, origin, view, instance, capture));
            String status = EconomyActionGate.execute(actor, connection, session, epoch, sequence, fingerprint,
                    account -> MineWorkPostService.release(actor, mine, instance, capture));
            if (MineWorkPostService.owns(actor, mine))
                MineWorkPostStatusPacket.send(actor, connection, mine, view, status, sequence);
            else EconomyActionResultPacket.send(actor, connection, status, sequence);
        });
        context.get().setPacketHandled(true);
    }
}
