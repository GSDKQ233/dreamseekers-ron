// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Stable identities for the eight original structures; authorized functionality is selected explicitly.
 * A role is not itself permission to enable a recipe or ability. Shadow mine/furnace opt into passive income.
 * Registry paths are stable save identities and match both NBT resource directories.
 */
public enum DreamBuildingSpec {
    DREAM_DEPOT("dream_depot", "Dream Depot", Role.LOGISTICS, "chest_minecart", Blocks.BARREL),
    SHADOW_MINE("shadow_mine", "Shadow Mine", Role.ORE_EXTRACTION, "iron_pickaxe", Blocks.DEEPSLATE_BRICKS),
    SHADOW_FURNACE("shadow_furnace", "Shadow Furnace", Role.SHADOW_ENERGY, "amethyst_shard", Blocks.POLISHED_BLACKSTONE_BRICKS),
    DREAM_HALL("dream_hall", "Dream Hall", Role.PRODUCTION, "book", Blocks.QUARTZ_BLOCK),
    RESONANCE_LABORATORY("resonance_laboratory", "Resonance Laboratory", Role.RESEARCH, "spyglass", Blocks.AMETHYST_BLOCK),
    CONTRACT_ECOLOGY("contract_ecology", "Contract Ecology Court", Role.CONTRACTS, "lead", Blocks.OAK_LEAVES),
    RESONANCE_DAIS("resonance_dais", "Resonance Dais", Role.RESONANCE, "ender_eye", Blocks.AMETHYST_BLOCK),
    SHADOW_WATCHTOWER("shadow_watchtower", "Shadow Watchtower", Role.DEFENSE, "glowstone_dust", Blocks.STONE_BRICKS);

    public enum Role {
        LOGISTICS, ORE_EXTRACTION, SHADOW_ENERGY, PRODUCTION,
        RESEARCH, CONTRACTS, RESONANCE, DEFENSE
    }

    // Historical public constants retained for compatibility; current prices are explicit below.
    public static final int PROVISIONAL_WOOD_COST = 100;
    public static final int PROVISIONAL_ORE_COST = 50;

    private final String structureName;
    private final String displayName;
    private final Role role;
    private final String iconItem;
    private final Block portraitBlock;

    DreamBuildingSpec(String structureName, String displayName, Role role, String iconItem, Block portraitBlock) {
        this.structureName = structureName;
        this.displayName = displayName;
        this.role = role;
        this.iconItem = iconItem;
        this.portraitBlock = portraitBlock;
    }

    public String structureName() { return structureName; }
    public String displayName() { return displayName; }
    public Role role() { return role; }
    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/" + structureName + ".png");
    }
    public Block portraitBlock() { return portraitBlock; }

    /** Role-specific durability: cheap logistics and the open dais are fragile, the hall and the tower are sturdy. */
    public double maxHealth() {
        return switch (this) {
            case DREAM_DEPOT -> 120.0d;
            case SHADOW_MINE -> 220.0d;
            case SHADOW_FURNACE -> 200.0d;
            case DREAM_HALL -> 260.0d;
            case RESONANCE_LABORATORY -> 220.0d;
            case CONTRACT_ECOLOGY -> 200.0d;
            case RESONANCE_DAIS -> 160.0d;
            case SHADOW_WATCHTOWER -> 240.0d;
        };
    }
    /** Build-time multiplier applied by the core to the block-count based construction time. */
    public float buildTimeModifier() {
        return switch (this) {
            case DREAM_DEPOT, RESONANCE_DAIS -> 0.8f;
            case DREAM_HALL, CONTRACT_ECOLOGY -> 0.9f;
            case SHADOW_MINE, SHADOW_FURNACE, RESONANCE_LABORATORY, SHADOW_WATCHTOWER -> 1.0f;
        };
    }

    /** Role-specific construction costs; supply and income are configured separately. */
    public ResourceCost newProvisionalCost() {
        return switch (this) {
            case DREAM_DEPOT -> ResourceCost.Building(0,75,0,0);
            case SHADOW_MINE -> ResourceCost.Building(0,300,150,0);
            case SHADOW_FURNACE -> ResourceCost.Building(0,150,100,0);
            case DREAM_HALL -> ResourceCost.Building(0,200,100,0);
            case RESONANCE_LABORATORY -> ResourceCost.Building(0,250,150,0);
            case CONTRACT_ECOLOGY -> ResourceCost.Building(0,150,75,0);
            case RESONANCE_DAIS -> ResourceCost.Building(0,150,150,0);
            case SHADOW_WATCHTOWER -> ResourceCost.Building(0,100,75,0);
        };
    }
}
