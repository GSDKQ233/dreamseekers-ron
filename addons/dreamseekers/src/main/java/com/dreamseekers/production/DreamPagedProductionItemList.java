// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.production;
import com.solegendary.reignofnether.building.production.ProductionItemList;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.hud.buttons.Button;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.util.List;
/** Hall-only presentation adapter. Server get()/add() retain the complete explicit recipe whitelist. */
public final class DreamPagedProductionItemList extends ProductionItemList {
    @Override public List<Button> getButtons(ProductionPlacement placement){
        if(FMLEnvironment.dist!=Dist.CLIENT||!placement.getLevel().isClientSide())return List.of();
        return com.dreamseekers.client.DreamProductionPages.create(placement,List.copyOf(get()));
    }
}
