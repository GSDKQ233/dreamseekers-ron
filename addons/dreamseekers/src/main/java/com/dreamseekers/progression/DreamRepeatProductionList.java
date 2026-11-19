package com.dreamseekers.progression;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.*;
import java.util.*;
/** Presentation-only selector; all fifteen wind tiers remain in the complete server whitelist. */
public final class DreamRepeatProductionList extends ProductionItemList {
 @Override public List<Button> getButtons(ProductionPlacement p) {
  if(!p.getLevel().isClientSide())return List.of();DreamWindClient.query();var s=DreamWindProgression.state(true,p.ownerName);
  var result=new ArrayList<Button>();var keys=new Keybinding[]{Keybindings.abilitySlot1,Keybindings.abilitySlot2,Keybindings.abilitySlot3};
  for(int family=0;family<3;family++){int next=Math.min(4,s.level(family));result.add(DreamWindResearch.TIERS[family][next].getStartButton(p,keys[family]));}
  // Permanent fixed slots: completed research must not move a later item under the pointer.
  for(var item:oneTimeItems())result.add(stableOneTimeButton(p,item));
  return result;
 }
 private static java.util.List<com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem> oneTimeItems(){
  var items=new java.util.ArrayList<com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem>();
  items.addAll(java.util.List.of(com.dreamseekers.unitprogression.DreamSpecialistResearch.PRECISION_TOOLS,
          com.dreamseekers.unitprogression.DreamSpecialistResearch.SHELL_WEAVING,
          com.dreamseekers.unitprogression.DreamSpecialistResearch.CONTRACT_CARE,
          com.dreamseekers.unitprogression.DreamSpecialistResearch.STONE_SIEGE));
  items.add(com.dreamseekers.resonance.ResonanceResearch.ITEM);
  items.add(com.dreamseekers.neutralcontract.ContractResearch.CAPACITY);
  items.add(com.dreamseekers.resonance.LightResonanceResearch.FIRST);
  items.add(com.dreamseekers.resonance.LightResonanceResearch.FINAL);
  items.addAll(com.dreamseekers.unitprogression.AdvancedUnitResearch.items());
  items.addAll(com.dreamseekers.unitprogression.ExpansionResearch.items());
  return java.util.List.copyOf(items);
 }
 private static StartProductionButton stableOneTimeButton(ProductionPlacement p,
         com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem item){
  // Retain the original tooltip renderer, quote/rate calculations, query and purchase callback.
  var button=item.getStartButton(p,null);var nativeEnabled=button.isEnabled;var nativeClick=button.onLeftClick;
  var state=new LaboratoryResearchSlotState(
          ()->com.solegendary.reignofnether.research.ResearchClient.hasResearch(item.id()),
          ()->nativeEnabled.get(),nativeClick);
  button.isHidden=()->false;
  button.isEnabled=state::enabled;
  button.greyWhenDisabled=true;
  var nativeLabel=button.bottomLeftText;
  button.bottomLeftText=()->state.completed()
          ?net.minecraft.network.chat.Component.translatable("research.dreamseekers.completed_short").getString()
          :nativeLabel==null?"":nativeLabel.get();
  // A completion received after hover/render still cannot invoke the old purchase callback.
  button.onLeftClick=state::click;
  return button;
 }
}
