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
  h.assertTrue(total>0,"known paid standard unit cost");var expected=DreamResearchMath.convert(total,500,0,0,1000);
  first.setHealth(1);h.assertTrue(first.hurt(h.getLevel().damageSources().mobAttack(attacker),1000),"actual fatal native attack");
  ResearchServerEvents.addResearch(dream,DreamEconomyResearch.CONVERSION_ID);
  at(h,es,4,()->{
   h.assertTrue(RtsKillValueClaims.isClaimed(first),"global addon listener claims actual victim");
   h.assertTrue(r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==expected.amount()&&r.conversionRemainders.getOrDefault(DreamShadowKillIncome.REMAINDER,0L)==expected.remainder(),"death-time 5% snapshot, not later researched10%");
   var second=spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),enemy,6);second.setHealth(1);second.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
  });
  at(h,es,8,()->{
   var expected2=DreamResearchMath.convert(total,1000,expected.remainder(),expected.amount(),1000);
   h.assertTrue(r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==expected.amount()+expected2.amount(),"researched actual kill10%");
   h.assertTrue(r.food==10000&&r.wood==10000&&r.ore==10000,"shadow never becomes ordinary resources");
   r.extra.put(DreamPassiveEconomy.SHADOW,1500);r.conversionRemainders.put(DreamShadowKillIncome.REMAINDER,9500L);
   var capped=spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),enemy,8);capped.setHealth(1);capped.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
  });
  at(h,es,12,()->{h.assertTrue(r.extra.get(DreamPassiveEconomy.SHADOW)==1500&&r.conversionRemainders.get(DreamShadowKillIncome.REMAINDER)==0,"above-cap refund balance preserved, income fraction cleared");cleanup(es);h.succeed();});
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
   CompoundTag nbt=new CompoundTag();unit.saveWithoutId(nbt);unit.load(nbt);
   h.assertTrue(Math.abs(unit.getHealth()/unit.getMaxHealth()-.4)<.001,"real entityNBT roundtrip preserves boosted damage ratio");
   ((Unit)unit).setOwnerName(other);
  });
  at(h,es,6,()->{
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp)<.001&&Math.abs(unit.getHealth()/unit.getMaxHealth()-.4)<.001,"captured-away unit loses only own bonus preservingratio");
   ((Unit)unit).setOwnerName(owner);
  });
  at(h,es,9,()->{
   h.assertTrue(Math.abs(unit.getMaxHealth()-hp*1.15)<.001,"captured-in native unit gainsownerresearch");
   var fresh=spawn(h,es,EntityRegistrar.ZOMBIE_UNIT.get(),owner,7);
   at(h,es,13,()->{h.assertTrue(fresh.getAttribute(Attributes.MAX_HEALTH).getModifier(DreamAscendancyAttributes.HEALTH_ID)!=null,"laternewunit gainsresearch");
    fresh.setHealth(0);DreamAscendancyAttributes.reconcile(fresh,false);h.assertTrue(fresh.getHealth()==0,"modifierremoval neverresurrects");cleanup(es);h.succeed();});
  });
 }
}
