package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import javax.annotation.Nullable;
import java.util.List;

/** Server authority for the native recipe prerequisites previously present only in button lambdas.
 * Uses the placement OWNER, never the controlling ally's tech. Map-author CustomProductionItem
 * recipes and addon/receipt policies retain their explicitly separate eligibility contracts.
 * Losing a prerequisite during training pauses completion; the paid queue remains cancelable.
 * Cosmetic scout selection/fog visibility is not a technology requirement.
 */
public final class NativeProductionEligibility {
    private NativeProductionEligibility() {}

    @Nullable
    public static String error(ProductionItem item, ProductionPlacement p, boolean completing) {
        if (p == null || item == null) return "research.economy.forbidden";
        if (p.getLevel().isClientSide() || item instanceof CustomProductionItem) return null;
        var id=ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
        if (id==null || !id.getNamespace().equals("reignofnether")) return null;
        if (item instanceof com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem) return null;

        List<ProductionItem> beaconStages=List.of(ProductionItems.RESEARCH_BEACON_LEVEL_1,
                ProductionItems.RESEARCH_BEACON_LEVEL_2,ProductionItems.RESEARCH_BEACON_LEVEL_3,
                ProductionItems.RESEARCH_BEACON_LEVEL_4,ProductionItems.RESEARCH_BEACON_LEVEL_5);
        int beaconStage=beaconStages.indexOf(item);
        if (beaconStage>=0)
            return p instanceof BeaconPlacement && p.getUpgradeLevel()==beaconStage ? null : "research.economy.forbidden";

        boolean portal=item==ProductionItems.RESEARCH_PORTAL_FOR_CIVILIAN || item==ProductionItems.RESEARCH_PORTAL_FOR_MILITARY
                || item==ProductionItems.RESEARCH_PORTAL_FOR_TRANSPORT;
        if (portal) {
            if (!(p instanceof PortalPlacement) || p.getUpgradeLevel()!=0) return "research.economy.forbidden";
            // StartPortalTransformButton requires an otherwise idle portal. Its own head is legal
            // at completion, but no other queued operation may be silently transformed away.
            if (!completing && !p.productionQueue.isEmpty()) return "research.economy.forbidden";
            if (completing && (p.productionQueue.size()!=1 || p.productionQueue.get(0).item!=item)) return "research.economy.forbidden";
            if (item==ProductionItems.RESEARCH_PORTAL_FOR_TRANSPORT && !research(p,ProductionItems.RESEARCH_ADVANCED_PORTALS))
                return "research.economy.research_required";
            return null;
        }

        if (item.workKind==ProductionItem.WorkKind.RESEARCH && item.dupeRule==ProdDupeRule.DISALLOW
                && research(p,item)) return "research.economy.duplicate";

        if (item==ProductionItems.HUSK && !research(p,ProductionItems.RESEARCH_HUSKS)
                || item==ProductionItems.DROWNED && !research(p,ProductionItems.RESEARCH_DROWNED)
                || item==ProductionItems.STRAY && !research(p,ProductionItems.RESEARCH_STRAYS)
                || item==ProductionItems.BOGGED && !research(p,ProductionItems.RESEARCH_BOGGED)
                || item==ProductionItems.POISON_SPIDER && !research(p,ProductionItems.RESEARCH_POISON_SPIDERS))
            return "research.economy.research_required";

        Building required=null;
        if (item==ProductionItems.WINDCALLER) required=Buildings.LIBRARY;
        else if (item==ProductionItems.MARAUDER) required=Buildings.BASTION;
        else if (item==ProductionItems.HOGLIN) required=Buildings.HOGLIN_STABLES;
        else if (item==ProductionItems.BLAZE) required=Buildings.FLAME_SANCTUARY;
        else if (item==ProductionItems.WITHER_SKELETON) required=Buildings.WITHER_SHRINE;
        else if (item==ProductionItems.MAGMA_CUBE) required=Buildings.BASALT_SPRINGS;
        else if (item==ProductionItems.GHAST) required=Buildings.FORTRESS;
        else if (item==ProductionItems.RESEARCH_BOGGED || item==ProductionItems.RESEARCH_DROWNED
                || item==ProductionItems.RESEARCH_HUSKS || item==ProductionItems.RESEARCH_STRAYS) required=Buildings.GRAVEYARD;
        else if (item==ProductionItems.RESEARCH_POISON_SPIDERS || item==ProductionItems.RESEARCH_SPIDER_JOCKEYS) required=Buildings.SPIDER_LAIR;
        else if (item==ProductionItems.RESEARCH_MASS_BURIAL || item==ProductionItems.RESEARCH_SCULK_AMPLIFIERS
                || item==ProductionItems.RESEARCH_SILVERFISH) required=Buildings.STRONGHOLD;
        else if (item==ProductionItems.RESEARCH_LAB_LIGHTNING_ROD) required=Buildings.DUNGEON;
        else if (item==ProductionItems.RESEARCH_OVERFLOWING_GRAVEYARD
                && !ResearchServerEvents.playerHasCheat(p.ownerName,"modifythephasevariance")) required=Buildings.STRONGHOLD;
        if (required!=null && !finished(p,required,1)) return "research.economy.forbidden";

        if (item==ProductionItems.RESEARCH_CASTLE_FLAG || item==ProductionItems.RESEARCH_GRAND_LIBRARY
                || item==ProductionItems.RESEARCH_LAB_LIGHTNING_ROD || item==ProductionItems.RESEARCH_OVERFLOWING_GRAVEYARD
                || item==ProductionItems.RESEARCH_SUPERIOR_BLACKSMITH) {
            if (p.getUpgradeLevel()!=0) return "research.economy.duplicate";
        }
        // These three already guard enqueue in their subclasses; repeat at the completion boundary.
        if (item==ProductionItems.RESEARCH_MARKET_UPGRADE_MONSTERS
                && (p.getUpgradeLevel()!=0 || !finished(p,Buildings.SCULK_CATALYST,5))) return "research.economy.forbidden";
        if (item==ProductionItems.RESEARCH_MARKET_UPGRADE_PIGLINS
                && (p.getUpgradeLevel()!=0 || !finished(p,Buildings.PORTAL_CIVILIAN,4))) return "research.economy.forbidden";
        if (item==ProductionItems.RESEARCH_MARKET_UPGRADE_VILLAGER
                && (p.getUpgradeLevel()!=0 || !finished(p,Buildings.VILLAGER_HOUSE,6))) return "research.economy.forbidden";
        return null;
    }
    private static boolean research(ProductionPlacement p,ProductionItem item) {
        return ResearchServerEvents.playerHasResearch(p.ownerName,item);
    }
    private static boolean finished(ProductionPlacement p,Building building,int count) {
        return BuildingUtils.numFinishedBuildings(false,building,p.ownerName)>=count;
    }
}
