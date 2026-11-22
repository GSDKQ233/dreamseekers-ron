// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unitprogression;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamLaboratory;
import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
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

/** Receipt-backed expansion research; legacy unlock flags do not replace paid completion. */
public final class ExpansionResearch {
    public static final ResourceLocation AIR_SUPERIORITY_ID=DreamseekersMod.id("air_superiority");
    public static final ResourceLocation BREACHING_STRUCTURE_ID=DreamseekersMod.id("breaching_structure");
    public static final ResourceLocation EMBER_INHIBITION_ID=DreamseekersMod.id("ember_inhibition");
    public static final ResourceLocation AARONCOS_VORTEX_ID=DreamseekersMod.id("aaroncos_vortex");
    public static final ResourceLocation HAND_PAIRING_ID=DreamseekersMod.id("hand_pairing");
    public static ReceiptBackedResearchItem AIR_SUPERIORITY,BREACHING_STRUCTURE,EMBER_INHIBITION,AARONCOS_VORTEX,HAND_PAIRING;
    private ExpansionResearch(){}
    public static boolean unlocked(String owner,ResourceLocation id,boolean client){
        return known(id)&&DreamSpecialistResearch.unlocked(owner,id,client);
    }
    private static boolean known(ResourceLocation id){
        return AIR_SUPERIORITY_ID.equals(id)||BREACHING_STRUCTURE_ID.equals(id)
            ||EMBER_INHIBITION_ID.equals(id)||AARONCOS_VORTEX_ID.equals(id)||HAND_PAIRING_ID.equals(id);
    }
    public static List<ReceiptBackedResearchItem> items(){
        return List.of(AIR_SUPERIORITY,BREACHING_STRUCTURE,EMBER_INHIBITION,AARONCOS_VORTEX,HAND_PAIRING);
    }
    public static void register(DreamLaboratory host){
        if(AIR_SUPERIORITY!=null)throw new IllegalStateException("Expansion research already registered");
        AIR_SUPERIORITY=add(host,AIR_SUPERIORITY_ID,100,0,125,25,45,"bone_wing_unit");
        BREACHING_STRUCTURE=add(host,BREACHING_STRUCTURE_ID,150,0,175,40,60,"shadow_golem_unit");
        EMBER_INHIBITION=add(host,EMBER_INHIBITION_ID,125,0,125,35,50,"ash_bone_wing_unit");
        AARONCOS_VORTEX=add(host,AARONCOS_VORTEX_ID,200,0,200,60,75,"skill_shadow_hand_grip");
        HAND_PAIRING=add(host,HAND_PAIRING_ID,250,0,200,75,75,"shadow_hand_unit");
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
        boolean anchor=buildings.stream().anyMatch(b->b instanceof DreamAnchorPlacement tree&&physical(b,p)
            &&tree.hasStandingUpgrade()&&(client||tree.productionAvailable()));
        if(!anchor)return false;
        if(AARONCOS_VORTEX_ID.equals(id)||HAND_PAIRING_ID.equals(id))return AdvancedUnitResearch.unlocked(p.ownerName,AdvancedUnitResearch.HAND_HARMONY_ID,client);
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
