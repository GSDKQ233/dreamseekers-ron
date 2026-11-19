package com.solegendary.reignofnether.building.workposts;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.shared.*;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;

public record MineWorkPostStatusPacket(UUID stream, long order, UUID session, UUID epoch, String owner,
        ResourceLocation dimension, BlockPos origin, UUID view, UUID instance, long capture,
        int capacity, boolean producing, List<Worker> workers, String status, long request) {
    public record Worker(int entityId, UUID uuid, int post, boolean active) {}
    public MineWorkPostStatusPacket { workers = List.copyOf(workers); }
    public MineWorkPostStatusPacket(FriendlyByteBuf b) {
        this(b.readUUID(), b.readLong(), b.readUUID(), b.readUUID(), b.readUtf(256), b.readResourceLocation(),
                b.readBlockPos(), b.readUUID(), b.readUUID(), b.readLong(), b.readVarInt(), b.readBoolean(),
                readWorkers(b), b.readUtf(96), b.readLong());
    }
    private static List<Worker> readWorkers(FriendlyByteBuf b) {
        int size = b.readVarInt();
        if (size < 0 || size > MineWorkPosts.MAX_CAPACITY) throw new IllegalArgumentException("Invalid mine worker count");
        List<Worker> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(new Worker(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readBoolean()));
        return result;
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(stream); b.writeLong(order); b.writeUUID(session); b.writeUUID(epoch); b.writeUtf(owner, 256);
        b.writeResourceLocation(dimension); b.writeBlockPos(origin); b.writeUUID(view); b.writeUUID(instance);
        b.writeLong(capture); b.writeVarInt(capacity); b.writeBoolean(producing); b.writeVarInt(workers.size());
        for (Worker worker : workers) { b.writeVarInt(worker.entityId()); b.writeUUID(worker.uuid()); b.writeVarInt(worker.post()); b.writeBoolean(worker.active()); }
        b.writeUtf(status, 96); b.writeLong(request);
    }
    public boolean validOccupancy() {
        if ((capacity != MineWorkPosts.BASE_CAPACITY && capacity != MineWorkPosts.MAX_CAPACITY)
                || capture < 0 || workers.size() > capacity) return false;
        Set<Integer> posts = new HashSet<>(), ids = new HashSet<>(); Set<UUID> uuids = new HashSet<>();
        for (Worker worker : workers) if (worker.post() < 0 || worker.post() >= capacity
                || !posts.add(worker.post()) || !ids.add(worker.entityId()) || !uuids.add(worker.uuid())) return false;
        return true;
    }
    public int activeCount() { return (int) workers.stream().filter(Worker::active).count(); }
    public static void send(ServerPlayer actor, Connection connection, BuildingPlacement mine, UUID view, String status, long request) {
        if (!MineWorkPostService.owns(actor, mine)) return;
        var token = EconomyActionGate.query(actor, connection); if (token == null) return;
        long order = ResearchQueueSync.nextSequence(); if (order < 0) return;
        var packet = new MineWorkPostStatusPacket(ResearchQueueSync.snapshotSession(), order, token.session(), token.epoch(),
                mine.ownerName, mine.level.dimension().location(), mine.originPos, view,
                ResearchPlacementIdentity.serverIdentity(mine), mine.getCaptureRevision(), MineWorkPosts.capacity(mine),
                PassiveOreBuilding.isProductionActive(mine), MineWorkPosts.snapshot(mine), status, request);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> actor), packet);
        EconomyBalanceSync.send(actor);
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var connection = context.get().getNetworkManager();
        context.get().enqueueWork(() -> MineWorkPostClient.accept(this, connection));
        context.get().setPacketHandled(true);
    }
}
