package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieVillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Single genuine native infection reproduction; does not call an economic test observer. */
@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class FreeInfectionEconomicAuditGameTests {
 private static Resources account(String name){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.MONSTERS));var r=new Resources(name,1000,1000,1000);ResourcesServerEvents.resourcesList.add(r);return r;}
 private static <T extends Mob> T spawn(GameTestHelper h,List<Entity> es,EntityType<T> type,String name,int x){T e=type.create(h.getLevel());((Unit)e).setOwnerName(name);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,3));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"genuine native RTS spawn");es.add(e);return e;}
 @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_free_infection",timeoutTicks=40)
 public static void nativeInfectionChildMustNotMintAnotherCost(GameTestHelper h){
  String victimOwner="AuditInfVict",infectorOwner="AuditInfOwn",killerOwner="AuditInfKill";Set<String> owners=Set.of(victimOwner,infectorOwner,killerOwner);
  account(victimOwner);account(infectorOwner);Resources ledger=account(killerOwner);ResearchServerEvents.addResearch(killerOwner,CoreEconomyResearch.id("research_global_salvage"));List<Entity> es=new ArrayList<>();
  Runnable cleanup=()->{for(String name:owners)ResearchServerEvents.removeAllResearchFor(name);PlayerServerEvents.rtsPlayers.removeIf(p->owners.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->owners.contains(r.ownerName));UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Unit u&&owners.contains(u.getOwnerName())).toList().forEach(Entity::discard);es.forEach(Entity::discard);};
  try{
   var infector=spawn(h,es,EntityRegistrar.DROWNED_UNIT.get(),infectorOwner,3);var victim=spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),victimOwner,4);spawn(h,es,EntityRegistrar.VILLAGER_UNIT.get(),victimOwner,20);
   var attacker=spawn(h,es,EntityRegistrar.ZOMBIE_UNIT.get(),killerOwner,9);
   victim.setHealth(1);h.assertTrue(infector.doHurtTarget(victim)&&victim.isDeadOrDying(),"actual Drowned ordinary attack causes infection death");
   var children=UnitServerEvents.getAllUnits().stream().filter(e->e instanceof ZombieVillagerUnit z&&z.getOwnerName().equals(infectorOwner)).toList();
   h.assertTrue(children.size()==1,"native death listener created exactly one free infected RTS child, no test-created replacement");
   LivingEntity child=children.get(0);es.add(child);((Mob)child).setNoAi(true);ResourceCost cost=((Unit)child).getCost();
   h.assertTrue(cost.food>0,"infection child has configured normal unit cost");int before=ledger.food;
   System.out.println("REAUDIT_FREE_INFECTION child="+child.getType()+" F="+cost.food+" parentDead="+victim.isDeadOrDying());
   child.setHealth(1);h.assertTrue(child.hurt(h.getLevel().damageSources().mobAttack(attacker),1000),"real enemy fatal attack on native free infection child");
   h.runAtTickTime(5,()->{boolean claimed=RtsKillValueClaims.isClaimed(child);int delta=ledger.food-before;System.out.println("REAUDIT_FREE_INFECTION_KILL claimed="+claimed+" foodDelta="+delta+" remainder="+ledger.conversionRemainders);cleanup.run();h.assertTrue(!claimed&&delta==0,"native free infection offspring must not yield a second standard-cost economic reward");h.succeed();});
  }catch(RuntimeException|Error e){cleanup.run();throw e;}
 }
}
