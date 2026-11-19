package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.buildings.villagers.Blacksmith;
import com.solegendary.reignofnether.building.buildings.monsters.Laboratory;
import com.solegendary.reignofnether.building.buildings.piglins.BasaltSprings;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Research flags only. Workforce/salvage/exchange consumers are separate feature modules. */
public final class CoreEconomyResearch {
    private CoreEconomyResearch() {}
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("reignofnether",path);}
    public static ReceiptBackedResearchItem workforce() {
        return new ReceiptBackedResearchItem(id("research_expanded_workforce"),ResourceCost.Research(150,0,150,60),Map.of(),
            ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/iron_pickaxe.png"),
            "research.economy.workforce","research.economy.workforce.description",
            p->p.getBuilding() instanceof Blacksmith&&EconomyFactionAccess.serverOwner(p.ownerName,Factions.VILLAGERS));
    }
    public static ReceiptBackedResearchItem salvage() {
        return new ReceiptBackedResearchItem(id("research_global_salvage"),ResourceCost.Research(100,0,100,60),Map.of(),
            ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/bone.png"),
            "research.economy.salvage","research.economy.salvage.description",
            p->p.getBuilding() instanceof Laboratory&&EconomyFactionAccess.serverOwner(p.ownerName,Factions.MONSTERS));
    }
    public static ReceiptBackedResearchItem smelting() {
        return new ReceiptBackedResearchItem(id("research_instant_smelting"),ResourceCost.Research(0,100,150,60),Map.of(),
            ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/iron_ingot.png"),
            "research.economy.smelting","research.economy.smelting.description",
            p->p.getBuilding() instanceof BasaltSprings&&EconomyFactionAccess.serverOwner(p.ownerName,Factions.PIGLINS));
    }
}
