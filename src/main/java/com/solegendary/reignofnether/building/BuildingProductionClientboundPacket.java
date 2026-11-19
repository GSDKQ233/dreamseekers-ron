package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionPause;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem;
import com.solegendary.reignofnether.research.economy.ResearchQueueSync;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.solegendary.reignofnether.building.BuildingUtils.findBuilding;

public class BuildingProductionClientboundPacket {
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath("", "");

    // pos is used to identify the building object serverside
    public BuildingAction action;
    public BlockPos buildingPos;
    public String itemName;
    public float ticksLeft;
    public UUID entryId;
    private ProductionPause headPause = ProductionPause.INITIAL;
    public ProductionPause headPause() { return headPause; }
    public record Row(String itemName, float ticks, UUID entryId) { }
    private List<Row> rows = List.of();
    public List<Row> rows() { return rows; }

    // Bound both row count and strings so a single replacement fits a normal client payload.
    private static final int MAX_ROWS = 4096, MAX_NAME_CHARS_TOTAL = 200_000;

    private static boolean canReceive(ServerPlayer player, BuildingPlacement building) {
        return player.level() == building.getLevel() && (building.ownerName.equals(player.getName().getString())
                || FogOfWarServerEvents.canPlayerSeeBuilding(player, building));
    }

    public static String recipeName(ProductionPlacement host, ProductionItem item) {
        if (host instanceof CustomBuildingPlacement) return item.getItemName();
        ResourceLocation key = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
        return key == null ? null : key.toString();
    }

    private static ProductionItem recipe(ProductionPlacement host, String name) {
        if (host instanceof CustomBuildingPlacement custom) return custom.getProductionItem(name);
        ResourceLocation key = ResourceLocation.tryParse(name);
        return key == null ? null : ReignOfNetherRegistries.PRODUCTION_ITEM.get(key);
    }

    /** An authorized read/stale reply goes only to its requester. Public rows contain no receipts. */
    public static void syncTo(ServerPlayer player, net.minecraft.network.Connection connection, ProductionPlacement host) {
        if (!canReceive(player, host)) return;
        if (host.ownerName.equals(player.getName().getString()) && ResearchQueueSync.isOptIn(host)) {
            ResearchQueueSync.send(player, connection, host, "ready", 0);
            return;
        }
        BuildingProductionClientboundPacket snapshot = queueSnapshot(host, canSeeHeadState(player, host));
        if (snapshot != null) PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }

    /** Fog-visible public rows never expose payment details or an owner's prerequisite status. */
    public static BuildingProductionClientboundPacket publicSnapshot(ProductionPlacement host) {
        return queueSnapshot(host, false);
    }

    private static BuildingProductionClientboundPacket queueSnapshot(ProductionPlacement host, boolean includeHead) {
        var result = new BuildingProductionClientboundPacket(BuildingAction.SYNC_PRODUCTION, "", host.originPos);
        List<Row> entries = new ArrayList<>();
        int chars = 0;
        for (ActiveProduction job : host.productionQueue) {
            if (job.receiptResearch != null || job.item instanceof ReceiptBackedResearchItem) continue;
            String name = recipeName(host, job.item);
            if (name == null || name.length() > 32767 || !Float.isFinite(job.ticksLeft) || job.ticksLeft < 0
                    || entries.size() >= MAX_ROWS || (chars += name.length()) > MAX_NAME_CHARS_TOTAL) return null;
            entries.add(new Row(name, job.ticksLeft, job.queueEntryId));
        }
        result.rows = List.copyOf(entries);
        if (includeHead && !entries.isEmpty() && !host.productionQueue.isEmpty()) {
            ActiveProduction head = host.productionQueue.get(0);
            // A private research row can precede the first public row.
            if (head.queueEntryId.equals(entries.get(0).entryId()) && head.pause().revision() != 0) {
                result.entryId = head.queueEntryId;
                result.headPause = head.pause();
            }
        }
        return result;
    }

    private static boolean canSeeHeadState(ServerPlayer actor, ProductionPlacement host) {
        String name = actor.getGameProfile().getName();
        return OreMinePlacementAccess.canPlaceFor(name, host.ownerName,
                com.solegendary.reignofnether.sandbox.SandboxServer.isSandboxPlayer(name),
                !host.ownerName.isBlank() && com.solegendary.reignofnether.alliance.AlliancesServerEvents.canControlAlly(name, host.ownerName));
    }

