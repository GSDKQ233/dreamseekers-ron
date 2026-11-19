package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.data.DataType;
import com.solegendary.reignofnether.building.data.OreProductionState;
import com.solegendary.reignofnether.building.data.OreMineTraitState;
import com.solegendary.reignofnether.building.data.OreMineStateCodecs;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A shared, server-authoritative income building. This registered object contains
 * definitions only; each placed mine owns its timer in BuildingPlacement's saved DataStorage.
 */
public class PassiveOreBuilding extends Building {
    public static final int ORE_PER_CYCLE = 1;
    public static final int MAX_PER_OWNER = 2;
    public static final int ORE_PER_MINUTE = 20 * 60 / OreProductionState.INTERVAL_TICKS * ORE_PER_CYCLE;

    public static final DataType<OreProductionState> PRODUCTION_STATE = DataType.createRegistered(
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "passive_ore_production"),
            (tag, server) -> readProductionState(tag),
            OreMineStateCodecs::writeProduction,
            OreProductionState::new
    );

    public static final DataType<OreMineTraitState> TRAIT_STATE = DataType.createRegistered(
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ore_mine_traits"),
            (tag, server) -> OreMineStateCodecs.readTrait(tag),
            OreMineStateCodecs::writeTrait, OreMineTraitState::new);

    public static OreProductionState readProductionState(CompoundTag tag) {
        return OreMineStateCodecs.readProduction(tag);
    }

    private final Supplier<Building> prerequisite;

    public PassiveOreBuilding(String name, String structureName, ResourceCost cost,
                              Supplier<Building> prerequisite, ResourceLocation icon, Block portraitBlock) {
        super(structureName, cost, false);
        this.name = name;
        this.prerequisite = prerequisite;
        this.icon = icon;
        this.portraitBlock = portraitBlock;
        this.maxHealth = 200d;
        this.buildTimeModifier = 1.0f;
        // Original mine structures use these foundation materials. They are ordinary
        // buildings: no resource drop-off, crop growth, portal or research behaviour.
        this.startingBlockTypes.addAll(List.of(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS,
                Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.COBBLESTONE, Blocks.BASALT, Blocks.POLISHED_BLACKSTONE));
    }

    /** Shared UI/server rule; the server rechecks immediately before accepting placement. */
    @Nullable
    public String getBuildAvailabilityError(String ownerName, @Nullable Faction ownerFaction,
                                            List<BuildingPlacement> buildings, boolean ignorePrerequisite) {
        if (ownerName == null || ownerName.isBlank() || ownerFaction == null || !getFaction().equals(ownerFaction))
            return "building.reignofnether.ore_mine_wrong_faction";
        int mines = 0;
        boolean hasPrerequisite = false;
        for (BuildingPlacement placement : buildings) {
            if (!ownerName.equals(placement.ownerName))
                continue;
            // Include unfinished foundations so queued/concurrent requests cannot evade the cap.
            if (placement.getBuilding() instanceof PassiveOreBuilding)
                mines++;
            if (placement.isBuilt && placement.getBuilding().isTypeOf(prerequisite.get()))
                hasPrerequisite = true;
        }
        if (mines >= MAX_PER_OWNER)
            return "building.reignofnether.ore_mine_limit";
        if (!hasPrerequisite && !ignorePrerequisite)
            return "building.reignofnether.ore_mine_prerequisite";
        return null;
    }

    private boolean canBuildClientside() {
        if (SandboxClientEvents.isSandboxPlayer())
            return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return false;
        String owner = HudClientEvents.hudSelectedEntity instanceof Unit unit
                ? unit.getOwnerName() : mc.player.getName().getString();
        return getBuildAvailabilityError(owner, PlayerClientEvents.getPlayerFaction(owner),
                BuildingClientEvents.getBuildings(), ResearchClient.hasCheat("modifythephasevariance")) == null;
    }

    @Override
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        String key = "buildings.reignofnether." + structureName;
        return new BuildingPlaceButton(Component.translatable(key).getString(), icon, hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                this::canBuildClientside,
                List.of(Component.translatable(key).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable(key + ".tooltip1", ORE_PER_MINUTE).getVisualOrderText(),
                        Component.translatable(key + ".tooltip2").getVisualOrderText(),
                        Component.translatable(key + ".tooltip3").getVisualOrderText()),
                this);
    }

    /** Observes supported runtime captures without mistaking initial JVM binding for ownership change. */
    public static OreMineTraitState traitState(BuildingPlacement placement) {
        OreMineTraitState state = placement.getDataStorage().getData(TRAIT_STATE);
        if (!placement.level.isClientSide() && state.observe(placement.ownerName, placement.getCaptureRevision()))
            placement.getDataStorage().getData(PRODUCTION_STATE).reset();
        return state;
    }

    @Nullable
    public static Resources account(BuildingPlacement placement) {
        Resources found = null;
        for (Resources resources : ResourcesServerEvents.resourcesList) {
            if (placement.ownerName != null && placement.ownerName.equals(resources.ownerName)) {
                if (found != null) return null; // Ambiguous ledger identity must not be rewarded twice.
                found = resources;
            }
        }
        return found;
    }

    public static boolean isProductionActive(BuildingPlacement placement) {
        if (placement.level.isClientSide() || placement.ownerName == null || placement.ownerName.isBlank()
                || !placement.level.dimension().equals(Level.OVERWORLD)) return false;
        Resources account = account(placement);
        return placement.getBuilding() instanceof PassiveOreBuilding && placement.isBuilt
                && !placement.shouldBeDestroyed() && placement.getCaptureRevision() >= 0
                && PlayerServerEvents.getRTSPlayer(placement.ownerName) != null && account != null
                && account.ore >= 0 && account.ore <= Integer.MAX_VALUE - ORE_PER_CYCLE;
    }

    @Override
    public void tick(Level tickLevel, BuildingPlacement placement) {
        if (tickLevel.isClientSide() || tickLevel != placement.level) return;
        OreProductionState production = placement.getDataStorage().getData(PRODUCTION_STATE);
        OreMineTraitState trait = traitState(placement);
        boolean active = isProductionActive(placement);
        int workers = this == Buildings.VILLAGER_ORE_MINE ? MineWorkPosts.countActive(placement) : 0;
        // Monster mines deliberately retain baseline production: global kill research is a separate service.
        int credit = trait.tick(active, workers, this == Buildings.PIGLIN_ORE_MINE);
        int cycles = production.advance(placement.ownerName, active, credit);
        if (cycles == 0) return;
        int amount = cycles * ORE_PER_CYCLE;
        ResourcesServerEvents.addSubtractResources(new Resources(placement.ownerName, 0, 0, amount));
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(placement.ownerName);
        player.scores.addToScore(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED, amount);
        player.scores.addToScore(RTSPlayerScoresEnum.ORES_HARVESTED, amount);
    }
}
