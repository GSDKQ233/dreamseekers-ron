// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.production.DreamHallPlacement;
import com.dreamseekers.production.DreamUnitProduction;
import com.dreamseekers.production.pagination.ProductionPageState;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.*;
import com.solegendary.reignofnether.keybinds.*;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** All state belongs to a freshly generated button list, never a building NBT/lifecycle field. */
public final class DreamProductionPages {
    private DreamProductionPages(){}
    private static List<Keybinding> slots(){return List.of(Keybindings.abilitySlot1,Keybindings.abilitySlot2,Keybindings.abilitySlot3,Keybindings.abilitySlot4,Keybindings.abilitySlot5,Keybindings.abilitySlot6);}
    private static Set<Integer> heldKeys(){
        Set<Integer> held=new HashSet<>();Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.getWindow()==null)return held;
        for(Keybinding k:slots())if(k.isDown())held.add(k.getKey());return held;
    }
    public static List<Button> create(ProductionPlacement placement,List<ProductionItem> recipes){
        // Called repeatedly and even virtually during base construction. No Hall lifecycle reads here.
        return page(placement,recipes,new ProductionPageState(recipes.size(),heldKeys()));
    }
    static final class PageButtons extends ArrayList<Button> {
        final ProductionPlacement placement;final ProductionPageState state;final long generation;
        PageButtons(ProductionPlacement placement,ProductionPageState state){this.placement=placement;this.state=state;generation=state.generation();}
        boolean current(){return state.allows(generation,placement.productionButtons==this,HudClientEvents.hudSelectedPlacement==placement,
                context(placement),true,true);}
    }
    private static List<Button> page(ProductionPlacement placement,List<ProductionItem> recipes,ProductionPageState state){
        PageButtons result=new PageButtons(placement,state);
        java.util.function.Consumer<List<FormattedCharSequence>> pageTooltip=lines->lines.add(
                Component.translatable("dreamseekers.production.page",state.page()+1,state.pages()).getVisualOrderText());
        for(int index=state.start();index<state.end();index++){
            ProductionItem recipe=recipes.get(index);
            Button button=recipe instanceof DreamUnitProduction dream
                    ?dream.getStartButton(placement,slots().get(index%6),pageTooltip)
                    :recipe.getStartButton(placement,slots().get(index%6));
            if(button==null)continue;
            var visible=button.isHidden;var enabled=button.isEnabled;
            button.isEnabled=()->result.current()&&!visible.get()&&enabled.get();
            if(!(recipe instanceof DreamUnitProduction)) {
                // Legacy non-live recipes retain their creation-time annotation.
                List<FormattedCharSequence> tooltip=new ArrayList<>(button.tooltipLines==null?List.of():button.tooltipLines);
                pageTooltip.accept(tooltip);button.tooltipLines=tooltip;
            }
            button.onLeftClick=()->{
                if(!result.current()||visible.get()||!enabled.get())return;
                // Only selected legal same-owner Halls participate. Do not switch displayed Hall/page.
                String owner=placement.ownerName;
                BuildingPlacement best=ProductionPageState.idlest(new ArrayList<>(BuildingClientEvents.getSelectedBuildings()),
                    b->b instanceof DreamHallPlacement p&&p.getBuilding()==placement.getBuilding()&&owner.equals(p.ownerName)
                        &&live(p)&&authorized(p)&&recipe.getProduceErrorMsg(p)==null,
                    b->((ProductionPlacement)b).productionQueue.stream().mapToDouble(a->a.ticksLeft).sum());
                var key=ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(recipe);
                if(best==null||key==null||!result.current())return;
                PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(BuildingAction.START_PRODUCTION,key.toString(),best.originPos));
            };
            result.add(button);
        }
        if(state.pages()>1)for(int direction:new int[]{-1,1}){
            String action=direction<0?"previous":"next";
            result.add(new ButtonBuilder("dreamseekers.production."+action)
                .iconResource(ResourceLocation.fromNamespaceAndPath("minecraft",direction<0?"textures/item/arrow.png":"textures/item/spectral_arrow.png"))
                .hotkey(null).isEnabled(()->result.current()&&state.canTurn(direction))
                .tooltipLines(List.of(Component.translatable("dreamseekers.production."+action).getVisualOrderText(),Component.translatable("dreamseekers.production.page",state.page()+1,state.pages()).getVisualOrderText()))
                .onLeftClick(()->{if(result.current()&&state.turn(direction,heldKeys()))placement.productionButtons=page(placement,recipes,state);}).build());
        }
        return result;
    }
    private static boolean context(ProductionPlacement placement){
        Minecraft mc=Minecraft.getInstance();return mc!=null&&mc.player!=null&&mc.level!=null&&mc.screen!=null
                &&mc.screen.getTitle().getString().contains("topdowngui_container")&&OrthoviewClientEvents.isEnabled()
                &&!TextInputClientEvents.isAnyInputFocused()&&BuildingClientEvents.getSelectedBuildings().contains(placement)
                &&com.solegendary.reignofnether.unit.UnitClientEvents.getSelectedUnits().isEmpty()
                &&live(placement)&&authorized(placement);
    }
    private static boolean live(ProductionPlacement p){
        return p instanceof DreamHallPlacement&&p.getBuilding()==DreamseekersMod.DREAM_HALL&&p.getLevel()==Minecraft.getInstance().level
                &&BuildingClientEvents.getBuildings().contains(p)&&p.isBuilt&&!p.isDestroyedServerside&&!p.shouldBeDestroyed();
    }
    private static boolean authorized(ProductionPlacement p){
        var player=Minecraft.getInstance().player;if(player==null||p.ownerName==null||p.ownerName.isBlank())return false;
        return p.ownerName.equals(player.getGameProfile().getName())||SandboxClientEvents.isSandboxPlayer()||AlliancesClient.canControlAlly(p.ownerName);
    }
}
