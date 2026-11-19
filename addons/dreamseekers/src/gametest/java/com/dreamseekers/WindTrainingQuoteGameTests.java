package com.dreamseekers;
import com.dreamseekers.progression.*;
import com.dreamseekers.production.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Actual21 registry recipes, exact integer quotes, and normal server ticks across a midqueue rate change. */
@GameTestHolder("dreamseekers_wind_training") @PrefixGameTestTemplate(false)
public final class WindTrainingQuoteGameTests {
 private static final String BATCH="dreamseekers_wind_training";
 private static final List<WindTrainingQuoteCase> CASES=new ArrayList<>();
 @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var c:CASES)c.close();CASES.clear();}
 private static final class WindTrainingQuoteCase implements AutoCloseable {
  final GameTestHelper h;final String owner;final Resources r;final List<BuildingPlacement> hosts=new ArrayList<>();
  WindTrainingQuoteCase(GameTestHelper h,String suffix){this.h=h;owner="WindTrainingGT_"+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));r=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(r);CASES.add(this);}
  ProductionPlacement anchor(){var l=h.getLevel();var at=BuildingAssetGameTests.outdoorPos(h,4,1,4);for(int x=-3;x<25;x++)for(int z=-3;z<25;z++){l.setBlock(at.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<22;y++)l.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(l),l,at,Rotation.NONE))l.setBlock(b.getBlockPos(),b.getBlockState(),2);var p=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,l,at,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);p.onBuilt();hosts.add(p);return p;}
  void max(int family){for(int n=1;n<=5;n++)DreamWindProgression.commitTier(r,family,n);}
  public void close(){for(var p:hosts){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(owner));}
 }
 @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=80)
 public static void everyVisibleRecipeUsesBaseOrThirtyOrFortyFivePercentCeilAndSamePopulation(GameTestHelper h){
  var f=new WindTrainingQuoteCase(h,"All19");var recipes=new LinkedHashSet<DreamUnitProduction>();
  for(var host:List.of(DreamseekersMod.DREAM_ANCHOR,DreamseekersMod.BEETLE_SANCTUARY,DreamseekersMod.DREAM_HALL))for(var item:host.productions.get())if(item instanceof DreamUnitProduction unit)recipes.add(unit);
  h.assertTrue(recipes.size()==21&&!recipes.contains(DreamseekersMod.LEGACY_WORKER_PRODUCTION),"exact21 normally visible registry recipes, no hidden legacy adapter");
  for(var item:recipes)assertQuote(h,item,item.getCost(false,f.owner),0);
  h.assertTrue(DreamWindProgression.earnWind(h.getLevel(),f.owner,f.r.accountEpoch),"entitlement fixture");for(var item:recipes)assertQuote(h,item,item.getCost(false,f.owner),30);
  f.max(0);for(var item:recipes)assertQuote(h,item,item.getCost(false,f.owner),45);
  var hidden=DreamseekersMod.LEGACY_WORKER_PRODUCTION;h.assertTrue(hidden.getCost(false,f.owner)==hidden.defaultCost,"hidden legacy recipe not discounted");
  h.assertTrue(ProductionItems.VILLAGER.getCost(false,f.owner)==ProductionItems.VILLAGER.defaultCost,"other-faction ordinary unit recipe unchanged");
  System.out.println("DREAM_WIND_TRAINING_PASS all19_ceil_prices_population_work");h.succeed();
 }
 private static void assertQuote(GameTestHelper h,DreamUnitProduction item,ResourceCost q,int discount){var b=item.defaultCost;h.assertTrue(q.food==DreamWindProgression.discount(b.food,discount)&&q.wood==DreamWindProgression.discount(b.wood,discount)&&q.ore==DreamWindProgression.discount(b.ore,discount)&&q.emerald==DreamWindProgression.discount(b.emerald,discount),"actual recipe quote derived once from immutable base: "+item.getItemName());h.assertTrue(q.population==b.population&&q.ticks==b.ticks,"quote leaves population/base work unchanged");}
 @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
 public static void nativeMidqueueSpeedChangeKeepsOriginalPaidThirtyFiveFoodRefund(GameTestHelper h){
  var f=new WindTrainingQuoteCase(h,"Midqueue");var p=f.anchor();h.assertTrue(DreamWindProgression.earnWind(h.getLevel(),f.owner,f.r.accountEpoch),"Wind fixture");var recipe=DreamseekersMod.WORKER_PRODUCTION;
  h.assertTrue(p.startProductionItem(recipe)&&f.r.food==9965,"actual normal worker queue pays35Food once");var active=p.productionQueue.get(0);active.ticksLeft=100;long[] remaining={0};
  h.runAtTickTime(20,()->{h.assertTrue(active.workHundredths>=7060&&active.workHundredths<=7340,"twenty real ticks at1.4x, no truncation");remaining[0]=active.workHundredths;f.max(0);f.max(1);h.assertTrue(recipe.getCost(false,f.owner).food==28&&ProductionWorkRates.percent(p,recipe)==190,"new quote28 and new rate1.9x apply after progression change");h.assertTrue(active.workHundredths==remaining[0]&&f.r.food==9965&&f.r.paidReceipts.values().stream().anyMatch(receipt->receipt.food()==35),"existing queue work not reset and original35Food receipt immutable");});
  h.runAtTickTime(40,()->{long consumed=remaining[0]-active.workHundredths;h.assertTrue(consumed>=3610&&consumed<=3990,"next twenty real ticks use1.9x");h.assertTrue(p.cancelProductionItem(recipe,true),"cancel real partly trained queue");h.assertTrue(f.r.food==10000&&f.r.paidReceipts.isEmpty()&&p.productionQueue.isEmpty(),"refund remains35 original, not current28");System.out.println("DREAM_WIND_TRAINING_PASS real_midqueue_rate_original_refund");h.succeed();});
 }
}
