package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.monsters.*;
import com.solegendary.reignofnether.building.buildings.neutral.*;
import com.solegendary.reignofnether.building.buildings.piglins.*;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.*;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.building.data.DataStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class BuildingSaveData extends SavedData {

    public final ArrayList<BuildingSave> buildings = new ArrayList<>();

    // Retried when the world is next opened with the same registry restored. Conflicts with a live
    // placement are parked separately for manual recovery, never spawned over the live building.
    private final ListTag unavailable = new ListTag();
    private ListTag conflicts = new ListTag();
    private static final String CONFLICTS = "repair3RetainedBuildingConflicts";
    private CompoundTag extraRoot = new CompoundTag();
    private CompoundTag incompatibleRoot;
    private static final Set<String> KNOWN_FIELDS = Set.of("customStructureName", "buildingKey", "buildingName",
            "x", "y", "z", "rotation", "rallyX", "rallyY", "rallyZ", "ownerName", "isDiagonalBridge", "isBuilt",
            "upgradeLevel", "portalType", "xp", "yp", "zp", "scenarioRoleIndex", "dataStorage", "partialBlocksDestroyed",
            "commandsNbt", "pendingConstruction");

    private static CompoundTag extraFields(CompoundTag original, Set<String> known) {
        CompoundTag extra = new CompoundTag();
        for (String key : original.getAllKeys())
            if (!known.contains(key)) extra.put(key, original.get(key).copy());
        return extra;
    }

    private static boolean compoundListOrAbsent(CompoundTag root, String key) {
        return !root.contains(key) || (root.get(key) instanceof ListTag list &&
                (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND));
    }

    private static BlockPos origin(CompoundTag record) {
        return new BlockPos(record.getInt("x"), record.getInt("y"), record.getInt("z"));
    }

    void retainUnmaterialized(BuildingSave row) {
        if (row.inputRecord != null) {
            unavailable.add(row.inputRecord);
            row.inputRecord = null;
            ReignOfNether.LOGGER.warn("Retaining unavailable building at {}. Restore its definition before editing that location.", row.originPos);
        }
    }

    private static BuildingSaveData create() {
        return new BuildingSaveData();
    }

    @Nonnull
    public static BuildingSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return create();
        }
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(tag -> BuildingSaveData.load(tag, server), BuildingSaveData::create, "saved-building-data");
    }

    public static BuildingSaveData load(CompoundTag tag, MinecraftServer server) {
        ReignOfNether.LOGGER.info("BuildingSaveData.load");

        BuildingSaveData data = create();
        if (!compoundListOrAbsent(tag, "buildings") || !compoundListOrAbsent(tag, CONFLICTS)) {
            // A future/incompatible container cannot safely accept even a single current-layout row.
            data.incompatibleRoot = tag.copy();
            ReignOfNether.LOGGER.error("Unrecognized saved-building-data layout. Its original NBT is protected; building edits will not persist. Restore matching mods and a backup before continuing.");
            return data;
        }
        data.extraRoot = extraFields(tag, Set.of("buildings", CONFLICTS));
        data.conflicts = tag.getList(CONFLICTS, Tag.TAG_COMPOUND).copy();
        Set<BlockPos> claimed = new HashSet<>();
        for (Tag entry : tag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            try {
                BuildingSave slot = decode(row, server);
                if (slot == null) {
                    data.unavailable.add(row.copy());
                } else if (!claimed.add(slot.originPos)) {
                    data.conflicts.add(row.copy());
                } else {
                    data.buildings.add(slot);
                }
            } catch (RuntimeException unsupported) {
                // E.g. an unknown rotation/portal enum or a registered extension that can't read this version.
                // Never apply a guessed default or lose the other loadable rows in the file.
                data.unavailable.add(row.copy());
                ReignOfNether.LOGGER.warn("Retained unreadable building at {} ({})", origin(row), unsupported.getClass().getSimpleName());
            }
        }
        if (!data.unavailable.isEmpty() || !data.conflicts.isEmpty())
            ReignOfNether.LOGGER.warn("Retained {} unavailable buildings and {} same-origin conflicts. Restore matching content before editing those locations; conflicts are held under {} for manual recovery.",
                    data.unavailable.size(), data.conflicts.size(), CONFLICTS);
        return data;
    }

    private static BuildingSave decode(CompoundTag row, MinecraftServer server) {
        Building definition;
        if (row.contains("customStructureName")) {
            definition = CustomBuildingServerEvents.getCustomBuilding(row.getString("customStructureName"));
        } else if (row.contains("buildingKey")) {
            ResourceLocation key = ResourceLocation.tryParse(row.getString("buildingKey"));
            definition = key == null ? null : ReignOfNetherRegistries.BUILDING.get(key);
        } else {
            definition = getOldBuilding(row.getString("buildingName"));
        }
        if (definition == null || !compoundListOrAbsent(row, "dataStorage") || !compoundListOrAbsent(row, "commandsNbt")
                || (row.contains("pendingConstruction") && !row.contains("pendingConstruction", Tag.TAG_COMPOUND)))
            return null;
        BuildingSave saved = new BuildingSave(origin(row), server.overworld(), definition, row.getString("ownerName"),
                Rotation.valueOf(row.getString("rotation")),
                new BlockPos(row.getInt("rallyX"), row.getInt("rallyY"), row.getInt("rallyZ")),
                row.getBoolean("isDiagonalBridge"), row.getBoolean("isBuilt"), row.getInt("upgradeLevel"),
                PortalPlacement.PortalType.valueOf(row.getString("portalType")),
                new BlockPos(row.getInt("xp"), row.getInt("yp"), row.getInt("zp")), row.getInt("scenarioRoleIndex"),
                DataStorage.read(row.getList("dataStorage", Tag.TAG_COMPOUND), server), row.getDouble("partialBlocksDestroyed"),
                row.getList("commandsNbt", Tag.TAG_COMPOUND).copy());
        saved.pendingConstruction = row.getCompound("pendingConstruction").copy();
        saved.extraFields = extraFields(row, KNOWN_FIELDS);
        saved.inputRecord = row.copy();
        return saved;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (incompatibleRoot != null) return incompatibleRoot.copy();
        tag.merge(extraRoot);
        Set<BlockPos> occupied = new HashSet<>();
        ListTag list = new ListTag();
        this.buildings.forEach(b -> {
            occupied.add(b.originPos);
            CompoundTag cTag = b.extraFields.copy();
            if (b.building instanceof CustomBuilding) {
                cTag.putString("customStructureName", b.building.name);
            }
            if (!(b.building instanceof CustomBuilding)) {
                cTag.putString("buildingKey", ReignOfNetherRegistries.BUILDING.getKey(b.building).toString());
            }
            cTag.putInt("x", b.originPos.getX());
            cTag.putInt("y", b.originPos.getY());
            cTag.putInt("z", b.originPos.getZ());
            cTag.putString("rotation", b.rotation.name());
            cTag.putInt("rallyX", b.rallyPoint != null ? b.rallyPoint.getX() : b.originPos.getX());
            cTag.putInt("rallyY", b.rallyPoint != null ? b.rallyPoint.getY() : b.originPos.getY());
            cTag.putInt("rallyZ", b.rallyPoint != null ? b.rallyPoint.getZ() : b.originPos.getZ());
            cTag.putString("ownerName", b.ownerName);
            cTag.putBoolean("isDiagonalBridge", b.isDiagonalBridge);
            cTag.putBoolean("isBuilt", b.isBuilt);
            cTag.putInt("upgradeLevel", b.upgradeLevel);
            cTag.putString("portalType", b.portalType != null ? b.portalType.name() : PortalPlacement.PortalType.BASIC.name());
            cTag.putInt("xp", b.portalDestination != null ? b.portalDestination.getX() : 0);
            cTag.putInt("yp", b.portalDestination != null ? b.portalDestination.getY() : 0);
            cTag.putInt("zp", b.portalDestination != null ? b.portalDestination.getZ() : 0);
            cTag.putInt("scenarioRoleIndex", b.scenarioRoleIndex);
            cTag.put("dataStorage", b.dataStorage.write());
            cTag.putDouble("partialBlocksDestroyed", b.partialBlocksDestroyed);
            cTag.put("commandsNbt", b.commandsNbt);
            cTag.put("pendingConstruction", b.pendingConstruction.copy());
            list.add(cTag);

            //ReignOfNether.LOGGER.info("BuildingSaveData.save: " + b.ownerName + "|" + buildingName);
        });
        for (var it = unavailable.iterator(); it.hasNext(); ) {
            CompoundTag record = (CompoundTag) it.next();
            if (occupied.contains(origin(record))) {
                // Don't silently discard either record or resurrect an old placement over its replacement.
                conflicts.add(record.copy());
                it.remove();
                ReignOfNether.LOGGER.warn("Parked unavailable building at occupied origin {} under {}", origin(record), CONFLICTS);
            } else list.add(record.copy());
        }
        if (!conflicts.isEmpty()) tag.put(CONFLICTS, conflicts.copy());
        tag.put("buildings", list);
        return tag;
    }

    public void save() {
        this.setDirty();
    }

    // backwards compatibility for old saves
    private static Building getOldBuilding(String name) {
        Building building = null;
        switch(name) {
            case OakBridge.buildingName -> building = Buildings.OAK_BRIDGE;
            case SpruceBridge.buildingName -> building = Buildings.SPRUCE_BRIDGE;
            case BlackstoneBridge.buildingName -> building = Buildings.BLACKSTONE_BRIDGE;
            case OakStockpile.buildingName -> building = Buildings.OAK_STOCKPILE;
            case SpruceStockpile.buildingName -> building = Buildings.SPRUCE_STOCKPILE;
            case VillagerHouse.buildingName -> building = Buildings.VILLAGER_HOUSE;
            case Graveyard.buildingName -> building = Buildings.GRAVEYARD;
            case WheatFarm.buildingName -> building = Buildings.WHEAT_FARM;
            case Laboratory.buildingName -> building = Buildings.LABORATORY;
            case Barracks.buildingName -> building = Buildings.BARRACKS;
            case PumpkinFarm.buildingName -> building = Buildings.PUMPKIN_FARM;
            case HauntedHouse.buildingName -> building = Buildings.HAUNTED_HOUSE;
            case Blacksmith.buildingName -> building = Buildings.BLACKSMITH;
            case WitchHut.buildingName -> building = Buildings.WITCH_HUT;
            case TownCentre.buildingName -> building = Buildings.TOWN_CENTRE;
            case IronGolemBuilding.buildingName -> building = Buildings.IRON_GOLEM_BUILDING;
            case Mausoleum.buildingName -> building = Buildings.MAUSOLEUM;
            case SculkCatalyst.buildingName -> building = Buildings.SCULK_CATALYST;
            case SpiderLair.buildingName -> building = Buildings.SPIDER_LAIR;
            case SlimePit.buildingName -> building = Buildings.SLIME_PIT;
            case ArcaneTower.buildingName -> building = Buildings.ARCANE_TOWER;
            case Library.buildingName -> building = Buildings.LIBRARY;
            case Dungeon.buildingName -> building = Buildings.DUNGEON;
            case Watchtower.buildingName -> building = Buildings.WATCHTOWER;
            case DarkWatchtower.buildingName -> building = Buildings.DARK_WATCHTOWER;
            case Castle.buildingName -> building = Buildings.CASTLE;
            case Stronghold.buildingName -> building = Buildings.STRONGHOLD;
            case CentralPortal.buildingName -> building = Buildings.CENTRAL_PORTAL;
            case PortalBasic.buildingName -> building = Buildings.PORTAL_BASIC;
            case PortalCivilian.buildingName -> building = Buildings.PORTAL_CIVILIAN;
            case PortalMilitary.buildingName -> building = Buildings.PORTAL_MILITARY;
            case PortalTransport.buildingName -> building = Buildings.PORTAL_TRANSPORT;
            case PortalPocket.buildingName -> building = Buildings.PORTAL_POCKET;
            case NetherwartFarm.buildingName -> building = Buildings.NETHERWART_FARM;
            case Bastion.buildingName -> building = Buildings.BASTION;
            case HoglinStables.buildingName -> building = Buildings.HOGLIN_STABLES;
            case FlameSanctuary.buildingName -> building = Buildings.FLAME_SANCTUARY;
            case WitherShrine.buildingName -> building = Buildings.WITHER_SHRINE;
            case BasaltSprings.buildingName -> building = Buildings.BASALT_SPRINGS;
            case Fortress.buildingName -> building = Buildings.FORTRESS;
            case Beacon.buildingName -> building = Buildings.BEACON;
            case CapturableBeacon.buildingName -> building = Buildings.CAPTURABLE_BEACON;
            case EndPortal.buildingName -> building = Buildings.END_PORTAL;
            case HealingFountain.buildingName -> building = Buildings.HEALING_FOUNTAIN;
            case NeutralTransportPortal.buildingName -> building = Buildings.NEUTRAL_TRANSPORT_PORTAL;
        }
        return building;
    }
}
