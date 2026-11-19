package com.solegendary.reignofnether.resources;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.UUID;

/** Testable snapshot ordering, independent of the GUI and Minecraft client singleton. */
public final class ResourceSnapshotState {
    private UUID session, epoch;
    private long sequence = -1, revision;
    private Map<ResourceLocation,Integer> balances = Map.of();
    public boolean accept(ExtendedResourcesClientboundPacket packet) {
        if (packet.sequence < 0 || packet.revision < 0 || packet.sequence <= sequence
                || (session != null && !session.equals(packet.session))) return false;
        if (packet.present && epoch != null && epoch.equals(packet.epoch) && packet.revision < revision) return false;
        session = packet.session; sequence = packet.sequence;
        epoch = packet.present ? packet.epoch : null; revision = packet.revision;
        balances = packet.present ? packet.balances : Map.of();
        return true;
    }
    public int balance(ResourceLocation id) { return balances.getOrDefault(id,0); }
    public UUID epoch() { return epoch; }
    public long revision() { return revision; }
    public boolean ready() { return epoch != null; }
    public void clear() { session=null; epoch=null; sequence=-1; revision=0; balances=Map.of(); }
}
