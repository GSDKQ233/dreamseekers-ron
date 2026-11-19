package com.dreamseekers.progression;
import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.*;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.keybinds.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Fifteen internal paid tier identities, exactly three user-facing repeat buttons. */
public final class DreamWindResearch {
 public static final String[] NAMES={"wind_thrifty_mustering","wind_training_cadence","wind_shared_insight"};
 public static final Tier[][] TIERS=new Tier[3][5];
 private DreamWindResearch(){}
 public static void register(DreamLaboratory host) {
  if(TIERS[0][0]!=null)throw new IllegalStateException("Wind research already registered");
  for(int family=0;family<3;family++)for(int n=1;n<=5;n++) {
   var item=new Tier(family,n);TIERS[family][n-1]=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,item.id(),item);
   host.productions.add(item,new Keybinding[]{Keybindings.abilitySlot1,Keybindings.abilitySlot2,Keybindings.abilitySlot3}[family]);
  }
 }
 private static ResourceCost cost(int family,int tier) {
  int[] f={80,120,60},w={100,60,120},o={60,100,120};
  var c=ResourceCost.Research(f[family]*tier,w[family]*tier,o[family]*tier,0);c.ticks=(family==2?800:600)+200*(tier-1);return c;
 }
 public static boolean hasAnchor(ProductionPlacement p,boolean client) {
  var buildings=client?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
  return buildings.stream().anyMatch(b->b.getBuilding() instanceof DreamAnchor&&b.getLevel()==p.getLevel()&&b.ownerName.equals(p.ownerName)&&b.isBuilt&&!b.isDestroyedServerside&&!b.shouldBeDestroyed()&&b.getLevel().isLoaded(b.minCorner)&&b.getLevel().isLoaded(b.maxCorner));
 }
 public static final class Tier extends ReceiptBackedResearchItem {
  public final int family,tier,shadow;
  Tier(int family,int tier) {
   super(DreamseekersMod.id(NAMES[family]+"_"+tier),cost(family,tier),Map.of(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW,(family==2?15:10)*tier),
    DreamseekersMod.id("textures/icons/research/"+NAMES[family]+".png"),"research.dreamseekers."+NAMES[family],"research.dreamseekers."+NAMES[family]+".desc",p->true);
   this.family=family;this.tier=tier;this.shadow=(family==2?15:10)*tier;
  }
  @Override public boolean hasCompleted(String owner){var s=DreamWindProgression.server(owner);return !s.supported()||s.level(family)>=tier;}
  @Override public boolean validHostAndPrerequisite(ProductionPlacement p) {
   return super.validHostAndPrerequisite(p)&&p.getBuilding()==DreamseekersMod.RESONANCE_LABORATORY
    &&p instanceof DreamLaboratoryPlacement lab&&lab.available()&&hasAnchor(p,false)
    &&DreamWindProgression.canAdvance(ExtendedResourcesSync.account(p.ownerName),family,tier);
  }
  @Override public boolean itemIsBeingProduced(boolean client,String owner) {
   var buildings=client?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
   for(var building:buildings)if(building.ownerName.equals(owner)&&building instanceof ProductionPlacement p)
    for(var active:p.productionQueue)if(active.item instanceof Tier other&&other.family==family&&!active.completed)return true;
   return false;
  }
  @Override public boolean isQueuedPurchaseObsolete(ProductionPlacement p,ActiveProduction a){return !DreamWindProgression.canAdvance(ExtendedResourcesSync.account(p.ownerName),family,tier);}
  @Override public boolean acceptsPaidCompletion(ProductionPlacement p,ActiveProduction a){return DreamWindProgression.canAdvance(ExtendedResourcesSync.account(p.ownerName),family,tier);}
  @Override public void commitPaidCompletion(ProductionPlacement p,ActiveProduction a,Resources account){DreamWindProgression.commitTier(account,family,tier);}
  @Override public void publishPaidCompletion(ProductionPlacement p,ActiveProduction a){DreamWindPackets.sendToOwner(p.getLevel().getServer(),p.ownerName);}
  /** Refresh local aura/time and private progression text even when the cached button stays selected. */
  private java.util.List<net.minecraft.util.FormattedCharSequence> windTooltip(ProductionPlacement p) {
   var s=DreamWindProgression.state(true,p.ownerName);int level=s.level(family);
   boolean capped=level>=5;var c=getCost(true,p.ownerName);var text=new ArrayList<Component>();
   text.add(Component.translatable("research.dreamseekers."+NAMES[family]));
   text.add(Component.translatable("research.dreamseekers.wind_level",level,5));
   text.add(Component.translatable("research.dreamseekers."+NAMES[family]+".desc"));
   if(capped)text.add(Component.translatable("research.dreamseekers.wind_max"));
   else {
    text.add(Component.translatable("research.dreamseekers.wind_quote",c.food,c.wood,c.ore,shadow,ProductionWorkRates.estimatedSeconds(c.ticks,ProductionWorkRates.percent(p,this))));
    text.add(Component.translatable("research.dreamseekers.wind_next_effect",family==0?30+3*tier:family==1?140+10*tier:125+10*tier));
   }
   text.add(Component.translatable("research.dreamseekers.wind_requires"));text.add(Component.translatable("research.economy.refund_notice"));
   return wrapTooltip(text);
  }
  @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key) {
   ResearchQueueClient.query(p);DreamWindClient.query();var text=windTooltip(p);
   var button=new StartProductionButton("research.dreamseekers."+NAMES[family],DreamseekersMod.id("textures/icons/research/"+NAMES[family]+".png"),key,()->false,
    ()->{var now=DreamWindProgression.state(true,p.ownerName);return p.isBuilt&&now.supported()&&now.wind()&&now.level(family)==tier-1&&ResearchQueueClient.ready(p)&&hasAnchor(p,true)&&!itemIsBeingProduced(true,p.ownerName);},text,this) {
     @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY) {
      this.tooltipLines=Tier.this.windTooltip(p);
      super.renderTooltip(graphics,mouseX,mouseY);
     }
   };
   button.onLeftClick=()->ResearchQueueClient.start(p,this);return button;
  }
 }
}
