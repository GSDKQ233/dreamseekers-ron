package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;


public class BuildingProductionServerboundPacket {
    public String itemName; // resource location string of the ProductionItem
    public BlockPos buildingPos; // used to identify the relevant production building
    public BuildingAction action;
    /** Public queue identity, not a payment receipt. The legacy head action retains its wire format. */
    public UUID expectedEntry;

    public static void startProduction(ProductionItem item) {
        BuildingClientEvents.switchHudToIdlestBuilding();
        if (HudClientEvents.hudSelectedPlacement instanceof ProductionPlacement pp) {
            String prodItemName = BuildingProductionClientboundPacket.recipeName(pp, item);
            if (prodItemName != null) {
                PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                        BuildingAction.START_PRODUCTION,
                        prodItemName,
                        pp.originPos));
            }
        }
    }

    /** A queue row is captured when the HUD is built, never inferred from the recipe at click time. */
    public static Runnable cancelClick(ProductionPlacement host, ActiveProduction row, boolean front) {
        String name = BuildingProductionClientboundPacket.recipeName(host, row.item);
        UUID observed = row.queueEntryId;
        return () -> {
            if (name == null) { requestSync(host.originPos); return; }
            PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                    front ? BuildingAction.CANCEL_PRODUCTION : BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION,
                    name, host.originPos, observed));
        };
    }

    /** Older button factories know only their recipe. A non-head row cannot safely be inferred. */
    public static Runnable cancelClick(ProductionPlacement host, BlockPos pos, ProductionItem item, boolean front) {
        if (front && !host.productionQueue.isEmpty() && host.productionQueue.get(0).item == item)
            return cancelClick(host, host.productionQueue.get(0), true);
        return () -> requestSync(host.originPos);
    }

    /** A Java caller without an observed row may refresh, not guess an order. */
    public static void cancelProduction(BlockPos buildingPos, ProductionItem item, boolean frontItem) {
        requestSync(buildingPos);
    }

    public static void requestSync(BlockPos buildingPos) {
        PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                BuildingAction.REQUEST_PRODUCTION_SYNC, "", buildingPos));
    }

    public BuildingProductionServerboundPacket(BuildingAction action, String itemName, BlockPos buildingPos) {
        this(action, itemName, buildingPos, null);
    }

    public BuildingProductionServerboundPacket(BuildingAction action, String itemName, BlockPos buildingPos, UUID expectedEntry) {
        this.expectedEntry = expectedEntry;
        this.action = action;
        this.itemName = itemName;
        this.buildingPos = buildingPos;
    }

    public BuildingProductionServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(BuildingAction.class);
        this.itemName = buffer.readUtf();
        this.buildingPos = buffer.readBlockPos();
        if ((action == BuildingAction.CANCEL_PRODUCTION || action == BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION)
                && buffer.readBoolean()) expectedEntry = buffer.readUUID();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.itemName);
        buffer.writeBlockPos(this.buildingPos);
        if (action == BuildingAction.CANCEL_PRODUCTION || action == BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION) {
            buffer.writeBoolean(expectedEntry != null);
            if (expectedEntry != null) buffer.writeUUID(expectedEntry);
        }
    }

    /** Resolved server-side for the target at execution time, never from some other player's sandbox state. */
    public static boolean canOperate(ServerPlayer actor, ProductionPlacement host) {
        if (actor == null || actor.serverLevel() != host.getLevel() || host.isDestroyedServerside
                || host.ownerName == null || host.ownerName.isBlank()) return false;
        String sender = actor.getGameProfile().getName();
        return OreMinePlacementAccess.canPlaceFor(sender, host.ownerName, SandboxServer.isSandboxPlayer(sender),
                AlliancesServerEvents.canControlAlly(sender, host.ownerName));
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("Sender for unit action packet was null");
                success.set(false);
                return;
            }
            // The legacy position-only protocol addresses the current Overworld RTS map.
            // Never resolve another dimension's entity/position against its placements.
            if (player.level() != BuildingServerEvents.getServerLevel()) return;
            var host = ProductionQueueAddress.resolve(player.level(), buildingPos, BuildingServerEvents.getBuildings());
            if (host == null) return;
            // Reads use the separate same-level/fog gate. The legacy recipe-only tail action is a refresh.
            if (action == BuildingAction.REQUEST_PRODUCTION_SYNC || action == BuildingAction.CANCEL_BACK_PRODUCTION) {
                BuildingProductionClientboundPacket.syncTo(player, ctx.get().getNetworkManager(), host);
                return;
            }
            if (action != BuildingAction.START_PRODUCTION && action != BuildingAction.CANCEL_PRODUCTION
                    && action != BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION) return;
            if (!canOperate(player, host)) return;

            ProductionItem item;
            if (host instanceof CustomBuildingPlacement custom) {
                item = custom.getProductionItem(itemName);
            } else {
                ResourceLocation key = ResourceLocation.tryParse(itemName);
                item = key == null ? null : ReignOfNetherRegistries.PRODUCTION_ITEM.get(key);
            }
            if (action == BuildingAction.CANCEL_PRODUCTION || action == BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION) {
                boolean front = action == BuildingAction.CANCEL_PRODUCTION;
                if (item != null && host.cancelObservedEntry(item, expectedEntry, front)) {
                    BuildingProductionClientboundPacket.cancelProduction(host, itemName, front, expectedEntry);
                } else {
                    BuildingProductionClientboundPacket.syncTo(player, ctx.get().getNetworkManager(), host);
                }
                return;
            }
            if (item == null || item instanceof com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem) return;
            if (host.startRequestedProduction(item,
                    reason -> com.solegendary.reignofnether.hud.HudClientboundPacket.showTempMessageI18n(player, reason))
                    && !host.productionQueue.isEmpty()) {
                ActiveProduction job = host.productionQueue.get(host.productionQueue.size() - 1);
                if (job.item == item)
                    BuildingProductionClientboundPacket.startProduction(host, itemName, job);
            }
            ReignOfNether.LOGGER.info("[Building] {} performed {} (itemName: {}, pos: {})", player.getName(), this.action, this.itemName, this.buildingPos);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