    /** Constant-size transition only, not progress streaming. Use the actual level/host, not a position lookup. */
    public static void headStateChanged(ProductionPlacement host, ActiveProduction head) {
        if (!(host.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.getServer().isSameThread() || host.productionQueue.isEmpty()
                || host.productionQueue.get(0) != head || head.pause().revision() == 0) return;
        var packet = new BuildingProductionClientboundPacket(BuildingAction.SYNC_PRODUCTION_HEAD, "", host.originPos, head.ticksLeft);
        packet.entryId = head.queueEntryId;
        packet.headPause = head.pause();
        for (ServerPlayer actor : level.getServer().getPlayerList().getPlayers()) {
            if (actor.level() == level && canSeeHeadState(actor, host) && canReceive(actor, host))
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> actor), packet);
        }
    }

    // send only to players whose fog reveals at least one corner of this building
    private static void sendFiltered(String ownerName, BlockPos buildingPos, BuildingProductionClientboundPacket packet) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // Legacy packet identifies by position only. Resolve the owner's unique live placement
        // and never send a queue mutation into a different dimension's client building cache.
        BuildingPlacement b = null;
        for (BuildingPlacement candidate : BuildingServerEvents.getBuildings()) {
            if ((candidate.originPos.equals(buildingPos) || candidate.isPosInsideBuilding(buildingPos)) && ownerName.equals(candidate.ownerName)) {
                if (b != null && b != candidate) return; // ambiguous cross-dimension legacy address
                b = candidate;
            }
        }
        if (b == null) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (canReceive(sp, b)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), packet);
            }
        }
    }

    public static void startProduction(String ownerName, BlockPos buildingPos, String itemName) {
        sendFiltered(ownerName, buildingPos, new BuildingProductionClientboundPacket(BuildingAction.START_PRODUCTION, itemName, buildingPos));
    }

    public static void startProduction(String ownerName, BlockPos buildingPos, ProductionItem item, float ticksLeft) {
        ResourceLocation key = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
        if (key != null) sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.START_PRODUCTION, key.toString(), buildingPos, ticksLeft));
    }

    public static void startProduction(String ownerName, BlockPos buildingPos, String name, ActiveProduction job) {
        var packet = new BuildingProductionClientboundPacket(BuildingAction.START_PRODUCTION, name, buildingPos, job.ticksLeft);
        packet.entryId = job.queueEntryId;
        sendFiltered(ownerName, buildingPos, packet);
    }

    public static void cancelProduction(String ownerName, BlockPos buildingPos, String itemName, boolean frontItem) {
        cancelProduction(ownerName, buildingPos, itemName, frontItem, null);
    }

    public static void cancelProduction(String ownerName, BlockPos buildingPos, String itemName, boolean frontItem, UUID entryId) {
        var packet = new BuildingProductionClientboundPacket(frontItem ? BuildingAction.CANCEL_PRODUCTION
                : BuildingAction.CANCEL_BACK_PRODUCTION, itemName, buildingPos);
        packet.entryId = entryId;
        sendFiltered(ownerName, buildingPos, packet);
    }

    public static void clearQueue(String ownerName, BlockPos buildingPos) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.CLEAR_PRODUCTION, "", buildingPos)
        );
    }

    public static void completeProduction(String ownerName, BlockPos buildingPos, String itemName) {
        completeProduction(ownerName, buildingPos, itemName, null);
    }
    public static void completeProduction(String ownerName, BlockPos buildingPos, String itemName, UUID entryId) {
        var packet = new BuildingProductionClientboundPacket(BuildingAction.COMPLETE_PRODUCTION, itemName, buildingPos);
        packet.entryId = entryId;
        sendFiltered(ownerName, buildingPos, packet);
    }

    public BuildingProductionClientboundPacket(
            BuildingAction action,
            String itemName,
            BlockPos buildingPos
    ) {
        this.action = action;
        this.itemName = itemName;
        this.buildingPos = buildingPos;
        this.ticksLeft = -1;
    }

    public BuildingProductionClientboundPacket(
            BuildingAction action,
            String itemName,
            BlockPos buildingPos,
            float ticksLeft
    ) {
        this.action = action;
        this.itemName = itemName;
        this.buildingPos = buildingPos;
        this.ticksLeft = ticksLeft;
    }

    public BuildingProductionClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(BuildingAction.class);
        this.itemName = buffer.readUtf();
        this.buildingPos = buffer.readBlockPos();
        this.ticksLeft = buffer.readFloat();
        this.entryId = buffer.readBoolean() ? buffer.readUUID() : null;
        if (action == BuildingAction.SYNC_PRODUCTION) {
            int count = buffer.readVarInt(), chars = 0;
            if (count < 0 || count > MAX_ROWS) throw new IllegalArgumentException("Invalid production queue size");
            List<Row> entries = new ArrayList<>(count);
            Set<UUID> seen = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String name = buffer.readUtf();
                float ticks = buffer.readFloat();
                UUID id = buffer.readUUID();
                if (!Float.isFinite(ticks) || ticks < 0 || !seen.add(id) || (chars += name.length()) > MAX_NAME_CHARS_TOTAL)
                    throw new IllegalArgumentException("Invalid production queue row");
                entries.add(new Row(name, ticks, id));
            }
            rows = List.copyOf(entries);
        }
        if (action == BuildingAction.SYNC_PRODUCTION_HEAD || (action == BuildingAction.SYNC_PRODUCTION && entryId != null)) {
            headPause = ProductionPause.decode(buffer);
            if (entryId == null || headPause.revision() == 0
                    || (action == BuildingAction.SYNC_PRODUCTION_HEAD && (!Float.isFinite(ticksLeft) || ticksLeft < 0))
                    || (action == BuildingAction.SYNC_PRODUCTION && (rows.isEmpty() || !entryId.equals(rows.get(0).entryId()))))
                throw new IllegalArgumentException("Invalid production head state");
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.itemName);
        buffer.writeBlockPos(this.buildingPos);
        buffer.writeFloat(this.ticksLeft);
        buffer.writeBoolean(entryId != null);
        if (entryId != null) buffer.writeUUID(entryId);
        if (action == BuildingAction.SYNC_PRODUCTION) {
            buffer.writeVarInt(rows.size());
            for (Row row : rows) {
                buffer.writeUtf(row.itemName());
                buffer.writeFloat(row.ticks());
                buffer.writeUUID(row.entryId());
            }
        }
        if (action == BuildingAction.SYNC_PRODUCTION_HEAD || (action == BuildingAction.SYNC_PRODUCTION && entryId != null))
            headPause.encode(buffer);
    }

    // client-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);

        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level == null || mc.getConnection() == null || mc.getConnection().getConnection() != ctx.get().getNetworkManager()) return;
                BuildingPlacement building = findBuilding(true, this.buildingPos);
                if (!(building instanceof ProductionPlacement host) || host.getLevel() != mc.level) return;
                switch (action) {
                    case SYNC_PRODUCTION -> {
                        List<ActiveProduction> replacement = new ArrayList<>(rows.size());
                        for (Row row : rows) {
                            ProductionItem item = recipe(host, row.itemName());
                            if (item == null || item instanceof ReceiptBackedResearchItem) return;
                            ActiveProduction job = new ActiveProduction(item, host.ownerName, row.entryId());
                            job.ticksLeft = row.ticks();
                            replacement.add(job);
                        }
                        if (entryId != null && !replacement.isEmpty()) {
                            ActiveProduction head = replacement.get(0);
                            head.acceptPause(entryId, headPause, head.ticksLeft);
                        }
                        host.productionQueue.clear();
                        host.productionQueue.addAll(replacement);
                    }
                    case SYNC_PRODUCTION_HEAD -> {
                        // A superseded/unseen job is not a retry, cancellation or new order.
                        if (!host.productionQueue.isEmpty())
                            host.productionQueue.get(0).acceptPause(entryId, headPause, ticksLeft);
                    }
                    case CLEAR_PRODUCTION -> host.productionQueue.clear();
                    case START_PRODUCTION -> {
                        if (entryId == null) BuildingProductionServerboundPacket.requestSync(host.originPos);
                        else host.acceptProductionStart(recipe(host, itemName), ticksLeft, entryId);
                    }
                    case CANCEL_BACK_PRODUCTION -> host.cancelProductionItem(recipe(host, itemName), false);
                    case CANCEL_PRODUCTION, COMPLETE_PRODUCTION -> {
                        if (entryId == null) {
                            BuildingProductionServerboundPacket.requestSync(host.originPos);
                            return;
                        }
                        for (ActiveProduction job : host.productionQueue) {
                            if (entryId.equals(job.queueEntryId)) {
                                if (action == BuildingAction.COMPLETE_PRODUCTION && !job.completed) job.complete(host);
                                host.productionQueue.remove(job);
                                break;
                            }
                        }
                    }
                    default -> { }
                }
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}