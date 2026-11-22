package com.dreamseekers.resonance;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamLaboratory;
import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;

/** Two permanent paid research identities. Active cast qualification is deliberately not saved here. */
public final class LightResonanceResearch {
    public static final ResourceLocation FIRST_ID=DreamseekersMod.id("luminous_resonance_1");
    public static final ResourceLocation FINAL_ID=DreamseekersMod.id("luminous_resonance_2");
    public static ReceiptBackedResearchItem FIRST,FINAL;
    private LightResonanceResearch(){}

    /** A final-only/unsupported record is not authority for a tier-two cast. */
    public static int tier(String owner,boolean client){
        if(!DreamSpecialistResearch.unlocked(owner,FIRST_ID,client))return 0;
        return DreamSpecialistResearch.unlocked(owner,FINAL_ID,client)?2:1;
    }
    public static void register(DreamLaboratory host){
        if(FIRST!=null||FINAL!=null)throw new IllegalStateException("Light resonance already registered");
        FIRST=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,FIRST_ID,new Tier(1));
        FINAL=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,FINAL_ID,new Tier(2));
        // Append only: the pre-existing fifteen wind and six one-off research identities keep their order.
        host.productions.add(FIRST,null);host.productions.add(FINAL,null);
    }
    private static boolean physical(BuildingPlacement b,ProductionPlacement host){
        return b.getLevel()==host.getLevel()&&b.ownerName.equals(host.ownerName)&&b.isBuilt
            &&!b.isDestroyedServerside&&!b.shouldBeDestroyed()&&b.getHealth()>0
            &&b.getLevel().hasChunksAt(b.minCorner,b.maxCorner);
    }
    /** Actual completed known upgraded geometry, including legal shadow and wind variants; not an unlock flag. */
    private static boolean advanced(DreamAnchorPlacement tree,boolean client){
        return tree.hasStandingUpgrade()&&(client||tree.productionAvailable());
    }
    public static boolean prerequisites(ProductionPlacement p,boolean client,int targetTier){
        if((targetTier!=1&&targetTier!=2)||p.getBuilding()!=DreamseekersMod.RESONANCE_LABORATORY
            ||!DreamSpecialistResearch.ownerSupported(p.ownerName,client)
            ||(targetTier==2&&tier(p.ownerName,client)<1))return false;
        var buildings=client?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
        if(targetTier==2&&buildings.stream().noneMatch(b->b instanceof DreamAnchorPlacement tree
            &&physical(b,p)&&advanced(tree,client)))return false;
        if(!client)return p.getLevel() instanceof ServerLevel level&&!ResonanceNetwork.network(level,p.ownerName).active().isEmpty();
        // A client can preview visible structure prerequisites, never certify opaque server lifecycle data.
        // A nonempty rooted network necessarily has a direct root-to-dais edge. The server rechecks it at debit and every work tick.
        return buildings.stream().anyMatch(b->b instanceof DreamAnchorPlacement&&physical(b,p)
            &&buildings.stream().anyMatch(d->ResonanceNetwork.isDais(d)&&physical(d,p)&&ResonanceNetwork.linked(b,d)));
    }
    private static final class Tier extends ReceiptBackedResearchItem {
        private final int tier;
        Tier(int tier){super(tier==1?FIRST_ID:FINAL_ID,
            tier==1?ResourceCost.Research(120,120,100,45):ResourceCost.Research(240,240,200,75),
            Map.of(DreamPassiveEconomy.SHADOW,tier==1?30:80),DreamseekersMod.id("textures/icons/resonance_dais.png"),
            "research.dreamseekers.luminous_resonance_"+tier,"research.dreamseekers.luminous_resonance_"+tier+".desc",p->true);this.tier=tier;}
        @Override public boolean validHostAndPrerequisite(ProductionPlacement p){
            return super.validHostAndPrerequisite(p)&&p instanceof DreamLaboratoryPlacement lab&&lab.available()
                &&prerequisites(p,false,tier);
        }
        @Override protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip(ProductionPlacement p){
            var lines=super.liveTooltip(p);
            // Only the additional logical components are wrapped; superclass lines stay intact.
            lines.addAll(wrapTooltip(java.util.List.of(
                net.minecraft.network.chat.Component.translatable("research.dreamseekers.luminous_resonance_"+tier+".requires"),
                net.minecraft.network.chat.Component.translatable("research.dreamseekers.luminous_resonance.scope"),
                net.minecraft.network.chat.Component.translatable("research.dreamseekers.luminous_resonance.cast"))));
            return lines;
        }
        @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key){
            var button=super.getStartButton(p,key);var enabled=button.isEnabled;
            button.isEnabled=()->enabled.get()&&prerequisites(p,true,tier);return button;
        }
    }
}
