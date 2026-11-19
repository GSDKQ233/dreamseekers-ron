package com.dreamseekers.resonance;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.progression.DreamWindResearch;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** One paid technology. Existing wind tiers, specialist upgrades and roster remain unlocked as before. */
public final class ResonanceResearch {
    public static final ResourceLocation ID=DreamseekersMod.id("resonance_tuning");
    public static ReceiptBackedResearchItem ITEM;
    private ResonanceResearch(){}
    public static boolean unlocked(String owner,boolean client){return DreamSpecialistResearch.unlocked(owner,ID,client);}
    public static void register(DreamLaboratory host){if(ITEM!=null)throw new IllegalStateException("Resonance already registered");
        ITEM=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,ID,new Tuning());host.productions.add(ITEM,null);}
    private static boolean prerequisites(ProductionPlacement p,boolean client){
        if(p.getBuilding()!=DreamseekersMod.RESONANCE_LABORATORY||!DreamSpecialistResearch.ownerSupported(p.ownerName,client)||!DreamWindResearch.hasAnchor(p,client))return false;
        var buildings=client?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
        return buildings.stream().anyMatch(b->ResonanceNetwork.isDais(b)&&b.getLevel()==p.getLevel()&&b.ownerName.equals(p.ownerName)
            &&b.isBuilt&&!b.isDestroyedServerside&&!b.shouldBeDestroyed()&&b.getHealth()>0&&b.getLevel().hasChunksAt(b.minCorner,b.maxCorner)&&(client||ResonanceNetwork.live(b)));
    }
    private static final class Tuning extends ReceiptBackedResearchItem {
        Tuning(){super(ID,ResourceCost.Research(0,150,100,45),Map.of(DreamPassiveEconomy.SHADOW,25),
            DreamseekersMod.id("textures/icons/resonance_dais.png"),"research.dreamseekers.resonance_tuning","research.dreamseekers.resonance_tuning.desc",p->true);}
        @Override public boolean validHostAndPrerequisite(ProductionPlacement p){return super.validHostAndPrerequisite(p)
            &&p instanceof DreamLaboratoryPlacement lab&&lab.available()&&prerequisites(p,false);}
        @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key){var button=super.getStartButton(p,key);var enabled=button.isEnabled;
            button.isEnabled=()->enabled.get()&&prerequisites(p,true);return button;}
    }
}
