// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.skill.*;
import com.dreamseekers.shadow.EmberLandingProjectile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;


import com.dreamseekers.scout.*;
import com.solegendary.reignofnether.api.HealthWriteObservers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.nbt.CompoundTag;
/** Eight fixed methods. Direct hurt stimuli are explicitly boundary-only; real melee/projectile/Ember and flight are independent positive paths. */
@GameTestHolder("dreamseekers_light_feather_alert_skill")
@PrefixGameTestTemplate(false)
public final class LightFeatherAlertSkillGameTests {
 private static final String CORE="dreamseekers_light_feather_alert_core",BOUNDARY="dreamseekers_light_feather_alert_boundaries",PREFIX="Feather_";
 private static final List<Entity> ENTITIES=new ArrayList<>();
 private static final Set<String> OWNERS=new LinkedHashSet<>();
 private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
 private static final Set<ServerPlayer> SYNC_MEMBERS=new LinkedHashSet<>();
 private static final Set<ChunkPos> TICKETS=new LinkedHashSet<>();
 private static final List<Object> LISTENERS=new ArrayList<>();
 @AfterBatch(batch=CORE) public static void cleanup(ServerLevel level){
   for(Object p:LISTENERS)MinecraftForge.EVENT_BUS.unregister(p);LISTENERS.clear();
   for(var p:List.copyOf(SYNC_MEMBERS))syncMembership(p,false);
   for(String a:OWNERS)for(String b:OWNERS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
   PlayerServerEvents.players.removeIf(p->OWNERS.contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->OWNERS.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->OWNERS.contains(r.ownerName));
   for(Entity e:List.copyOf(ENTITIES))if(!e.isRemoved()){if(e instanceof ServerPlayer p)p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);else e.discard();}
   for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();ENTITIES.clear();OWNERS.clear();PLAYERS.clear();
 }
 @AfterBatch(batch=BOUNDARY) public static void cleanupBoundaries(ServerLevel level){cleanup(level);}
 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=220)
 public static void naturalMeleeHpWriteTriggersPassiveWithoutTakingScoutMarkOrCommands(GameTestHelper h){
  room(h);var scout=chicken(h,"Melee",8.5,8.5);var enemy=beetle(h,"MeleeEnemy",6.5,8.5);long[] at={-1};float[] before={0};UUID[] mark={null};
  ready(h,scout,enemy).thenExecute(()->{
   var markPacket=new ScoutMarkNetwork.Cast(scout.getId(),scout.getUUID(),enemy.getId(),enemy.getUUID(),scout.scoutMark().revision());
   h.assertTrue(markPacket.execute(player(h,"Melee"))==ScoutMarkState.Result.SUCCESS,"original actual scout mark remains available");mark[0]=scout.scoutMark().targetUuid();
   h.assertTrue(scout.getAbilities().get().equals(List.of(ScoutMarkAbility.INSTANCE))&&!scout.doHurtTarget(enemy)&&!active(scout)&&cd(scout)==0,"passive adds no attack/active ability");
   before[0]=scout.getHealth();order(h,enemy,UnitAction.ATTACK,scout,BlockPos.ZERO);
  }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(enemy.getId())&&enemy.getTargetGoal().getTarget()==scout,"actual owner ATTACK sets exact target"))
  .thenExecute(()->enemy.setNoAi(false))
  .thenWaitUntil(()->h.assertTrue(scout.getHealth()<before[0],"original naturally ticking melee goal must deliver real HP loss"))
  .thenExecute(()->{
   at[0]=h.getLevel().getGameTime();enemy.setNoAi(true);order(h,enemy,UnitAction.STOP,null,BlockPos.ZERO);
   h.assertTrue(active(scout)&&cd(scout)>0&&cd(scout)<=200,"real melee write activated bounded passive");close(h,SkillEffects.bonus(scout,BuffKind.MOVEMENT_SPEED),.20,"one twenty-percent category");
   h.assertTrue(mark[0].equals(scout.scoutMark().targetUuid())&&scout.scoutMark().hasActiveMark()&&scout.getTarget()==null,"incoming hit does not erase lawful mark or create combat target");
   h.assertTrue(frameDepth(scout)==0,"natural hurt finally exits observer frame");bank(h);pass("NATURAL_MELEE_SCOUT_MARK_NONCOMBAT_PASSIVE");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=220)
 public static void originalRangedProjectileAndActualEmberBothReachExactHealthObserver(GameTestHelper h){
  room(h);var ranged=ash(h,"ProjectileEnemy",5.5,6.5);var victim=chicken(h,"Projectile",11.5,6.5);var ember=ash(h,"EmberEnemy",5.5,22.5);var magicVictim=chicken(h,"Ember",12.5,23.8);
  h.assertTrue(ranged.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,240)),"real source invisibility effect, passive must not demand receiver sees attacker");
  List<Projectile> shots=new ArrayList<>();Object joins=new Object(){@SubscribeEvent public void lightFeatherObserveOriginalProjectileJoin(EntityJoinLevelEvent e){if(e.getEntity() instanceof Projectile p&&p.getOwner()==ranged){shots.add(p);ENTITIES.add(p);}}};listen(joins);float[] hp={0,0};long[] born={-1};
  ready(h,ranged,victim,ember,magicVictim).thenExecute(()->{
   h.assertTrue(ranged.isInvisible(),"native effect really hides source at attack admission");hp[0]=victim.getHealth();hp[1]=magicVictim.getHealth();order(h,ranged,UnitAction.ATTACK,victim,BlockPos.ZERO);
   var ground=h.absolutePos(new BlockPos(12,1,22));var packet=new SkillNetwork.GroundCast(new SkillGroundRequest(UnitSkill.ASH_EMBER_LANDING,ember.getId(),ember.getUUID(),ember.rts().skills().revision(),ground));
   var bytes=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.GroundCast decoded;try{packet.encode(bytes);decoded=SkillNetwork.GroundCast.decode(bytes);h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"actual ground packet codec");}finally{bytes.release();}
   h.assertTrue(decoded.execute(player(h,"EmberEnemy"))==SkillResult.SUCCESS,"real paid Ember publication, no hand-made damage ticket");
  }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(ranged.getId())&&ranged.getTargetGoal().getTarget()==victim,"actual original ranged target"))
  .thenExecute(()->ranged.setNoAi(false))
  .thenWaitUntil(()->{h.assertTrue(!shots.isEmpty()&&victim.getHealth()<hp[0],"ordinary original projectile really impacts");h.assertTrue(magicVictim.getHealth()<hp[1],"real Ember collision/pulse completes vanilla magic write");})
  .thenExecute(()->{
   ranged.setNoAi(true);order(h,ranged,UnitAction.STOP,null,BlockPos.ZERO);
   h.assertTrue(shots.stream().anyMatch(p->p instanceof com.dreamseekers.shadow.AshBoneWingBolt),"actual ordinary Ash projectile class");
   h.assertTrue(active(victim)&&active(magicVictim)&&cd(victim)>0&&cd(magicVictim)>0,"both independent damage branches trigger");
   h.assertTrue(frameDepth(victim)==0&&frameDepth(magicVictim)==0,"both actual hurt calls exited");
   // Owner cooldown is the public evidence of the real skill; actual victim loss is separate from event amount.
   h.assertTrue(ember.rts().skills().cooldownTicks(UnitSkill.ASH_EMBER_LANDING)>0,"Ember remained paid");bank(h);pass("ORIGINAL_RANGED_AND_ACTUAL_EMBER_HP_WRITES");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=120)
 public static void canceledZeroInvulnerableFriendlyUnownedEnvironmentAndFullAbsorptionNeverTrigger(GameTestHelper h){
  room(h);var enemy=beetle(h,"NegativeEnemy",3.5,3.5);var friend=beetle(h,"Negative",3.5,26.5);var ally=beetle(h,"NegativeAlly",6.5,26.5);var raw=beetle(h,"NegativeRaw",9.5,26.5);var anchor=beetle(h,"NegativeRaw",12.5,26.5);
  List<PinkChickenScout> c=new ArrayList<>();for(int i=0;i<14;i++)c.add(chicken(h,"Negative",8.5+(i%4)*5,5.5+(i/4)*5));
  var gel=slime(h,"Negative",c.get(4).getX()-h.absolutePos(BlockPos.ZERO).getX()-1,c.get(4).getZ()-h.absolutePos(BlockPos.ZERO).getZ());
  c.get(2).setAbsorptionAmount(20);c.get(3).setAbsorptionAmount(3);c.get(11).setInvulnerable(true);c.get(13).setHealth(1);
  Object cancel=new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void lightFeatherCancelOrZeroHurt(LivingHurtEvent e){if(e.getEntity()==c.get(0))e.setCanceled(true);if(e.getEntity()==c.get(1))e.setAmount(0);}};listen(cancel);
  List<Entity> all=new ArrayList<>(c);all.addAll(List.of(enemy,friend,ally,raw,anchor,gel));
  ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
   AlliancesServerEvents.addAlliance(c.get(0).getOwnerName(),ally.getOwnerName());raw.setOwnerName("");
   h.assertTrue(new SkillNetwork.Cast(new SkillRequest(UnitSkill.GEL_GUARD,gel.getId(),gel.getUUID(),gel.rts().skills().revision(),c.get(4).getId(),c.get(4).getUUID())).execute(player(h,"Negative"))==SkillResult.SUCCESS,"real gel before boundary hit");
   for(int i:new int[]{0,1,2,3,4,11,13})c.get(i).hurt(enemy.damageSources().mobAttack(enemy),10);
   c.get(5).hurt(c.get(5).damageSources().magic(),2);c.get(6).hurt(friend.damageSources().mobAttack(friend),10);c.get(7).hurt(ally.damageSources().mobAttack(ally),10);c.get(8).hurt(raw.damageSources().mobAttack(raw),10);
   c.get(9).hurt(player(h,"NegativeEnemy").damageSources().playerAttack(player(h,"NegativeEnemy")),2);
   var wild=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());wild.setNoAi(true);wild.setPersistenceRequired();wild.moveTo(enemy.getX(),enemy.getY(),enemy.getZ()+2);ENTITIES.add(wild);h.assertTrue(h.getLevel().addFreshEntity(wild),"actual wild non-Unit fixture");c.get(10).hurt(wild.damageSources().mobAttack(wild),2);c.get(12).hurt(c.get(12).damageSources().mobAttack(c.get(12)),2);
   for(int i=0;i<c.size();i++){if(i==3)continue;h.assertTrue(!active(c.get(i))&&cd(c.get(i))==0&&frameDepth(c.get(i))==0,"ineligible/canceled/fully absorbed/dead source index "+i+" cannot mint passive");}
   h.assertTrue(active(c.get(3))&&cd(c.get(3))==200&&c.get(3).getHealth()<1000,"partial absorption with actual HP loss does trigger");
   close(h,c.get(0).getHealth(),1000,"canceled actual hurt");close(h,c.get(1).getHealth(),1000,"zero actual hurt");close(h,c.get(2).getHealth(),1000,"full vanilla absorption");close(h,c.get(4).getHealth(),1000,"full actual gel");h.assertTrue(SkillEffects.shieldRemaining(c.get(4))<12&&!c.get(13).isAlive(),"real gel consumed and actual lethal hit negative");bank(h);pass("REAL_NEGATIVES_ABSORPTION_AND_PARTIAL_HP_POSITIVE");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=270)
 public static void actualWorldClockExpiresAtSixtyAndOnlyFreshDamageAtTwoHundredRetriggers(GameTestHelper h){
  room(h);var enemy=beetle(h,"ClockEnemy",4.5,8.5);var scout=chicken(h,"Clock",8.5,8.5);long[] start={-1};
  ready(h,enemy,scout).thenExecute(()->{start[0]=h.getLevel().getGameTime();hit(h,enemy,scout);h.assertTrue(active(scout)&&cd(scout)==200,"first real write pays200");})
  .thenIdle(30).thenExecute(()->{hit(h,enemy,scout);h.assertTrue(cd(scout)==170&&scout.lightFeatherAlert().cooldownDeadline()==start[0]+200,"repeat actual hit cannot refresh paid deadline");})
  .thenIdle(29).thenExecute(()->{h.assertTrue(h.getLevel().getGameTime()==start[0]+59&&active(scout)&&cd(scout)==141,"last active tick59");hit(h,enemy,scout);})
  .thenIdle(1).thenExecute(()->h.assertTrue(!active(scout)&&cd(scout)==140,"exact60 short expiry despite repeat hit59"))
  .thenIdle(139).thenExecute(()->{h.assertTrue(h.getLevel().getGameTime()==start[0]+199&&cd(scout)==1,"original199 boundary");hit(h,enemy,scout);h.assertTrue(!active(scout)&&cd(scout)==1,"real damage199 does not trigger early");})
  .thenIdle(1).thenExecute(()->{h.assertTrue(cd(scout)==0,"original200 cooldown really elapsed");hit(h,enemy,scout);h.assertTrue(active(scout)&&cd(scout)==200&&scout.lightFeatherAlert().cooldownDeadline()==start[0]+400,"fresh real hit exactly200 retriggers without iframe/CD writes");bank(h);pass("EXACT_60_199_200_NO_REFRESH");}).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=BOUNDARY,timeoutTicks=260)
 public static void actualNaturalFlightMovesTwentyPercentFasterInFixedStraightWindow(GameTestHelper h){
  room(h);var baseline=chicken(h,"Flight",4.5,6.5);var boosted=chicken(h,"Flight",4.5,20.5);var enemy=beetle(h,"FlightEnemy",3.5,13.5);
  var probe=new FlightProbe(h,List.of(baseline,boosted));listen(probe);
  ready(h,baseline,boosted,enemy).thenExecute(()->{for(var c:List.of(baseline,boosted)){c.setNoAi(false);order(h,c,UnitAction.MOVE,null,h.absolutePos(new BlockPos(28,0,c==baseline?6:20)));}probe.admit=true;})
  .thenWaitUntil(()->probe.requireStable())
  .thenExecute(()->{probe.start=h.getLevel().getGameTime();hit(h,enemy,boosted);h.assertTrue(active(boosted)&&!active(baseline),"only actual hit lane gets passive after preexisting stable flight");})
  .thenIdle(49).thenExecute(()->{
   // The original12->28 failed the instantaneous-displacement assumption; retain it only as a diagnostic.
   for(int i=0;i<2;i++)h.assertTrue(probe.at(i,12)!=null&&probe.at(i,28)!=null,"original transient samples retained separately");
   double earlyBase=probe.at(0,28).position.x-probe.at(0,12).position.x,earlyBoost=probe.at(1,28).position.x-probe.at(1,12).position.x;
   System.out.println("LIGHT_FEATHER_FLIGHT_TRANSIENT_DIAGNOSTIC window=12:28 start="+probe.start+" ratio="+(earlyBoost/earlyBase)+" notSteadyAcceptance=true");
   double[] distance=new double[2];for(int i=0;i<2;i++){var from=probe.at(i,32);var to=probe.at(i,48);h.assertTrue(from!=null&&to!=null,"fixed32 to48 world positions");distance[i]=to.position.x-from.position.x;h.assertTrue(distance[i]>.1&&Math.abs(to.position.z-from.position.z)<.03,"actual straight forward world movement");for(var s:probe.samples)if(s.lane==i&&s.time>=probe.start+32&&s.time<=probe.start+48){h.assertTrue(!s.noAI&&s.moving&&s.airborneClear&&s.position.x<probe.endX-1,"all fixed window samples remain genuinely airborne/collision-free with original MOVE away from arrival");close(h,s.bonus,i==0?0:.20,"single category actual bonus");}}
   double ratio=distance[1]/distance[0];System.out.println("LIGHT_FEATHER_FLIGHT_FIXED_WINDOW window=32:48 start="+probe.start+" distances="+Arrays.toString(distance)+" ratio="+ratio+" samples="+probe.samples);h.assertTrue(Math.abs(ratio-1.20)<.01,"actual fixed-window flight is1.20, not1.44: "+ratio);
   close(h,boosted.getAttributeValue(Attributes.MOVEMENT_SPEED)/baseline.getAttributeValue(Attributes.MOVEMENT_SPEED),1.20,"ground attribute retained same one multiplier");close(h,boosted.getAttributeValue(Attributes.FLYING_SPEED)/baseline.getAttributeValue(Attributes.FLYING_SPEED),1.20,"flight attribute one multiplier");for(var c:List.of(baseline,boosted)){order(h,c,UnitAction.STOP,null,BlockPos.ZERO);c.setNoAi(true);}bank(h);pass("REAL_NATURAL_FLIGHT_120_FIXED32_48");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=BOUNDARY,timeoutTicks=150)
 public static void movementCategoryMaximumAndNativeCommandsDoNotRefreshOrStealPassive(GameTestHelper h){
  room(h);var weak=chicken(h,"Maximum",8.5,6.5);var strong=chicken(h,"Maximum",8.5,16.5);var friend=chicken(h,"Maximum",20.5,25.5);var enemy=beetle(h,"MaximumEnemy",4.5,25.5);long[] start={-1};
  ready(h,weak,strong,friend,enemy).thenExecute(()->{
   h.assertTrue(SkillEffects.addBuff(weak,weak,"fixture_tide10",BuffKind.MOVEMENT_SPEED,.10,90)&&SkillEffects.addBuff(strong,strong,"fixture_stronger25",BuffKind.MOVEMENT_SPEED,.25,20),"explicit trusted category fixtures, not claiming player research activation");start[0]=h.getLevel().getGameTime();hit(h,enemy,weak);hit(h,enemy,strong);close(h,SkillEffects.bonus(weak,BuffKind.MOVEMENT_SPEED),.20,"10 and20 choose20");close(h,SkillEffects.bonus(strong,BuffKind.MOVEMENT_SPEED),.25,"25 and20 choose25");order(h,weak,UnitAction.MOVE,null,h.absolutePos(new BlockPos(20,0,6)));order(h,strong,UnitAction.FOLLOW,friend,BlockPos.ZERO);
  }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(weak.getId())&&!UnitServerEvents.hasPendingImmediateAction(strong.getId()),"actual MOVE/FOLLOW dispatch"))
  .thenExecute(()->{h.assertTrue(active(weak)&&active(strong)&&strong.getFollowTarget()==friend&&weak.getMoveGoal().getMoveTarget()!=null,"new commands survive without revoking passive");order(h,weak,UnitAction.STOP,null,BlockPos.ZERO);order(h,strong,UnitAction.HOLD,null,BlockPos.ZERO);})
  .thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(weak.getId())&&!UnitServerEvents.hasPendingImmediateAction(strong.getId())&&strong.getHoldPosition(),"actual STOP/HOLD dispatch"))
  .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=start[0]+21,"after fixed stronger20 expiry"))
  .thenExecute(()->{h.assertTrue(h.getLevel().getGameTime()==start[0]+21&&active(strong)&&active(weak),"both original60 effects remain, not restarted by commands");close(h,SkillEffects.bonus(strong,BuffKind.MOVEMENT_SPEED),.20,"stronger effect falls back to only original remaining20");h.assertTrue(cd(strong)==179&&cd(weak)==179,"commands/max preserve original clock");})
  .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=start[0]+60,"original60 deadline"))
  .thenExecute(()->{h.assertTrue(!active(weak)&&!active(strong)&&cd(weak)==140&&cd(strong)==140,"neither command nor stronger expiry extends short effect");close(h,SkillEffects.bonus(weak,BuffKind.MOVEMENT_SPEED),.10,"unrelated longer10 survives");close(h,SkillEffects.bonus(strong,BuffKind.MOVEMENT_SPEED),0,"all shorter contributions expired");bank(h);pass("CATEGORY_MAX_AND_REAL_MOVE_FOLLOW_STOP_HOLD");}).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=BOUNDARY,timeoutTicks=180)
 public static void exactNestedWritesCanceledOuterOwnerAbaAndObserverExceptionsRemainIsolated(GameTestHelper h){
  room(h);var enemy=beetle(h,"NestedEnemy",3.5,3.5);var other=beetle(h,"NestedOtherEnemy",3.5,26.5);List<PinkChickenScout> c=new ArrayList<>();for(int i=0;i<6;i++)c.add(chicken(h,"Nested",8.5+(i%3)*6,7.5+(i/3)*12));
  var captureCow=new ThrowCow(h.getLevel(),true);var appliedCow=new ThrowCow(h.getLevel(),false);publishCow(h,captureCow,23.5,5.5);publishCow(h,appliedCow,23.5,15.5);
  List<Entity> all=new ArrayList<>(c);all.addAll(List.of(enemy,other,captureCow,appliedCow));
  ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
   for(int i=0;i<2;i++){final int lane=i;var victim=c.get(i);var outer=enemy.damageSources().mobAttack(enemy);boolean[] entered={false};Object callback=new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void lightFeatherNestedInnerHurt(LivingHurtEvent e){if(e.getEntity()==victim&&!entered[0]){entered[0]=true;victim.hurt(lane==0?outer:other.damageSources().mobAttack(other),20);e.setCanceled(true);}}};listen(callback);float before=victim.getHealth();victim.hurt(outer,10);h.assertTrue(entered[0]&&before-victim.getHealth()>0&&before-victim.getHealth()<=10.001&&active(victim)&&cd(victim)==200&&frameDepth(victim)==0,"one inner exact write triggers, canceled outer cannot borrow same/differentDS: "+i);}
   for(int i=2;i<4;i++){final var victim=c.get(i);final boolean attackerAba=i==3;boolean[] entered={false};Object callback=new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void lightFeatherOwnerAbaHurt(LivingHurtEvent e){if(e.getEntity()==victim&&!entered[0]){entered[0]=true;var unit=attackerAba?(Unit)enemy:victim;String owner=unit.getOwnerName();unit.setOwnerName(owner+"Temp");unit.setOwnerName(owner);}}};listen(callback);victim.hurt(enemy.damageSources().mobAttack(enemy),10);h.assertTrue(entered[0]&&!active(victim)&&cd(victim)==0&&frameDepth(victim)==0,"owner ABA inside actual callback revokes original captured frame: "+i);}
   var allied=c.get(4);Object alliance=new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void lightFeatherAllianceRevocationHurt(LivingHurtEvent e){if(e.getEntity()==allied)AlliancesServerEvents.addAlliance(allied.getOwnerName(),enemy.getOwnerName());}};listen(alliance);allied.hurt(enemy.damageSources().mobAttack(enemy),10);h.assertTrue(!active(allied)&&cd(allied)==0,"callback alliance cannot trigger former enemy receipt");AlliancesServerEvents.removeAlliance(allied.getOwnerName(),enemy.getOwnerName());
   for(var cow:List.of(captureCow,appliedCow)){float before=cow.getHealth();cow.hurt(enemy.damageSources().mobAttack(enemy),10);h.assertTrue(cow.getHealth()<before&&cow.captureCalls==1,"actual physical health write survives deliberate observer exception");}
   h.assertTrue(appliedCow.appliedCalls==1&&captureCow.appliedCalls==0,"exception occurs at actual selected capture/completion site");hit(h,enemy,c.get(5));h.assertTrue(active(c.get(5))&&frameDepth(c.get(5))==0,"new real chicken hurt after failures has no stale observer frame");
  }).thenIdle(21).thenExecute(()->{
   for(var cow:List.of(captureCow,appliedCow)){cow.throwNow=false;float before=cow.getHealth();cow.hurt(magic(h,other),2);h.assertTrue(cow.getHealth()<before&&cow.captureCalls==2&&cow.lastActual>0,"later vanilla magic write remains functional after exception");}bank(h);pass("NESTED_SAME_DIFFERENT_DS_ABA_AND_EXCEPTION_RECOVERY");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=BOUNDARY,timeoutTicks=140)
 public static void deathOwnerTransferNativeUnloadAndSameUuidReloadNeverReplayShortEffect(GameTestHelper h){
  room(h);var enemy=beetle(h,"LifecycleEnemy",3.5,3.5);var dead=chicken(h,"Lifecycle",8.5,6.5);var changed=chicken(h,"Lifecycle",8.5,14.5);var saved=chicken(h,"Lifecycle",8.5,23.5);var anchor=chicken(h,"Lifecycle",22.5,23.5);var dimension=chicken(h,"Lifecycle",22.5,14.5);Entity[] moved={null};ServerLevel other=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);long[] at={-1};UUID[] id={null};PinkChickenScout[] loaded={null};float[] hp={0};
  UUID permanent=UUID.fromString("3713aa8a-bb96-44da-bca4-ef23cbe3f808");saved.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(permanent,"independent fixture research",.02,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));saved.setScenarioRoleIndex(93);
  ready(h,enemy,dead,changed,saved,anchor,dimension).thenExecute(()->{
   at[0]=h.getLevel().getGameTime();for(var c:List.of(dead,changed,saved,dimension)){hit(h,enemy,c);h.assertTrue(active(c)&&cd(c)==200,"actual short effect before lifecycle event");}
   dead.hurt(dead.damageSources().genericKill(),10000);h.assertTrue(!dead.isAlive()&&!active(dead)&&cd(dead)==200,"fatal hit does not resurrect effect or refund paid clock");
   String owner=changed.getOwnerName();changed.setOwnerName(owner+"Temp");changed.setOwnerName(owner);h.assertTrue(!active(changed)&&cd(changed)==200,"owner ABA clears short but retains paid clock");
   h.assertTrue(other!=null,"actual second dimension exists");moved[0]=dimension.changeDimension(other,new net.minecraftforge.common.util.ITeleporter(){@Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity e,ServerLevel destination,java.util.function.Function<ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback){return new net.minecraft.world.level.portal.PortalInfo(new Vec3(2000+dimension.getX(),200,2000+dimension.getZ()),Vec3.ZERO,0,0);}@Override public Entity placeEntity(Entity e,ServerLevel current,ServerLevel destination,float yaw,java.util.function.Function<Boolean,Entity> reposition){return reposition.apply(false);}});
   h.assertTrue(moved[0] instanceof PinkChickenScout&&moved[0]!=dimension&&moved[0].level()==other&&moved[0].getUUID().equals(dimension.getUUID())&&dimension.isRemoved(),"actual changeDimension produces sameUUID body in other world");var transferred=(PinkChickenScout)moved[0];transferred.setNoAi(true);ENTITIES.add(transferred);h.assertTrue(!active(dimension)&&!active(transferred)&&frameDepth(dimension)==0&&frameDepth(transferred)==0&&transferred.lightFeatherAlert().cooldownDeadline()==at[0]+200,"real transfer clears both short leases/frames but retains original absolute deadline");
   id[0]=saved.getUUID();hp[0]=saved.getHealth();var nbt=saved.saveWithoutId(new CompoundTag());h.assertTrue(nbt.getCompound("DreamseekersLightFeatherAlert").getLong("CooldownUntil")==at[0]+200,"native absolute deadline saved");saved.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);h.assertTrue(!active(saved),"genuine unload clears original short lease");
   loaded[0]=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());loaded[0].load(nbt);h.assertTrue(!loaded[0].isNoAi()&&loaded[0].isNoGravity(),"observe original scout load policy before fixture isolation");loaded[0].setNoAi(true);ENTITIES.add(loaded[0]);h.assertTrue(h.getLevel().addFreshEntity(loaded[0]),"actual originalUUID native NBT republished");
  }).thenWaitUntil(()->h.assertTrue(loaded[0].tickCount>3&&h.getLevel().getEntity(id[0])==loaded[0],"actual loaded identity naturally ticks"))
  .thenExecute(()->{
   var c=loaded[0];h.assertTrue(c!=saved&&c.getUUID().equals(id[0])&&c.getScenarioRoleIndex()==93&&c.getHealth()==hp[0]&&c.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(permanent)!=null,"native identity HP/permanent data preserved");h.assertTrue(!active(c)&&cd(c)==200-(h.getLevel().getGameTime()-at[0])&&cd(c)>0,"sameUUID replacement cannot restore short lease or reset clock");hit(h,enemy,c);h.assertTrue(!active(c)&&frameDepth(c)==0,"fresh real hit during original paid clock stays inactive");var transferred=(PinkChickenScout)moved[0];h.assertTrue(!active(transferred)&&transferred.lightFeatherAlert().cooldownDeadline()==at[0]+200&&cd(transferred)==Math.max(0,at[0]+200-other.getGameTime()),"actual other-world clock preserves original paid deadline without short replay");bank(h);pass("NATIVE_LIFECYCLE_SAME_UUID_NO_REPLAY");
  }).thenSucceed();
 }

