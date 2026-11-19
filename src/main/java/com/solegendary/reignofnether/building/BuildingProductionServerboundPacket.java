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

import static com.solegendary.reignofnether.building.BuildingUtils.findBuilding;

public class BuildingProductionServerboundPacket {
    public String itemName; // resource location string of the ProductionItem
    public BlockPos buildingPos; // used to identify the relevant production building
    public BuildingAction action;
    public UUID expectedHead;

    public static void startProduction(ProductionItem item) {
        BuildingClientEvents.switchHudToIdlestBuilding();
        if (HudClientEvents.hudSelectedPlacement instanceof ProductionPlacement pp) {
            String prodItemName;
            if (pp instanceof CustomBuildingPlacement) {
                prodItemName = item.getItemName();
            } else {
                prodItemName = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString();
            }
            if (prodItemName != null) {
                PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                        BuildingAction.START_PRODUCTION,
                        prodItemName,
                        pp.originPos));
            }
        }
    }

    /** Bind the intent when this HUD button is made, not when a delayed click is processed. */
    public static Runnable cancelClick(ProductionPlacement host, BlockPos pos, ProductionItem item, boolean front) {
        UUID observed = front && !host.productionQueue.isEmpty() && host.productionQueue.get(0).item == item
                ? host.productionQueue.get(0).queueEntryId : null;
        return () -> {
            String name = BuildingProductionClientboundPacket.recipeName(host, item);
            if (name == null || (front && observed == null)) { requestSync(pos); return; }
            PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                    front ? BuildingAction.CANCEL_PRODUCTION : BuildingAction.CANCEL_BACK_PRODUCTION, name, pos, observed));
        };
    }

    /** Older Java callers without an observed head may refresh, never guess its replacement. */
    public static void cancelProduction(BlockPos buildingPos, ProductionItem item, boolean frontItem) {
        if (frontItem) { requestSync(buildingPos); return; }
        if (HudClientEvents.hudSelectedPlacement instanceof ProductionPlacement host)
            cancelClick(host, buildingPos, item, false).run();
    }

    public static void requestSync(BlockPos buildingPos) {
        PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                BuildingAction.REQUEST_PRODUCTION_SYNC, "", buildingPos));
    }

    public BuildingProductionServerboundPacket(BuildingAction action, String itemName, BlockPos buildingPos) {
        this(action, itemName, buildingPos, null);
    }

    public BuildingProductionServerboundPacket(BuildingAction action, String itemName, BlockPos buildingPos, UUID expectedHead) {
        this.expectedHead = expectedHead;
        this.action = action;
        this.itemName = itemName;
        this.buildingPos = buildingPos;
    }

    public BuildingProductionServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(BuildingAction.class);
        this.itemName = buffer.readUtf();
        this.buildingPos = buffer.readBlockPos();
        if (action == BuildingAction.CANCEL_PRODUCTION && buffer.readBoolean()) expectedHead = buffer.readUUID();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.itemName);
        buffer.writeBlockPos(this.buildingPos);
        if (action == BuildingAction.CANCEL_PRODUCTION) {
            buffer.writeBoolean(expectedHead != null);
            if (expectedHead != null) buffer.writeUUID(expectedHead);
        }
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
            BuildingPlacement building = findBuilding(player.level(), this.buildingPos);
            if (building == null) return;
            // Opt-in addon queues use the authenticated sender and dimension, not
            // unrelated global sandbox presence or an empty alliance owner.
            boolean strict = building.getBuilding().requiresStrictPlacementOwnerCheck();
            String senderName = player.getName().getString();
            if (strict && (player.serverLevel() != building.getLevel()
                    || !OreMinePlacementAccess.canPlaceFor(senderName, building.ownerName,
                            SandboxServer.isSandboxPlayer(senderName),
                            building.ownerName != null && !building.ownerName.isBlank()
                                    && AlliancesServerEvents.canControlAlly(senderName, building.ownerName)))) {
                ReignOfNether.LOGGER.warn("Rejected restricted production action {} from {} for {}", this.action, senderName, building.ownerName);
                return;
            }
            else if (!strict && !player.getName().getString().equals(building.ownerName) &&
                    !SandboxServer.isAnyoneASandboxPlayer() &&
                    !AlliancesServerEvents.canControlAlly(player.getName().getString(), "")) {

                ReignOfNether.LOGGER.warn("BuildingProductionServerboundPacket: Tried to process packet from " + player.getName() + " for " + building.ownerName);
                success.set(false);
                return;
            }
            if (building instanceof ProductionPlacement pBuilding) {
                if (this.action == BuildingAction.REQUEST_PRODUCTION_SYNC) {
                    BuildingProductionClientboundPacket.syncTo(player, ctx.get().getNetworkManager(), pBuilding);
                } else {
                    ProductionItem productionItem;
                    if (pBuilding instanceof CustomBuildingPlacement cbp) {
                        productionItem = cbp.getProductionItem(this.itemName);
                    } else {
                        ResourceLocation key = ResourceLocation.tryParse(this.itemName);
                        productionItem = key == null ? null : ReignOfNetherRegistries.PRODUCTION_ITEM.get(key);
                    }
                    if (action == BuildingAction.CANCEL_PRODUCTION) {
                        if (productionItem != null && pBuilding.cancelObservedHead(productionItem, expectedHead)) {
                            BuildingProductionClientboundPacket.cancelProduction(pBuilding.ownerName, buildingPos, itemName, true, expectedHead);
                        } else {
                            BuildingProductionClientboundPacket.syncTo(player, ctx.get().getNetworkManager(), pBuilding);
                        }
                        return;
                    }
                    if(productionItem instanceof com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem)return;
                    if (productionItem != null) {
                        switch (this.action) {
                            case START_PRODUCTION -> {
                                boolean prodSuccess = pBuilding.startRequestedProduction(productionItem,
                                        reason -> com.solegendary.reignofnether.hud.HudClientboundPacket.showTempMessageI18n(player, reason));
                                if (prodSuccess && !pBuilding.productionQueue.isEmpty()) {
                                    ActiveProduction job = pBuilding.productionQueue.get(pBuilding.productionQueue.size() - 1);
                                    if (job.item == productionItem)
                                        BuildingProductionClientboundPacket.startProduction(pBuilding.ownerName, buildingPos, this.itemName, job);
                                }
                            }
                            case CANCEL_BACK_PRODUCTION -> {
                                boolean cancelSuccess = pBuilding.cancelProductionItem(productionItem, false);
                                if (cancelSuccess || pBuilding.productionQueue.isEmpty())
                                    BuildingProductionClientboundPacket.cancelProduction(pBuilding.ownerName, buildingPos, this.itemName, false);
                            }
                            default -> { }
                        }
                    }
                }
            }
            ReignOfNether.LOGGER.info("[Building] {} performed {} (itemName: {}, pos: {})", player.getName(), this.action, this.itemName, this.buildingPos);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
