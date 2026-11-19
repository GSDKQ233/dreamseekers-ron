package com.solegendary.reignofnether.building.production;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import net.minecraftforge.eventbus.api.Event;
/** Server/client pure rate query. Never payment, progress mutation or bonus persistence. */
public final class ProductionWorkEvent extends Event {
 public final ProductionPlacement placement;public final ProductionItem item;public int percent=100;
 public ProductionWorkEvent(ProductionPlacement placement,ProductionItem item){this.placement=placement;this.item=item;}
}
