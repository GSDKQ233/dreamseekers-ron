package com.solegendary.reignofnether.building.custombuilding;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.blocks.RTSStructureBlockEntity;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.ItemShopAddon;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class CustomBuildingServerEvents {

    // since every custom building has a different structure, we need to maintain a list of them here
    public static final Set<CustomBuilding> customBuildings = new HashSet<>();

    public static boolean retainBuildings = false; // retain custom buildiongs across singleplayer worlds in the same client session

    public static CustomBuilding getCustomBuilding(String name) {
        for (CustomBuilding building : customBuildings)
            if (building.name.equals(name))
                return building;
        return null;
    }

    public static void deregisterCustomBuilding(String buildingName) {
        CustomBuilding building = getCustomBuilding(buildingName);
        if (building != null) deregisterCustomBuilding(BuildingServerEvents.getServerLevel(), building);
    }

    static void deregisterCustomBuilding(ServerLevel level, CustomBuilding definition) {
        if (level == null || level != BuildingServerEvents.getServerLevel() || !customBuildings.remove(definition)) return;
        BuildingServerEvents.getBuildings().removeIf(b -> {
            if (b.level != level || b.getBuilding() != definition) return false;
            b.onRemoved(BuildingPlacement.RemovalReason.REPLACED);
            return true;
        });
        saveCustomBuildings(level);
    }

    // registers and places a new custom building on server and client
    public static boolean createAndRegisterNewCustomBuilding(ResourceLocation structureRL, String structureName, ServerLevel level,
                                                             BlockPos pos, Vec3i structureSize) {
        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> template = manager.get(structureRL);
        CompoundTag structureNbt = null;
        if (template.isPresent()) {
            structureNbt = template.get().save(new CompoundTag());
        }
        if (structureNbt != null) {
            ArrayList<BuildingBlock> blocks = BuildingUtils.getAbsoluteBlockData(
                    BuildingBlockData.getBuildingBlocksFromNbt(structureNbt),
                    level, pos, Rotation.NONE
            );
            int numSolidBlocks = 0;
            Block portraitBlock = Blocks.COMMAND_BLOCK;
            for (BuildingBlock bb : blocks) {
                BlockState bs = bb.getBlockState();
                if (!bs.isAir() && bs.getFluidState().isEmpty() &&
                    !CustomBuilding.INVULNERABLE_BLOCKS.contains(bb.getBlockState().getBlock())) {
                    numSolidBlocks += 1;
                    portraitBlock = bs.getBlock();
                }
            }
            if (numSolidBlocks == 0) {
                PlayerServerEvents.sendMessageToAllPlayers("ERROR (server): cannot registerBuilding custom building with no solid blocks");
            } else {
                CustomBuilding building = new CustomBuilding(structureName, structureSize, portraitBlock, structureNbt);
                for (CustomBuilding customBuilding : customBuildings) {
                    if (customBuilding.name.equals(building.name)) {
                        PlayerServerEvents.sendMessageToAllPlayers("ERROR (server): custom building " + building.name + " already exists");
                        return false;
                    }
                }
                if (!building.productions.get().isEmpty()) {
                    PlayerServerEvents.sendMessageToAllPlayers(
                            "sandbox.reignofnether.custom_buildings.trainable_units.save_success",
                            false, building.productions.get().size()
                    );
                }
                if (!building.stockedItems.isEmpty()) {
                    PlayerServerEvents.sendMessageToAllPlayers(
                            "sandbox.reignofnether.custom_buildings.buyable_items.save_success",
                            false, building.stockedItems.size()
                    );
                }

                // Prepare and validate first. A rejected template/name must never detach the live placement.
                BuildingPlacement placement = building.createBuildingPlacement(level, pos, Rotation.NONE, "");
                if (placement == null) return false;
                BuildingServerEvents.getBuildings().removeIf(previous -> {
                    if (previous.level != level || !previous.originPos.equals(pos)) return false;
                    previous.onRemoved(BuildingPlacement.RemovalReason.REPLACED);
                    return true;
                });
                customBuildings.add(building);
                BuildingServerEvents.getBuildings().add(placement);
                CustomBuildingClientboundPacket.registerCustomBuilding(building);
                saveCustomBuildings(level);
                BuildingServerEvents.saveBuildings(level);
                BuildingServerEvents.placeBuildingClientside(pos);
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
        if (level != null) {
            saveCustomBuildings(level);
        }
    }

    public static void saveCustomBuildings(ServerLevel level) {
        if (level == null)
            return;
        CustomBuildingSaveData customBuildingData = CustomBuildingSaveData.getInstance(level);
        customBuildingData.customBuildings.clear();
        customBuildings.forEach(b -> {
            b.packAttributesNbt();
            b.packCommandsNbt();
            customBuildingData.customBuildings.add(new CustomBuildingSave(
                    b.structureNbt,
                    b.name,
                    b.structureSize,
                    b.attributesNbt,
                    b.commandsNbt
            ));
        });
        customBuildingData.save();
        level.getDataStorage().save();
    }

    public static void loadCustomBuildings(ServerLevel level) {
        if (!retainBuildings)
            customBuildings.clear();

        CustomBuildingSaveData customBuildingData = CustomBuildingSaveData.getInstance(level);
        customBuildingData.customBuildings.forEach(bSave -> {
            CustomBuilding building = new CustomBuilding(bSave.buildingName, bSave.structureSize, Blocks.COMMAND_BLOCK, bSave.structureNbt, bSave.attributesNbt, bSave.commandsNbt);
            boolean buildingExists = false;
            for (CustomBuilding customBuilding : customBuildings) {
                if (customBuilding.name.equals(building.name)) {
                    buildingExists = true;
                    break;
                }
            }
            if (!buildingExists)
                customBuildings.add(building);
            ReignOfNether.LOGGER.info("loaded custom building in serverevents: " + bSave.buildingName);
        });
    }

    /** Compatibility entry point; only BuildingServerEvents subscribes to login events now. */
    @Deprecated
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        BuildingServerEvents.onPlayerJoin(event);
    }

    public static void syncCustomBuildings(String playerName) {
        for (CustomBuilding customBuilding : customBuildings) {
            CustomBuildingClientboundPacket.registerCustomBuilding(playerName, customBuilding);
        }
    }
}
