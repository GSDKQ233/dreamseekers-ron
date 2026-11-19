package com.dreamseekers.progression;
import com.dreamseekers.production.DreamUnitReceipts;
import com.dreamseekers.building.DreamResonanceAura;
import com.solegendary.reignofnether.building.production.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class DreamWindRates {
 private DreamWindRates(){}
 @SubscribeEvent public static void query(ProductionWorkEvent event) {
  var p=event.placement;var s=DreamWindProgression.state(p.getLevel().isClientSide(),p.ownerName);
  int aura=DreamResonanceAura.active(p)?10:0;
  if(DreamUnitReceipts.supported(event.item))event.percent=Math.min(200,100+(s.supported()&&s.wind()?40+10*s.level(1):0)+aura);
  else if(event.item.workKind==ProductionItem.WorkKind.RESEARCH)event.percent=Math.min(185,100+(s.supported()&&s.wind()?25+10*s.level(2):0)+aura);
 }
}
