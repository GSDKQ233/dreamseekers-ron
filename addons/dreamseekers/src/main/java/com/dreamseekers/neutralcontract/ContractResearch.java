package com.dreamseekers.neutralcontract;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** One paid extension, not a second research ledger. */
public final class ContractResearch {
    public static final ResourceLocation ID=DreamseekersMod.id("contract_capacity");
    public static ReceiptBackedResearchItem CAPACITY;
    private ContractResearch(){}
    public static int capacity(String owner,boolean client){return DreamSpecialistResearch.unlocked(owner,ID,client)?5:3;}
    public static void register(DreamLaboratory host){
        if(CAPACITY!=null)throw new IllegalStateException("Contract research already registered");
        CAPACITY=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,ID,new Item());host.productions.add(CAPACITY,null);
    }
    private static boolean allowed(ProductionPlacement p,boolean client){return p.getBuilding()==DreamseekersMod.RESONANCE_LABORATORY
        &&DreamSpecialistResearch.ownerSupported(p.ownerName,client)&&DreamSpecialistResearch.hasEcology(p,client)
        &&com.dreamseekers.progression.DreamWindResearch.hasAnchor(p,client);}
    private static final class Item extends ReceiptBackedResearchItem {
        Item(){super(ID,ResourceCost.Research(100,100,75,45),Map.of(DreamPassiveEconomy.SHADOW,20),DreamseekersMod.id("textures/icons/contract_ecology.png"),
            "research.dreamseekers.contract_capacity","research.dreamseekers.contract_capacity.desc",p->true);}
        @Override public boolean validHostAndPrerequisite(ProductionPlacement p){return super.validHostAndPrerequisite(p)&&p instanceof DreamLaboratoryPlacement lab&&lab.available()&&allowed(p,false);}
        @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key){var button=super.getStartButton(p,key);var old=button.isEnabled;button.isEnabled=()->old.get()&&allowed(p,true);return button;}
    }
}