private static String account(String suffix){String name=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;if(OWNERS.add(name)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));}return name;}
private static FakePlayer player(GameTestHelper h,String suffix){String name=account(suffix);return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);var origin=h.absolutePos(BlockPos.ZERO);p.moveTo(origin.getX()+2.5,origin.getY()+1,origin.getZ()+29.5);ENTITIES.add(p);h.getLevel().addNewPlayer(p);PlayerServerEvents.players.add(p);syncMembership(p,true);return p;});}
private static void order(GameTestHelper h,Mob source,UnitAction action,Entity target,BlockPos destination){try{String owner=((Unit)source).getOwnerName();var sender=player(h,owner);var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"actual Forge command context resolves exact owner");h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(owner,action,target==null?-1:target.getId(),new int[]{source.getId()},destination,BlockPos.ZERO,false).handle(()->context),"actual authenticated native order accepted");}catch(ReflectiveOperationException failure){throw new RuntimeException("actual native command context",failure);}}
private static void damage(LivingEntity source,double amount){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
private static void neutral(LivingEntity target){target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setAbsorptionAmount(0);for(var attr:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(target.getAttribute(attr)!=null)target.getAttribute(attr).setBaseValue(0);}
private static void bank(GameTestHelper h){for(String owner:OWNERS){var r=ResourcesServerEvents.resourcesList.stream().filter(v->v.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null&&r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"no new currency/pending mutation or defeated-account recreation: "+owner);}for(Entity e:ENTITIES)if(e instanceof Unit u)h.assertTrue(u.getItems().isEmpty(),"no manufactured unit cargo");}
private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
private static void room(GameTestHelper h){var p=h.absolutePos(BlockPos.ZERO);var level=h.getLevel();for(int x=p.getX()>>4;x<=(p.getX()+31)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+31)>>4;z++){var c=new ChunkPos(x,z);if(!level.getForcedChunks().contains(c.toLong())){level.setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(p.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+32,level.getMaxBuildHeight(),p.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(p.offset(x,y,z),(x==0||x==31||z==0||z==31?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(p,p.offset(32,10,32))).forEach(Entity::discard);}
@SuppressWarnings("unchecked") private static void syncMembership(ServerPlayer player,boolean present){try{var list=player.serverLevel().getServer().getPlayerList();var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);byId.setAccessible(true);List<ServerPlayer> actual=(List<ServerPlayer>)players.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)byId.get(list);if(present){if(!actual.contains(player))actual.add(player);ids.put(player.getUUID(),player);SYNC_MEMBERS.add(player);}else{actual.remove(player);ids.remove(player.getUUID(),player);SYNC_MEMBERS.remove(player);}}catch(ReflectiveOperationException error){throw new RuntimeException("real local PlayerList membership",error);}}
private static final class SilentHandler extends ServerGamePacketListenerImpl {SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
private static final class SilentConnection extends net.minecraft.network.Connection {SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
 private static void pass(String name){System.out.println("DREAMSEEKERS_LIGHT_FEATHER_ALERT_PASS "+name);}
 private static void listen(Object listener){LISTENERS.add(listener);MinecraftForge.EVENT_BUS.register(listener);}
 private static PinkChickenScout chicken(GameTestHelper h,String owner,double x,double z){var e=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());owned(h,e,owner,x,z);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);return e;}
 private static BeetleUnit beetle(GameTestHelper h,String owner,double x,double z){var e=DreamseekersMod.BEETLE.get().create(h.getLevel());owned(h,e,owner,x,z);return e;}
 private static AshBoneWingUnit ash(GameTestHelper h,String owner,double x,double z){var e=DreamseekersMod.ASH_BONE_WING_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);return e;}
 private static PinkSlimeUnit slime(GameTestHelper h,String owner,double x,double z){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);return e;}
 private static void owned(GameTestHelper h,Mob e,String suffix,double x,double z){h.assertTrue(e instanceof Unit,"genuine registered RTS type");((Unit)e).setOwnerName(account(suffix));e.setNoAi(true);e.setPersistenceRequired();neutral(e);if(e instanceof AttackerUnit){damage(e,10);e.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).setBaseValue(1);}var aggro=e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get());if(aggro!=null)aggro.setBaseValue(0);var origin=h.absolutePos(BlockPos.ZERO);e.moveTo(origin.getX()+x,origin.getY()+1,origin.getZ()+z,0,0);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual entity publication");player(h,suffix);}
 private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"original entity ticks and identity before damage");}).thenExecute(()->{h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"focused fixture uses actual fog-off setting");bank(h);});}
 private static boolean active(PinkChickenScout c){return c.lightFeatherAlert().active();}
 private static int cd(PinkChickenScout c){return c.lightFeatherAlert().cooldownTicks();}
 private static float hit(GameTestHelper h,LivingEntity enemy,PinkChickenScout c){float before=c.getHealth();c.hurt(enemy.damageSources().mobAttack(enemy),10);float delta=before-c.getHealth();h.assertTrue(delta>0,"boundary stimulus must produce actual HP loss, not merely accepted hurt");return delta;}
 private static DamageSource magic(GameTestHelper h,LivingEntity enemy){return new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MAGIC),enemy);}
 private static int frameDepth(PinkChickenScout c){try{var f=LightFeatherAlert.State.class.getDeclaredField("hits");f.setAccessible(true);return ((java.util.Deque<?>)f.get(c.lightFeatherAlert())).size();}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
 private static final class ThrowCow extends Cow implements HealthWriteObservers.Observed {
  final boolean throwCapture;boolean throwNow=true;int captureCalls,appliedCalls;float lastActual;
  ThrowCow(ServerLevel level,boolean capture){super(EntityType.COW,level);throwCapture=capture;}
  @Override public HealthWriteObservers.Observer captureHealthWrite(DamageSource source){captureCalls++;if(throwNow&&throwCapture)throw new IllegalStateException("EXPECTED_LIGHT_FEATHER_OBSERVER_CAPTURE_FAILURE");return actual->{appliedCalls++;if(throwNow)throw new IllegalStateException("EXPECTED_LIGHT_FEATHER_OBSERVER_APPLIED_FAILURE");lastActual=actual;};}
 }
 private static void publishCow(GameTestHelper h,ThrowCow cow,double x,double z){cow.setNoAi(true);cow.setPersistenceRequired();cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);neutral(cow);var origin=h.absolutePos(BlockPos.ZERO);cow.moveTo(origin.getX()+x,origin.getY()+1,origin.getZ()+z);ENTITIES.add(cow);h.assertTrue(h.getLevel().addFreshEntity(cow),"test-only observed Cow actually published; no claim it is chicken frame");}
 private record FlightSample(long time,int lane,Vec3 position,Vec3 velocity,boolean noAI,boolean moving,boolean airborneClear,double bonus){}
 private static final class FlightProbe {
  final GameTestHelper h;final List<PinkChickenScout> birds;final List<FlightSample> samples=new ArrayList<>();final double endX;boolean admit;long start=-1;
  FlightProbe(GameTestHelper h,List<PinkChickenScout> birds){this.h=h;this.birds=birds;endX=h.absolutePos(new BlockPos(28,0,0)).getX()+.5;}
  @SubscribeEvent public void tick(TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END||e.level!=h.getLevel()||!admit)return;long now=e.level.getGameTime();for(int i=0;i<birds.size();i++){var b=birds.get(i);samples.add(new FlightSample(now,i,b.position(),b.getDeltaMovement(),b.isNoAi(),b.getMoveGoal().getMoveTarget()!=null&&!UnitServerEvents.hasPendingImmediateAction(b.getId()),!b.onGround()&&!b.horizontalCollision&&!b.verticalCollision&&b.level().noCollision(b,b.getBoundingBox()),SkillEffects.bonus(b,BuffKind.MOVEMENT_SPEED)));System.out.println("LIGHT_FEATHER_FLIGHT_SAMPLE "+samples.get(samples.size()-1)+" wantedY="+b.getMoveControl().getWantedY()+" onGround="+b.onGround()+" moveTarget="+b.getMoveGoal().getMoveTarget()+" navigationDone="+b.getNavigation().isDone()+" yya="+b.yya+" zza="+b.zza+" start="+start+" endX="+endX+" cooldown="+cd(b)+" active="+active(b));}}
  FlightSample exact(int lane,long time){return samples.stream().filter(s->s.lane==lane&&s.time==time).findFirst().orElse(null);}
  FlightSample at(int lane,long elapsed){return exact(lane,start+elapsed);}
  void requireStable(){h.assertTrue(!samples.isEmpty(),"natural END observations required");long last=samples.get(samples.size()-1).time;for(int i=0;i<birds.size();i++){var newest=exact(i,last);h.assertTrue(newest!=null&&newest.position.x<endX-12,"first natural straight segment leaves fixed window before arrival");for(long t=last-2;t<=last;t++){var s=exact(i,t);h.assertTrue(s!=null&&!s.noAI&&s.moving&&s.airborneClear&&s.velocity.x>.01&&Math.abs(s.velocity.z)<.0005&&Math.abs(s.velocity.x-newest.velocity.x)<.0005,"three completed horizontally steady airborne flight samples before trigger, not future ratio selection: "+s);var paired=exact(1-i,t);h.assertTrue(paired!=null&&paired.airborneClear&&Math.abs(s.position.y-paired.position.y)<.000001&&Math.abs(s.velocity.y-paired.velocity.y)<.000001,"pre-trigger lanes share native bobbing height/Y velocity within fixed1e-6: "+s+" / "+paired);}}}
 }

}
