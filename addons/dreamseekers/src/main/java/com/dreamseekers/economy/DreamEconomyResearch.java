package com.dreamseekers.economy;

import com.dreamseekers.building.DreamAnchor;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Only the two explicitly authorized new addon research purchases. */
public final class DreamEconomyResearch {
    public static final ResourceLocation CONVERSION_ID=id("shadow_conversion_efficiency");
    public static final ResourceLocation ASCENDANCY_ID=id("world_tree_ascendancy");
    public static ReceiptBackedResearchItem CONVERSION,ASCENDANCY;
    private DreamEconomyResearch(){}
    private static ResourceLocation id(String p){return ResourceLocation.fromNamespaceAndPath("dreamseekers",p);}
    /** Called once in addon registration after the actual DreamAnchor was registered. */
    public static void register(DreamAnchor host) {
        if(CONVERSION!=null)throw new IllegalStateException("Dream economy research already registered");
        CONVERSION=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,CONVERSION_ID,
            new DreamResearchItem(CONVERSION_ID,ResourceCost.Research(0,100,150,60),30,false,
                "research.dreamseekers.shadow_conversion","research.dreamseekers.shadow_conversion.desc"));
        ASCENDANCY=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,ASCENDANCY_ID,
            new DreamResearchItem(ASCENDANCY_ID,ResourceCost.Research(300,0,300,90),100,true,
                "research.dreamseekers.world_tree_ascendancy","research.dreamseekers.world_tree_ascendancy.desc"));
        host.productions.add(CONVERSION,com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot2);
        host.productions.add(ASCENDANCY,com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot3);
    }
    public static boolean eligibleHost(ProductionPlacement p,boolean ascended) {
        return p.getBuilding() instanceof DreamAnchor tree
            &&DreamPassiveEconomy.isDreamOwner(PlayerServerEvents.getRTSPlayer(p.ownerName))
            &&(!ascended||tree.getUpgradeLevel(p)==1);
    }
    private static final class DreamResearchItem extends ReceiptBackedResearchItem {
        private final boolean ascended;
        private final int shadow; private final String title,description;
        DreamResearchItem(ResourceLocation id,ResourceCost cost,int shadow,boolean ascended,String title,String desc) {
            super(id,cost,Map.of(DreamPassiveEconomy.SHADOW,shadow),DreamPassiveEconomy.SHADOW_ICON,
                title,desc,p->eligibleHost(p,ascended));this.ascended=ascended;this.shadow=shadow;this.title=title;this.description=desc;
        }
        // Shared receipt lifecycle: temporary prerequisite loss pauses; explicit cancel refunds.
        // Capture/identity invalidation is handled by the shared receipt binding lifecycle.
        /** Rebuilt on every hover by the receipt-backed button, including changing work rates. */
        @Override protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip(ProductionPlacement p) {
            var lines=new java.util.ArrayList<Component>();
            lines.add(Component.translatable(title));
            lines.add(Component.translatable(description));
            var cost=getCost(true,p.ownerName);
            lines.add(Component.translatable("research.economy.cost",cost.food,cost.wood,cost.ore,
                ProductionWorkRates.estimatedSeconds(cost.ticks,ProductionWorkRates.percent(p,this))));
            lines.add(Component.translatable("research.economy.extra_cost",resourceLabel(DreamPassiveEconomy.SHADOW),shadow));
            lines.add(Component.translatable(ascended?"research.dreamseekers.requires_ascended_tree":"research.dreamseekers.requires_dream_owner"));
            lines.add(Component.translatable("research.economy.refund_notice"));
            return wrapTooltip(lines);
        }
        @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key) {
            var button=super.getStartButton(p,key);var base=button.isEnabled;
            button.isEnabled=()->base.get()
                &&DreamPassiveEconomy.isDreamOwner(PlayerClientEvents.getRTSPlayer(p.ownerName))
                &&(!ascended||p.getBuilding().getUpgradeLevel(p)==1);
            return button;
        }
    }
}
