package com.dreamseekers;
import static com.dreamseekers.ShadowGolemStrikeSkillGameTests.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Reuses original real RTS fixtures/authorized packet helpers, never injects a skill lease or Impact. */
@GameTestHolder("dreamseekers_economic_skill_kills") @PrefixGameTestTemplate(false)
public final class ActiveSkillEconomicGameTests {
 static final List<ActiveSkillEconomicSubscriber> PROBES=new ArrayList<>();
 static void clean(ServerLevel level){for(ActiveSkillEconomicSubscriber p:PROBES)MinecraftForge.EVENT_BUS.unregister(p);PROBES.clear();ShadowGolemStrikeSkillGameTests.cleanup(level);}
 @AfterBatch(batch="economic_skill_golem") public static void cleanGolem(ServerLevel l){clean(l);}
 @AfterBatch(batch="economic_skill_stone") public static void cleanStone(ServerLevel l){clean(l);}
 @AfterBatch(batch="economic_skill_squeal") public static void cleanSqueal(ServerLevel l){clean(l);}
 @AfterBatch(batch="economic_skill_ash") public static void cleanAsh(ServerLevel l){clean(l);}
 @AfterBatch(batch="economic_skill_debt") public static void cleanDebt(ServerLevel l){clean(l);}
 static final class ActiveSkillEconomicSubscriber {
  final GameTestHelper h;final Set<LivingEntity> watched=new HashSet<>(),cancel=new HashSet<>(),cancelSettlement=new HashSet<>();final Map<UUID,Integer> credits=new HashMap<>();final Map<UUID,DamageSource> fatal=new HashMap<>();final Map<UUID,List<String>> frames=new HashMap<>();
  ActiveSkillEconomicSubscriber(GameTestHelper h){this.h=h;PROBES.add(this);MinecraftForge.EVENT_BUS.register(this);}
  void watch(LivingEntity... bodies){for(LivingEntity body:bodies){var cost=((Unit)body).getCost();h.assertTrue(cost!=null&&(long)cost.food+cost.wood+cost.ore>0,"actual paid RTS victim configured cost");watched.add(body);}}
  @SubscribeEvent(priority=EventPriority.LOWEST) public void cancel(LivingDamageEvent e){if(cancel.contains(e.getEntity())||(cancelSettlement.contains(e.getEntity())&&FriendlyDebtContract.isSettlement(e.getSource())))e.setCanceled(true);}
  @SubscribeEvent public void death(LivingDeathEvent e){if(watched.contains(e.getEntity())){fatal.put(e.getEntity().getUUID(),e.getSource());frames.put(e.getEntity().getUUID(),Arrays.stream(Thread.currentThread().getStackTrace()).map(StackTraceElement::getClassName).toList());}}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void credit(ConfirmedRtsKillEvent e){if(watched.stream().anyMatch(v->v.getUUID().equals(e.snapshot().victimId()))&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))credits.merge(e.snapshot().victimId(),1,Integer::sum);}
  int count(LivingEntity e){return credits.getOrDefault(e.getUUID(),0);}
  void one(LivingEntity body,String skill){h.assertTrue(body.isDeadOrDying()&&count(body)==1&&RtsKillValueClaims.isClaimed(body),"one actual fatal "+skill+" hit / one permanent value");h.assertTrue(frames.getOrDefault(body.getUUID(),List.of()).contains("com.dreamseekers.skill."+skill),"death was inside actual "+skill+" gameplay call, not helper-only damage");}
  void zero(LivingEntity body){h.assertTrue(body.isAlive()&&count(body)==0&&!RtsKillValueClaims.isClaimed(body),"real canceled skill never produces a death value");}
 }
 static <T extends Mob>T body(GameTestHelper h,EntityType<T> type,String owner,double x,double z){T e=type.create(h.getLevel());owned(h,e,owner,x,z);return e;}
 static SkillResult castSkill(GameTestHelper h,SkillUnit source,LivingEntity target,UnitSkill skill){return new SkillNetwork.Cast(new SkillRequest(skill,source.mob().getId(),source.mob().getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(h,source.getOwnerName()));}
 static Resources ledger(String owner){return ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();}
 static void paid(GameTestHelper h,SkillUnit source,int count){Resources r=ledger(source.getOwnerName());h.assertTrue(r.ore==345+count&&r.food==123&&r.wood==234&&r.extra.getOrDefault(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dreamseekers","shadow_energy"),0)==0,"fixture consumer pays exactly once; faction consumer cannot double-pay");}
 @GameTest(templateNamespace="dreamseekers_economic_skill_kills",template="unit_isolation_arena",batch="economic_skill_golem",timeoutTicks=150)
 public static void golemActualReleaseFatalAndFinalCancellation(GameTestHelper h){
  room(h);var s=golem(h,"EconomicGolem",5.5,8.5);var t=target(h,"EconomicGolemEnemy",8,8.5);var c=golem(h,"EconomicGolemCancel",21.5,8.5);var ct=target(h,"EconomicGolemCancelEnemy",24,8.5);var reserve=target(h,"EconomicGolemEnemy",15,25);var reserve2=target(h,"EconomicGolemCancelEnemy",20,25);ActiveSkillEconomicSubscriber p=new ActiveSkillEconomicSubscriber(h);p.watch(t,ct);p.cancel.add(ct);
  ready(h,s,t,c,ct,reserve,reserve2).thenExecute(()->{t.setHealth(1);ct.setHealth(1);h.assertTrue(castSkill(h,s,t,UnitSkill.SHADOW_GOLEM_STRIKE)==SkillResult.SUCCESS&&castSkill(h,c,ct,UnitSkill.SHADOW_GOLEM_STRIKE)==SkillResult.SUCCESS,"two genuine authenticated twenty-tick strikes");}).thenIdle(24).thenExecute(()->{p.one(t,"ShadowGolemStrike");p.zero(ct);paid(h,s,1);paid(h,c,0);System.out.println("GOLEM_FATAL_ECONOMIC_RUNTIME_PASS actual_release canceled_final");}).thenSucceed();
 }
 @GameTest(templateNamespace="dreamseekers_economic_skill_kills",template="unit_isolation_arena",batch="economic_skill_stone",timeoutTicks=170)
 public static void heavyActualPhysicalPrimaryAndSecondaryFatal(GameTestHelper h){projectileSkill(h,true);}
 @GameTest(templateNamespace="dreamseekers_economic_skill_kills",template="unit_isolation_arena",batch="economic_skill_squeal",timeoutTicks=170)
 public static void suppressionActualPhysicalPrimaryAndSecondaryFatal(GameTestHelper h){projectileSkill(h,false);}
 static void projectileSkill(GameTestHelper h,boolean stone){
  room(h);SkillUnit source=stone?body(h,DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),"EconomicStone",4.5,8.5):body(h,DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get(),"EconomicWave",4.5,8.5);SkillUnit canceled=stone?body(h,DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),"EconomicStoneCancel",4.5,23.5):body(h,DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get(),"EconomicWaveCancel",4.5,23.5);
  String enemy=stone?"EconomicStoneEnemy":"EconomicWaveEnemy",enemyC=enemy+"Cancel";
  var primary=body(h,DreamseekersMod.JELLYFISH_UNIT.get(),enemy,12.5,8.5);var secondary=body(h,DreamseekersMod.JELLYFISH_UNIT.get(),enemy,11.85,9.4);var primaryC=body(h,DreamseekersMod.JELLYFISH_UNIT.get(),enemyC,12.5,23.5);var secondaryC=body(h,DreamseekersMod.JELLYFISH_UNIT.get(),enemyC,11.85,24.4);var reserve=target(h,enemy,26,12);var reserveC=target(h,enemyC,26,27);ActiveSkillEconomicSubscriber p=new ActiveSkillEconomicSubscriber(h);p.watch(primary,secondary,primaryC,secondaryC);p.cancel.add(primaryC);
  ready(h,source.mob(),canceled.mob(),primary,secondary,primaryC,secondaryC,reserve,reserveC).thenExecute(()->{for(var e:List.of(primary,secondary,primaryC,secondaryC))e.setHealth(1);UnitSkill skill=stone?UnitSkill.STONE_HEAVY_SHOT:UnitSkill.SQUEAL_SUPPRESSION_WAVE;h.assertTrue(castSkill(h,source,primary,skill)==SkillResult.SUCCESS&&castSkill(h,canceled,primaryC,skill)==SkillResult.SUCCESS,"real targeted skill packets publish native skill projectiles");}).thenIdle(65).thenExecute(()->{String skill=stone?"StoneHeavyShot":"SquealSuppressionWave";p.one(primary,skill);p.one(secondary,skill);p.zero(primaryC);p.zero(secondaryC);paid(h,source,2);paid(h,canceled,0);h.assertTrue(p.fatal.get(primary.getUUID()).getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile,"fatal primary truly came from a physical projectile");System.out.println("PROJECTILE_FATAL_ECONOMIC_RUNTIME_PASS "+skill+" primary secondary canceled_receipt_no_splash");}).thenSucceed();
 }
 @GameTest(templateNamespace="dreamseekers_economic_skill_kills",template="unit_isolation_arena",batch="economic_skill_ash",timeoutTicks=210)
 public static void ashActualLandingAndSecondPulseFatalOnlyOnce(GameTestHelper h){
  room(h);var s=body(h,DreamseekersMod.ASH_BONE_WING_UNIT.get(),"EconomicAsh",5.5,8.5);var cs=body(h,DreamseekersMod.ASH_BONE_WING_UNIT.get(),"EconomicAshCancel",5.5,23.5);var t=target(h,"EconomicAshEnemy",12.5,9.8);var ct=target(h,"EconomicAshCancelEnemy",12.5,24.8);var reserve=target(h,"EconomicAshEnemy",25,12);var reserveC=target(h,"EconomicAshCancelEnemy",25,27);ActiveSkillEconomicSubscriber p=new ActiveSkillEconomicSubscriber(h);p.watch(t,ct);p.cancel.add(ct);long[] landed={-1};
  ready(h,s,cs,t,ct,reserve,reserveC).thenExecute(()->{t.setHealth(3);ct.setHealth(3);for(var source:List.of(s,cs)){int z=source==s?8:23;var request=new SkillGroundRequest(UnitSkill.ASH_EMBER_LANDING,source.getId(),source.getUUID(),source.rts().skills().revision(),h.absolutePos(new BlockPos(12,1,z)));h.assertTrue(new SkillNetwork.GroundCast(request).execute(player(h,source.getOwnerName()))==SkillResult.SUCCESS,"real authorized ground projectile");}})
   .thenWaitUntil(()->{var zones=AshEmberLanding.zones(h.getLevel()).stream().filter(z->z.sourceUuid().equals(s.getUUID())||z.sourceUuid().equals(cs.getUUID())).toList();h.assertTrue(zones.size()==2,"both real projectiles landed and created zones");landed[0]=zones.stream().mapToLong(AshEmberLanding.ZoneView::landedAt).max().orElseThrow();})
   .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=landed[0]+21,"first real twenty-tick pulse elapsed"))
   .thenExecute(()->{h.assertTrue(t.isAlive()&&Math.abs(t.getHealth()-1)<.001&&p.count(t)==0,"first fixed two-damage pulse was nonfatal and paid nothing");p.zero(ct);})
   .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=landed[0]+42,"second real pulse and deferred death confirmation elapsed"))
   .thenExecute(()->{p.one(t,"AshEmberLanding");p.zero(ct);paid(h,s,1);paid(h,cs,0);})
   .thenIdle(65).thenExecute(()->{h.assertTrue(p.count(t)==1,"remaining zone pulses never repay same permanent victim");p.zero(ct);paid(h,s,1);System.out.println("ASH_FATAL_ECONOMIC_RUNTIME_PASS actual_ground_landing first_nonfatal second_fatal remaining_pulses_once cancel");}).thenSucceed();
 }
 @GameTest(templateNamespace="dreamseekers_economic_skill_kills",template="unit_isolation_arena",batch="economic_skill_debt",timeoutTicks=190)
 public static void debtActualThreeOrdinaryReceiptsThenIndependentFatalSettlement(GameTestHelper h){
  room(h);var s=body(h,DreamseekersMod.FRIENDLY_GHOST_UNIT.get(),"EconomicDebt",4.5,8.5);var cs=body(h,DreamseekersMod.FRIENDLY_GHOST_UNIT.get(),"EconomicDebtCancel",4.5,23.5);var t=target(h,"EconomicDebtEnemy",10,8.5);var ct=target(h,"EconomicDebtCancelEnemy",10,23.5);var reserve=target(h,"EconomicDebtEnemy",26,10);var reserveC=target(h,"EconomicDebtCancelEnemy",26,26);List<ShadowGolemUnit> a=new ArrayList<>(),b=new ArrayList<>();for(int i=0;i<3;i++){double x=i==0?7.5:i==1?10:12.5,z=i==1?6:8.5;a.add(golem(h,"EconomicDebt",x,z));b.add(golem(h,"EconomicDebtCancel",x,z+15));}ActiveSkillEconomicSubscriber p=new ActiveSkillEconomicSubscriber(h);p.cancelSettlement.add(ct);p.watch(t,ct);
  List<Entity> all=new ArrayList<>(List.of(s,cs,t,ct,reserve,reserveC));all.addAll(a);all.addAll(b);
  var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{t.setHealth(35);ct.setHealth(35);h.assertTrue(castSkill(h,s,t,UnitSkill.FRIENDLY_DEBT_CONTRACT)==SkillResult.SUCCESS&&castSkill(h,cs,ct,UnitSkill.FRIENDLY_DEBT_CONTRACT)==SkillResult.SUCCESS,"real marks before any ordinary contributors");for(var actor:a)order(h,actor,UnitAction.ATTACK,t,BlockPos.ZERO);for(var actor:b)order(h,actor,UnitAction.ATTACK,ct,BlockPos.ZERO);});
  for(int i=0;i<3;i++){final int index=i;seq.thenIdle(12).thenExecute(()->{ordinaryGoal(h,a.get(index),t);ordinaryGoal(h,b.get(index),ct);if(index<2)h.assertTrue(FriendlyDebtContract.contributors(t,s.getOwnerName())==index+1&&p.count(t)==0,"actual original installed melee goal contributed unique UUID without early settlement");});}
  seq.thenIdle(4).thenExecute(()->{p.one(t,"FriendlyDebtContract");h.assertTrue(FriendlyDebtContract.isSettlement(p.fatal.get(t.getUUID()))&&p.fatal.get(t.getUUID()).getEntity()==s,"fatal extra belongs to original contract caster, not third melee contributor");p.zero(ct);h.assertTrue(Math.abs(ct.getHealth()-5)<.001&&FriendlyDebtContract.remainingTicks(ct,cs.getOwnerName())==0,"canceled extra consumed contract, preserved three ordinary hits, no retry");paid(h,s,1);paid(h,cs,0);System.out.println("DEBT_FATAL_ECONOMIC_RUNTIME_PASS three_real_native_melee_receipts independent_settlement canceled_extra");}).thenSucceed();
 }
}
