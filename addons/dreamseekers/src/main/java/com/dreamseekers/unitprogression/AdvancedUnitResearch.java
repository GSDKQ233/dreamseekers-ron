// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unitprogression;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamLaboratory;
import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.resonance.LightResonanceResearch;
import com.dreamseekers.resonance.ResonanceResearch;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.dreamseekers.worldtree.WorldTreeModel;
import com.dreamseekers.worldtree.WorldTreeState;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Append-only, one-time paid purchases. No mutable extra save schema or client unlock authority. */
public final class AdvancedUnitResearch {
    public static final ResourceLocation STONE_RANGE_ID=DreamseekersMod.id("stone_range");
    public static final ResourceLocation PULSE_RANGE_ID=DreamseekersMod.id("pulse_range");
    public static final ResourceLocation MOTHER_NEST_ID=DreamseekersMod.id("mother_nest");
    public static final ResourceLocation HAND_HARMONY_ID=DreamseekersMod.id("hand_harmony");
    public static final ResourceLocation LIGHT_EXTENSION_ID=DreamseekersMod.id("light_extension");
    public static ReceiptBackedResearchItem STONE_RANGE,PULSE_RANGE,MOTHER_NEST,HAND_HARMONY,LIGHT_EXTENSION;
    private AdvancedUnitResearch(){}
    public static boolean unlocked(String owner,ResourceLocation id,boolean client){
        return known(id)&&DreamSpecialistResearch.unlocked(owner,id,client);
    }
    private static boolean known(ResourceLocation id){
        return STONE_RANGE_ID.equals(id)||PULSE_RANGE_ID.equals(id)||MOTHER_NEST_ID.equals(id)
            ||HAND_HARMONY_ID.equals(id)||LIGHT_EXTENSION_ID.equals(id);
    }
    public static List<ReceiptBackedResearchItem> items(){
        return List.of(STONE_RANGE,PULSE_RANGE,MOTHER_NEST,HAND_HARMONY,LIGHT_EXTENSION);
    }
    public static void register(DreamLaboratory host){
        if(STONE_RANGE!=null)throw new IllegalStateException("Advanced research already registered");
        STONE_RANGE=add(host,STONE_RANGE_ID,0,100,125,0,40,"stone_spirit_thrower");
        PULSE_RANGE=add(host,PULSE_RANGE_ID,0,150,125,25,45,"resonance_dais");
        MOTHER_NEST=add(host,MOTHER_NEST_ID,125,100,100,30,50,"beetle_mother_unit");
        HAND_HARMONY=add(host,HAND_HARMONY_ID,200,0,150,50,60,"shadow_hand_unit");
        LIGHT_EXTENSION=add(host,LIGHT_EXTENSION_ID,150,150,125,40,60,"golden_fox_unit");
    }
    private static ReceiptBackedResearchItem add(DreamLaboratory host,ResourceLocation id,int food,int wood,int ore,int shadow,int seconds,String icon){
        var item=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,id,new Item(id,
            ResourceCost.Research(food,wood,ore,seconds),shadow,DreamseekersMod.id("textures/icons/"+icon+".png")));
        host.productions.add(item,null);return item;
    }
    private static boolean physical(BuildingPlacement b,ProductionPlacement host){
        return b.getLevel()==host.getLevel()&&b.ownerName.equals(host.ownerName)&&b.isBuilt
            &&!b.isDestroyedServerside&&!b.shouldBeDestroyed()&&b.getHealth()>0
            &&b.getLevel().hasChunksAt(b.minCorner,b.maxCorner);
    }
    /** Host and actual geometry are rechecked at payment and each work tick, not only GUI preview. */
    public static boolean prerequisites(ProductionPlacement p,boolean client,ResourceLocation id){
        if(!known(id)||p.getBuilding()!=DreamseekersMod.RESONANCE_LABORATORY
            ||!DreamSpecialistResearch.ownerSupported(p.ownerName,client))return false;
        var buildings=client?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
        boolean upgraded=HAND_HARMONY_ID.equals(id);
        boolean anchor=buildings.stream().anyMatch(b->b instanceof DreamAnchorPlacement tree&&physical(b,p)
            &&(!upgraded||tree.model().branch!=WorldTreeModel.Branch.BASE)
            &&(client||(tree.productionAvailable()&&tree.tree().supported&&(tree.tree().phase==WorldTreeState.Phase.COMPLETE||(!upgraded&&tree.tree().phase==WorldTreeState.Phase.IDLE)))));
        if(!anchor)return false;
        if(PULSE_RANGE_ID.equals(id))return ResonanceResearch.unlocked(p.ownerName,client);
        if(MOTHER_NEST_ID.equals(id))return DreamSpecialistResearch.hasEcology(p,client);
        if(LIGHT_EXTENSION_ID.equals(id))return LightResonanceResearch.tier(p.ownerName,client)>=1;
        return true;
    }
    /** Client-only execution stays isolated from research registration and server prerequisites. */
    private static final class ClientTooltip {
        static java.util.List<net.minecraft.util.FormattedCharSequence> wrap(List<Component> components){
            var mc=net.minecraft.client.Minecraft.getInstance();
            int width=AdvancedResearchTooltipLayout.wrapWidth(mc.getWindow().getGuiScaledWidth());
            var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
            for(Component component:components)lines.addAll(mc.font.split(component,width));
            return lines;
        }
    }
    private static final class Item extends ReceiptBackedResearchItem {
        private final int shadowCost;
        Item(ResourceLocation id,ResourceCost cost,int shadow,ResourceLocation icon){
            super(id,cost,shadow==0?Map.of():Map.of(DreamPassiveEconomy.SHADOW,shadow),icon,
                "research.dreamseekers."+id.getPath(),"research.dreamseekers."+id.getPath()+".desc",p->true);
            this.shadowCost=shadow;
        }
        @Override public boolean validHostAndPrerequisite(ProductionPlacement p){
            return super.validHostAndPrerequisite(p)&&p instanceof DreamLaboratoryPlacement lab&&lab.available()
                &&prerequisites(p,false,id());
        }
        @Override protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip(ProductionPlacement p){
            // Rebuild logical components before wrapping: parent lines are already visual-order
            // sequences, which must never be converted back to text and bidi-reordered again.
            var components=new java.util.ArrayList<Component>();
            String key="research.dreamseekers."+id().getPath();
            components.add(Component.translatable(key));
            components.add(Component.translatable(key+".desc"));
            var cost=getCost(true,p.ownerName);
            components.add(Component.translatable("research.economy.cost",cost.food,cost.wood,cost.ore,
                com.solegendary.reignofnether.building.production.ProductionWorkRates.estimatedSeconds(cost.ticks,
                    com.solegendary.reignofnether.building.production.ProductionWorkRates.percent(p,this))));
            if(shadowCost>0)components.add(Component.translatable("research.economy.extra_cost",resourceLabel(DreamPassiveEconomy.SHADOW),shadowCost));
            components.add(Component.translatable("research.economy.refund_notice"));
            components.add(Component.translatable(key+".requires"));
            components.add(Component.translatable("research.dreamseekers.advanced.once"));
            return ClientTooltip.wrap(components);
        }
        @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key){
            var button=super.getStartButton(p,key);var enabled=button.isEnabled;
            button.isEnabled=()->enabled.get()&&prerequisites(p,true,id());return button;
        }
    }
}
