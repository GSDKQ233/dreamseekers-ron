package com.dreamseekers;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.dreamseekers.summon.BoundedSummons;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("dreamseekers_summons")
@PrefixGameTestTemplate(false)
public final class BoundedSummonGameTests {
 private static final String BATCH="dreamseekers_summons";
 private static final List<F> FIXTURES=new ArrayList<>();
 private static final class F implements AutoCloseable {
  final GameTestHelper h; final String owner;final Resources account;final PassiveIncomePeer peer;
  final List<Mob> sources=new ArrayList<>();final List<Consumer<EntityJoinLevelEvent>> guards=new ArrayList<>();
  final BlockPos center;
  F(GameTestHelper h,String suffix){
   this.h=h;owner="Summon_"+suffix;
   PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
   account=new Resources(owner,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);
   ResearchServerEvents.addCheat(owner,"foodforthought");peer=new PassiveIncomePeer(h.getLevel(),owner);
   var p=h.absolutePos(new BlockPos(15,2,15));center=new BlockPos(p.getX(),100,p.getZ());
   for(BlockPos at:BlockPos.betweenClosed(center.offset(-9,-1,-9),center.offset(9,5,9)))h.getLevel().setBlock(at,at.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
   FIXTURES.add(this);
  }
  SkillUnit source(boolean mother,int x){
   Mob body=mother?DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel()):DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get().create(h.getLevel());
   ((Unit)body).setOwnerName(owner);body.setNoAi(true);body.moveTo(center.getX()+x+.5,100,center.getZ()+.5);
   h.assertTrue(h.getLevel().addFreshEntity(body),"real caster publication");sources.add(body);return (SkillUnit)body;
  }
  SkillRequest request(SkillUnit source){var skill=SkillService.skillFor(source);return new SkillRequest(skill,source.mob().getId(),source.mob().getUUID(),source.rts().skills().revision(),source.mob().getId(),source.mob().getUUID());}
  SkillResult cast(SkillUnit source){return SkillService.request(peer.actor,request(source));}
  CompoundTag ledger(){var tag=new CompoundTag();tag.putInt("food",account.food);tag.putInt("wood",account.wood);tag.putInt("ore",account.ore);tag.putInt("emerald",account.emerald);account.writeExtended(tag);return tag;}
  List<Mob> children(){return UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Mob&&e instanceof Unit u&&u.getOwnerName().equals(owner)&&BoundedSummons.isTemporary(e)).map(e->(Mob)e).toList();}
  void guard(Consumer<EntityJoinLevelEvent> consumer){guards.add(consumer);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,consumer);}
  void at(int tick,Runnable run){h.runAtTickTime(tick,()->{try{run.run();}catch(RuntimeException|Error e){close();throw e;}});}
  @Override public void close(){
   guards.forEach(MinecraftForge.EVENT_BUS::unregister);guards.clear();
   new ArrayList<>(UnitServerEvents.getAllUnits()).stream().filter(e->e instanceof Unit u&&u.getOwnerName().equals(owner)).forEach(Entity::discard);
   ResearchServerEvents.removeCheat(owner,"foodforthought");PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r==account);peer.close();FIXTURES.remove(this);
  }
 }
 @AfterBatch(batch=BATCH) public static void cleanup(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void zeroShadowMotherReplayPopulationAndCaptureCleanup(GameTestHelper h){
  F f=new F(h,"mother");SkillUnit source=f.source(true,0);int pop=UnitServerEvents.getCurrentPopulation(f.owner);SkillRequest old=f.request(source);
  f.account.extra.put(DreamPassiveEconomy.SHADOW,0);var before=f.ledger();
  h.assertTrue(SkillService.request(f.peer.actor,old)==SkillResult.SUCCESS,"real authenticated mother summon at zero shadow");
  h.assertTrue(f.children().size()==2&&BoundedSummons.activeCount(f.owner)==2,"exactly two live leases");
  h.assertTrue(f.ledger().equals(before)&&source.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==900,"cooldown starts without any resource or ledger revision change");
  h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==pop+4,"two beetles immediately consume native population");
  h.assertTrue(f.children().stream().allMatch(RtsKillValueClaims::isFree),"all temporary births permanently ineligible for kill value");
  h.assertTrue(SkillService.request(f.peer.actor,old)==SkillResult.REPLAY&&f.cast(source)==SkillResult.COOLDOWN
      &&f.children().size()==2&&f.ledger().equals(before),"replay and fresh duplicate cannot add children or mutate the ledger");
  var child=f.children().get(0);((Unit)child).setOwnerName("Summon_other");((Unit)child).setOwnerName(f.owner);
  f.at(4,()->{h.assertTrue(child.isRemoved(),"same-tick child capture ABA invalidates lease");
   source.setOwnerName("Summon_other");source.setOwnerName(f.owner);
  });
  f.at(8,()->{h.assertTrue(f.children().isEmpty()&&BoundedSummons.activeCount(f.owner)==0&&UnitServerEvents.getCurrentPopulation(f.owner)==pop,"caster capture ABA clears the remaining leases and population");h.assertTrue(f.account.extra.get(DreamPassiveEconomy.SHADOW)==0,"capture does not credit unspent shadow");f.close();h.succeed();});
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void secondBirthCancellationRollsBackEveryBodyAndNoCost(GameTestHelper h){
  F f=new F(h,"cancel");var source=f.source(true,0);int pop=UnitServerEvents.getCurrentPopulation(f.owner);int[] births={0};
  f.guard(e->{if(!e.getLevel().isClientSide()&&BoundedSummons.isTemporary(e.getEntity())&&e.getEntity() instanceof Unit u&&u.getOwnerName().equals(f.owner)&&++births[0]==2)e.setCanceled(true);});
  h.assertTrue(f.cast(source)==SkillResult.SPAWN_FAILED,"second real join veto fails whole group");
  h.assertTrue(f.children().isEmpty()&&UnitServerEvents.getCurrentPopulation(f.owner)==pop,"published first and late-cancelled second removed from real roster");
  h.assertTrue(f.account.extra.get(DreamPassiveEconomy.SHADOW)==500&&source.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==0,"zero cost and cooldown on partial birth failure");
  f.close();h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void weepingEchoesNeedsNoShadowEntryAndReentrantJoinCannotReserveMoreChildren(GameTestHelper h){
  F f=new F(h,"reenter");var source=f.source(false,0);var other=f.source(true,6);int[] attempts={0};
  f.account.extra.remove(DreamPassiveEconomy.SHADOW);var before=f.ledger();
  f.guard(e->{if(!e.getLevel().isClientSide()&&BoundedSummons.isTemporary(e.getEntity())&&e.getEntity() instanceof Unit u&&u.getOwnerName().equals(f.owner)){
   attempts[0]++;h.assertTrue(f.cast(other)==SkillResult.ALREADY_ACTIVE,"owner construction lock rejects reentrant other caster");
  }});
  h.assertTrue(f.cast(source)==SkillResult.SUCCESS&&attempts[0]==2,"two actual join callbacks exercised");
  h.assertTrue(f.children().size()==2&&source.rts().skills().cooldownTicks(UnitSkill.WEEPING_ECHOES)==900
      &&!f.account.extra.containsKey(DreamPassiveEconomy.SHADOW)&&f.ledger().equals(before),"one ghost pair with a full cooldown, without creating a shadow balance or touching the ledger");
  h.assertTrue(other.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==0,"reentrant rejected caster remains ready");f.close();h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void zeroShadowStillRequiresPopulationAndALiveAccount(GameTestHelper h){
  F f=new F(h,"gates");var source=f.source(true,0);int pop=UnitServerEvents.getCurrentPopulation(f.owner);
  f.account.extra.remove(DreamPassiveEconomy.SHADOW);var before=f.ledger();
  ResourcesServerEvents.resourcesList.remove(f.account);
  try{h.assertTrue(f.cast(source)==SkillResult.NOT_OWNER&&f.children().isEmpty(),"free summons still require a live match account");}
  finally{ResourcesServerEvents.resourcesList.add(f.account);}
  ResearchServerEvents.removeCheat(f.owner,"foodforthought");
  h.assertTrue(f.cast(source)==SkillResult.CAP_REACHED&&f.children().isEmpty()&&UnitServerEvents.getCurrentPopulation(f.owner)==pop,"no supply rejects whole group");
  h.assertTrue(f.ledger().equals(before)&&source.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==0,"failed admissions leave ledger and cooldown untouched");
  ResearchServerEvents.addCheat(f.owner,"foodforthought");int[] joins={0};
  f.guard(e->{if(BoundedSummons.isTemporary(e.getEntity())&&e.getEntity() instanceof Unit u&&f.owner.equals(u.getOwnerName())&&++joins[0]==2)f.account.resetExtendedAccount();});
  h.assertTrue(f.cast(source)==SkillResult.SPAWN_FAILED&&joins[0]==2,"a match reset during the second real join invalidates the old reservation");
  h.assertTrue(f.children().isEmpty()&&UnitServerEvents.getCurrentPopulation(f.owner)==pop&&source.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==0,"no partial pair, stale cooldown or leaked population after account replacement");
  f.close();h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void markedSavedBodyCannotPublishAndOrdinaryBodyUnaffected(GameTestHelper h){
  F f=new F(h,"save");var source=f.source(false,0);h.assertTrue(f.cast(source)==SkillResult.SUCCESS,"committed ghost group");
  var original=f.children().get(0);CompoundTag tag=new CompoundTag();original.saveWithoutId(tag);original.discard();
  Mob loaded=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(h.getLevel());loaded.load(tag);
  h.assertTrue(BoundedSummons.isTemporary(loaded)&&!h.getLevel().addFreshEntity(loaded),"saved temporary body rejected without a runtime birth reservation");
  UnitServerEvents.rollbackRejectedUnitSpawn(loaded);
  Mob ordinary=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(h.getLevel());((Unit)ordinary).setOwnerName(f.owner);ordinary.moveTo(f.center.getX()+7,100,f.center.getZ()+7);ordinary.setNoAi(true);
  h.assertTrue(h.getLevel().addFreshEntity(ordinary)&&!BoundedSummons.isTemporary(ordinary),"ordinary same-type paid unit is not removed");f.close();h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=660)
 public static void realSixHundredTickLifetimeNeverAwardsShadow(GameTestHelper h){
  F f=new F(h,"life");var source=f.source(true,0);h.assertTrue(f.cast(source)==SkillResult.SUCCESS,"mother group before realtimer");
  var born=List.copyOf(f.children());born.forEach(b->b.setNoAi(true));
  f.at(590,()->h.assertTrue(born.stream().allMatch(Entity::isAlive)&&BoundedSummons.activeCount(f.owner)==2,"no premature expiration before600ticks"));
  f.at(610,()->{h.assertTrue(born.stream().allMatch(Entity::isRemoved)&&BoundedSummons.activeCount(f.owner)==0,"both children expire after actual600server ticks");h.assertTrue(f.account.extra.get(DreamPassiveEconomy.SHADOW)==500,"expiration does not create shadow");f.close();h.succeed();});
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void ownerCapAndPendingChildCaptureNeverGrantFreeSoldiers(GameTestHelper h){
  F f=new F(h,"cap");var first=f.source(true,-5);var second=f.source(false,5);var third=f.source(true,0);
  h.assertTrue(f.cast(first)==SkillResult.SUCCESS&&f.cast(second)==SkillResult.SUCCESS,"two caster groups share four slots");
  h.assertTrue(f.cast(third)==SkillResult.CAP_REACHED&&BoundedSummons.activeCount(f.owner)==4&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==500,"third group rejected at owner cap; first two free groups never debited existing shadow");
  f.close();h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void childCapturedDuringJoinRollsBackAndBuildingGateRejectsRevokedLease(GameTestHelper h){
  F f=new F(h,"capture");var source=f.source(true,0);int baseline=UnitServerEvents.getCurrentPopulation(f.owner);List<Entity> attempted=new ArrayList<>();
  f.guard(e->{if(!e.getLevel().isClientSide()&&BoundedSummons.isTemporary(e.getEntity())&&e.getEntity() instanceof Unit u&&u.getOwnerName().equals(f.owner)){attempted.add(e.getEntity());u.setOwnerName("CapturedSummon");u.setOwnerName(f.owner);}});
  h.assertTrue(f.cast(source)==SkillResult.SPAWN_FAILED&&attempted.size()==1,"join capture ABA rejected before second birth");
  h.assertTrue(attempted.stream().allMatch(Entity::isRemoved)&&UnitServerEvents.getCurrentPopulation(f.owner)==baseline&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==500,"exact attempted object removed, no population orcost leak");
  f.guards.forEach(MinecraftForge.EVENT_BUS::unregister);f.guards.clear();
  h.assertTrue(f.cast(source)==SkillResult.SUCCESS,"fresh summon after failed transaction remains usable");
  var child=f.children().get(0);h.assertTrue(BoundedSummons.canAct(child),"live child can act");((Unit)child).setOwnerName("CapturedSummon");((Unit)child).setOwnerName(f.owner);
  h.assertTrue(!BoundedSummons.canAct(child),"same-stack ownerABA denies living AND building attack before END cleanup");
  var goal=(com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal)((com.solegendary.reignofnether.unit.interfaces.AttackerUnit)child).getAttackBuildingGoal();
  goal.setBuildingTarget(f.center);h.assertTrue(goal.getBuildingTarget()==null&&!goal.canUse(),"real summon-specific building goal refuses revoked source");
  f.at(3,()->{h.assertTrue(child.isRemoved(),"deferred physical cleanup removes revoked body");f.close();h.succeed();});
 }

 private static final class BreakProbe extends com.solegendary.reignofnether.building.BuildingPlacement {
  int callbacks;Runnable hook;
  BreakProbe(net.minecraft.server.level.ServerLevel level,BlockPos pos,ArrayList<com.solegendary.reignofnether.building.BuildingBlock> blocks){
   super(DreamseekersMod.BEETLE_SANCTUARY,level,pos,net.minecraft.world.level.block.Rotation.NONE,"SummonTarget",blocks,false);
  }
  @Override public void onBlockBreak(net.minecraft.server.level.ServerLevel level,BlockPos pos,boolean physical){
   callbacks++;super.onBlockBreak(level,pos,physical);if(hook!=null){var action=hook;hook=null;action.run();}
  }
 }
 private static void interruptedBuildingBudget(GameTestHelper h,boolean motherDeath){
  F f=new F(h,motherDeath?"breakdeath":"breakcapture");var source=f.source(motherDeath,0);
  h.assertTrue(f.cast(source)==SkillResult.SUCCESS,"real summoned children for building callback boundary");var child=f.children().get(0);
  var blocks=new ArrayList<com.solegendary.reignofnether.building.BuildingBlock>();var origin=f.center.offset(0,1,7);
  for(int x=0;x<5;x++)for(int z=0;z<4;z++){var pos=origin.offset(x,0,z);h.getLevel().setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),2);blocks.add(new com.solegendary.reignofnether.building.BuildingBlock(pos,Blocks.STONE_BRICKS.defaultBlockState()));}
  var target=new BreakProbe(h.getLevel(),origin,blocks);com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().add(target);target.onBuilt();
  try {
   child.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(1000);
   target.hook=()->{if(motherDeath)source.mob().setHealth(0);else {((Unit)child).setOwnerName("SummonCaptured");((Unit)child).setOwnerName(f.owner);}};
   h.assertTrue(BoundedSummons.attackBuilding(child,target),"first physical block budget accepted");
   h.assertTrue(target.callbacks==1&&blocks.stream().filter(b->!b.isPlaced(h.getLevel())).count()==1,"first break callback completes, source revocation prevents every subsequent block in same budget");
   h.assertTrue(!BoundedSummons.attackBuilding(child,target)&&target.callbacks==1,"same-stack new damage also rejected before END cleanup");
  } finally {com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(target);f.close();}
  h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void motherDeathInsideFirstBlockStopsRemainingBudget(GameTestHelper h){interruptedBuildingBudget(h,true);}
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void ghostCaptureInsideFirstBlockStopsRemainingBudget(GameTestHelper h){interruptedBuildingBudget(h,false);}

 @SuppressWarnings("unchecked") private static java.util.Map<Integer,?> unitTickets(){
  try{var field=UnitServerEvents.class.getDeclaredField("forcedUnitChunks");field.setAccessible(true);return (java.util.Map<Integer,?>)field.get(null);}
  catch(ReflectiveOperationException e){throw new RuntimeException(e);}
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void repeatedTemporaryGroupsReleaseExactNativeChunkTickets(GameTestHelper h){
  F f=new F(h,"tickets");var first=f.source(true,-5);var second=f.source(true,5);
  h.assertTrue(f.cast(first)==SkillResult.SUCCESS,"first group");var originals=List.copyOf(f.children());
  h.assertTrue(originals.stream().allMatch(b->unitTickets().containsKey(b.getId())),"real native forced-ticket entries created");
  originals.forEach(Entity::discard);
  h.assertTrue(originals.stream().noneMatch(b->unitTickets().containsKey(b.getId())),"leave event releases each exact original ticket immediately");
  f.at(3,()->{
   h.assertTrue(f.cast(second)==SkillResult.SUCCESS,"second group after retirement");var next=List.copyOf(f.children());
   h.assertTrue(next.size()==2&&next.stream().allMatch(b->unitTickets().containsKey(b.getId())),"second real native tickets");
   second.setOwnerName("TicketOther");second.setOwnerName(f.owner);
   f.at(6,()->{h.assertTrue(next.stream().allMatch(Entity::isRemoved)&&next.stream().noneMatch(b->unitTickets().containsKey(b.getId())),"automatic group retirement releases exact ticket entries too");
    h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==8,"only both permanent casters remain in nativepopulation");f.close();h.succeed();});
  });
 }

 private static void finalDamageRevocation(GameTestHelper h,boolean parentDies){
  F f=new F(h,parentDies?"lateparent":"latechild");var source=f.source(parentDies,0);
  h.assertTrue(f.cast(source)==SkillResult.SUCCESS,"real summoned children before late Forge callback");var child=f.children().get(0);
  String enemy=f.owner+"Enemy";PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(enemy,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(enemy,0,0,0));
  var victim=com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());victim.setOwnerName(enemy);victim.moveTo(f.center.getX()+7,100,f.center.getZ()+7);victim.setNoAi(true);h.assertTrue(h.getLevel().addFreshEntity(victim),"real enemyvictim");
  var control=com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());control.setOwnerName(enemy);control.moveTo(f.center.getX()+7,100,f.center.getZ()-7);control.setNoAi(true);h.assertTrue(h.getLevel().addFreshEntity(control),"separate real positivecontrol");
  int[] callbacks={0};Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> hook=e->{
   if(e.getEntity()==victim&&e.getSource().getEntity()==child){callbacks[0]++;if(parentDies)source.mob().setHealth(0);else {((Unit)child).setOwnerName(enemy);((Unit)child).setOwnerName(f.owner);}}
  };
  MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,hook);
  try {
   var damage=parentDies?h.getLevel().damageSources().mobAttack(child):h.getLevel().damageSources().indirectMagic(child,child);
   float controlHp=control.getHealth();control.hurt(damage,5);
   h.assertTrue(control.getHealth()<controlHp,"active temporary child does realHP damage before revocation");
   float hp=victim.getHealth();victim.hurt(damage,5);
   h.assertTrue(callbacks[0]==1&&victim.getHealth()==hp,"late LivingDamage callback revokes authority before finalHP write");
  } finally {MinecraftForge.EVENT_BUS.unregister(hook);victim.discard();control.discard();PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(enemy));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(enemy));f.close();}
  h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void finalDamageListenerKillingMotherPreventsHealthWrite(GameTestHelper h){finalDamageRevocation(h,true);}
 @GameTest(templateNamespace="dreamseekers_summons",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
 public static void finalDamageListenerCapturingGhostPreventsHealthWrite(GameTestHelper h){finalDamageRevocation(h,false);}

}
