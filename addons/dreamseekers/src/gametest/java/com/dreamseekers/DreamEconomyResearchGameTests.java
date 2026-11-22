package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.solegendary.reignofnether.faction.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Actual Forge fixture, not a substitute for queue/network and independent-JVM research tests. */
@GameTestHolder("dreamseekers_research_economy")
@PrefixGameTestTemplate(false)
public final class DreamEconomyResearchGameTests {
 private static final String PREFIX="DreamEconomyResearch_";
 /** Exact owner set per test: both cases run concurrently in defaultBatch. */
 private static final class Fixture extends ArrayList<Entity> {
  final Set<String> owners;
  Fixture(String... owners){this.owners=Set.of(owners);}
 }
 private static Resources account(String name,Faction faction){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,faction));var r=new Resources(name,10000,10000,10000);ResourcesServerEvents.resourcesList.add(r);return r;}
 private static <T extends Mob> T spawn(GameTestHelper h,Fixture entities,EntityType<T> type,String owner,int x){
  T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);var p=h.absolutePos(new BlockPos(x,2,3));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.getLevel().setBlock(p.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);h.assertTrue(h.getLevel().addFreshEntity(e),"real fixture entity enters level");entities.add(e);return e;
 }
 private static void cleanup(Fixture entities){
  for(var player:List.copyOf(PlayerServerEvents.rtsPlayers))if(entities.owners.contains(player.name))ResearchServerEvents.removeAllResearchFor(player.name);
  PlayerServerEvents.rtsPlayers.removeIf(p->entities.owners.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->entities.owners.contains(r.ownerName));entities.forEach(Entity::discard);
 }
 private static void at(GameTestHelper h,Fixture entities,int tick,Runnable action){h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error failure){cleanup(entities);throw failure;}});}
 @GameTest(templateNamespace="dreamseekers_research_economy",template="ore_mine_test_arena",timeoutTicks=80)
 public static void nativeKillResearchSnapshotAndCap(GameTestHelper h){
  String dream=PREFIX+"Kill",enemy=PREFIX+"Enemy";var es=new Fixture(dream,enemy);Resources r=account(dream,DreamseekersMod.DREAMSEEKERS);account(enemy,Factions.VILLAGERS);
  var attacker=spawn(h,es,EntityRegistrar.ZOMBIE_UNIT.get(),dream,2);spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),enemy,20);
  var first=spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),enemy,4);var cost=((Unit)first).getCost();long total=(long)cost.food+cost.wood+cost.ore;
  h.assertTrue(total>0,"known paid standard unit cost");
  var line=DreamEconomyResearch.Line.CONVERSION;
  var shadow=DreamPassiveEconomy.SHADOW;
  int[] balance={0},remainder={0};
  // The previous kill gets the old rate even if the next rank is granted before its deferred confirmation.
  for(int i=0;i<4;i++){
   final int rank=i;
   at(h,es,2+4*i,()->{
    Mob victim=rank==0?first:spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),enemy,4+2*rank);
    victim.setHealth(1);h.assertTrue(victim.hurt(h.getLevel().damageSources().mobAttack(attacker),1000),"actual fatal native attack at rank "+rank);
    if(rank<3)ResearchServerEvents.addResearch(dream,line.id(rank+1));
    at(h,es,4+4*rank,()->{
     var expected=DreamResearchMath.convert(total,500+500*rank,remainder[0],balance[0],DreamPassiveEconomy.SHADOW_CAP);
     balance[0]+=expected.amount();remainder[0]=expected.remainder();
     h.assertTrue(RtsKillValueClaims.isClaimed(victim)&&r.extra.getOrDefault(shadow,0)==balance[0]
       &&r.conversionRemainders.getOrDefault(DreamShadowKillIncome.REMAINDER,0L)==remainder[0],
       "rank "+rank+" uses exact death-time conversion: expected "+balance[0]+" / "+remainder[0]);
    });
   });
  }
  at(h,es,19,()->{
   h.assertTrue(r.food==10000&&r.wood==10000&&r.ore==10000,"shadow never becomes ordinary resources");
   r.extra.put(shadow,1500);r.conversionRemainders.put(DreamShadowKillIncome.REMAINDER,9500L);
   var capped=spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),enemy,12);capped.setHealth(1);capped.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
  });
  at(h,es,23,()->{h.assertTrue(r.extra.get(shadow)==1500&&r.conversionRemainders.get(DreamShadowKillIncome.REMAINDER)==0,"above-cap refund balance preserved, income fraction cleared");cleanup(es);h.succeed();});
 }
 @GameTest(templateNamespace="dreamseekers_research_economy",template="ore_mine_test_arena",timeoutTicks=80)
 public static void ownerWideAttributesCaptureAndEntityRoundtrip(GameTestHelper h){
  String owner=PREFIX+"Stats",other=PREFIX+"Other";var es=new Fixture(owner,other);account(owner,DreamseekersMod.DREAMSEEKERS);account(other,Factions.VILLAGERS);
  var unit=spawn(h,es,EntityRegistrar.ZOMBIE_UNIT.get(),owner,3);var worker=spawn(h,es,DreamseekersMod.FIREFLY_WORKER.get(),owner,5);
  double hp=unit.getMaxHealth(),ad=unit.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get());unit.setHealth((float)(hp*.4));
  ResearchServerEvents.addResearch(owner,DreamEconomyResearch.ASCENDANCY_ID);
  at(h,es,3,()->{
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp*1.15)<.001&&Math.abs(unit.getHealth()/unit.getMaxHealth()-.4)<.001,"stable maxHP and damage ratio applied by real livingtick");
   h.assertTrue(Math.abs(unit.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get())-ad*1.15)<.001,"ordinary RoN AD boosted once");
   if(worker.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getBaseValue()==0)h.assertTrue(worker.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getModifier(DreamAscendancyAttributes.DAMAGE_ID)==null,"noncombat zeroAD stays zero");
   for(int i=0;i<20;i++)DreamAscendancyAttributes.reconcile(unit,true);
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp*1.15)<.001,"repeated reconciliation does not stack");
   ResearchServerEvents.addResearch(owner,DreamEconomyResearch.Line.ASCENDANCY.id(2));
  });
  at(h,es,5,()->{
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp*1.30)<.001&&Math.abs(unit.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get())-ad*1.30)<.001,
      "second rank replaces +15% with +30%; no compound or second independent modifier");
   ResearchServerEvents.addResearch(owner,DreamEconomyResearch.Line.ASCENDANCY.id(3));
  });
  at(h,es,7,()->{
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp*1.45)<.001&&Math.abs(unit.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get())-ad*1.45)<.001,
      "third rank aggregates to a single +45% modifier");
   for(int i=0;i<20;i++)DreamAscendancyAttributes.reconcile(unit,DreamAscendancyAttributes.rankFor(unit));
   CompoundTag nbt=new CompoundTag();unit.saveWithoutId(nbt);unit.load(nbt);
   h.assertTrue(Math.abs(unit.getHealth()/unit.getMaxHealth()-.4)<.001&&Math.abs(unit.getMaxHealth()-hp*1.45)<.001,
      "replayed reconciliation and real entityNBT roundtrip maintain the single capped bonus and damage ratio");
   ((Unit)unit).setOwnerName(other);
  });
  at(h,es,10,()->{
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp)<.001&&Math.abs(unit.getHealth()/unit.getMaxHealth()-.4)<.001,"captured-away unit loses only own bonus preservingratio");
   ((Unit)unit).setOwnerName(owner);
  });
  at(h,es,13,()->{
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp*1.45)<.001,"captured-in native unit gains all owner ranks, not just rank one");
   var fresh=spawn(h,es,EntityRegistrar.ZOMBIE_UNIT.get(),owner,7);
   at(h,es,17,()->{h.assertTrue(Math.abs(fresh.getMaxHealth()-hp*1.45)<.001,"new unit reconciles to the owner's full earned rank");
    fresh.setHealth(0);DreamAscendancyAttributes.reconcile(fresh,false);h.assertTrue(fresh.getHealth()==0,"modifier removal never resurrects");cleanup(es);h.succeed();});
  });
 }
}
