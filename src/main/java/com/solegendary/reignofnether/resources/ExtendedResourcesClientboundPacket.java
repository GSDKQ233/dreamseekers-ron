package com.solegendary.reignofnether.resources;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Owner-only complete extension snapshot. Never carries receipts or conversion secrets. */
public final class ExtendedResourcesClientboundPacket {
    public static final int MAX_ENTRIES = 64;
    public final UUID session, epoch;
    public final long sequence, revision;
    public final String owner;
    public final Map<ResourceLocation, Integer> balances;
    public final boolean present;
    public ExtendedResourcesClientboundPacket(UUID session, long sequence, String owner, Resources account) {
        this.session = session; this.sequence = sequence; this.owner = owner; present = account != null && account.extendedStateSupported();
        epoch = present ? account.accountEpoch : new UUID(0, 0);
        revision = present ? account.revision : 0;
        Map<ResourceLocation,Integer> result = new LinkedHashMap<>();
        if (present) account.extra.forEach((id, value) -> {
            if (ResourceExtensionRegistry.get(id) != null && result.size() < MAX_ENTRIES) result.put(id, Math.max(0,value));
        });
        balances = Map.copyOf(result);
    }
    public ExtendedResourcesClientboundPacket(FriendlyByteBuf b) {
        session = b.readUUID(); sequence = b.readLong(); owner = b.readUtf(256); present = b.readBoolean();
        epoch = b.readUUID(); revision = b.readLong();
        int count = b.readVarInt();
        if (count < 0 || count > MAX_ENTRIES || sequence < 0 || revision < 0) throw new IllegalArgumentException("Invalid extension snapshot");
        Map<ResourceLocation,Integer> values = new LinkedHashMap<>();
        for (int i=0;i<count;i++) {
            ResourceLocation id = b.readResourceLocation(); int amount = b.readVarInt();
            if (amount < 0 || values.put(id, amount) != null) throw new IllegalArgumentException("Invalid extension balance");
        }
        balances = Map.copyOf(values);
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(session); b.writeLong(sequence); b.writeUtf(owner,256); b.writeBoolean(present);
        b.writeUUID(epoch); b.writeLong(revision); b.writeVarInt(balances.size());
        balances.forEach((id, amount) -> { b.writeResourceLocation(id); b.writeVarInt(amount); });
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        net.minecraft.network.Connection connection = ctx.get().getNetworkManager();
        ctx.get().enqueueWork(() -> ExtendedResourcesClient.accept(this, connection));
        ctx.get().setPacketHandled(true);
    }
}
