// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Eight fixed real-server methods. Packet execution is authenticated local PlayerList, not socket/GUI coverage.
 * Building helper explicitly distinguishes boundary miniature placements from full production assets. */
@GameTestHolder("dreamseekers_shadow_golem_strike_skill")
@PrefixGameTestTemplate(false)
public final class ShadowGolemStrikeSkillGameTests {
 static final String CORE="dreamseekers_shadow_golem_strike_core",PREFIX="Strike_";
 static final List<Entity> ENTITIES=new ArrayList<>();
 static final Set<String> OWNERS=new LinkedHashSet<>();
 static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
 static final Set<ServerPlayer> SYNC_MEMBERS=new LinkedHashSet<>();
 static final Set<ChunkPos> TICKETS=new LinkedHashSet<>();
 static final List<Object> LISTENERS=new ArrayList<>();
 @AfterBatch(batch=CORE) public static void cleanup(ServerLevel level){
  ShadowGolemStrikeBuildingCases.cleanup(level);
  for(Object o:LISTENERS)MinecraftForge.EVENT_BUS.unregister(o);LISTENERS.clear();
  for(var p:List.copyOf(SYNC_MEMBERS))syncMembership(p,false);
  for(String a:OWNERS)for(String b:OWNERS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
  for(String owner:OWNERS)com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
  PlayerServerEvents.players.removeIf(p->OWNERS.contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->OWNERS.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->OWNERS.contains(r.ownerName));
  for(Entity e:List.copyOf(ENTITIES))if(!e.isRemoved()){if(e instanceof ServerPlayer p)p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);else e.discard();}
  for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();ENTITIES.clear();OWNERS.clear();PLAYERS.clear();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=180)
 public static void researchedBreachingBudgetAddsQuarterWithoutUnitOrTerrainExpansion(GameTestHelper h){ShadowGolemStrikeBuildingCases.encodedBuildingBudgetAndLegacyAftermath(h,true);}
 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=550)
 public static void encodedEntityReleaseClockAndStableCap(GameTestHelper h){
  room(h);var s=golem(h,"Clock",8.5,8.5);var primary=target(h,"ClockEnemy",11,8.5);
  var worker=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,worker,"ClockEnemy",8.5,6);
  var support=target(h,"ClockEnemy",6,8.5);
  var reserve=target(h,"ClockEnemy",8.5,11.5);var friend=target(h,"Clock",11,11);
  List<LivingEntity> candidates=List.of(worker,support,reserve);List<LivingEntity> selected=new ArrayList<>();
  long[] at={-1};int[] hits={0};List<UUID> receipts=new ArrayList<>();
  Object probe=new Object(){@SubscribeEvent public void golemClockActualStrikeAttack(LivingAttackEvent e){if(e.getSource().getEntity()==s&&ShadowGolemStrike.isStrike(e.getSource())){hits[0]++;receipts.add(e.getEntity().getUUID());h.assertTrue(e.getSource().is(DamageTypes.MOB_ATTACK),"dedicated marker retains actual physical MOB_ATTACK");}}};listen(probe);
  ready(h,s,primary,worker,support,reserve,friend).thenExecute(()->{
   var packet=new SkillNetwork.Cast(request(s,primary));var bytes=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.Cast decoded;
   try{packet.encode(bytes);decoded=SkillNetwork.Cast.decode(bytes);h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"actual entity packet roundtrip");}finally{bytes.release();}
   long rev=s.rts().skills().revision();no(h,decoded.execute(null),"missing sender");no(h,decoded.execute(player(h,"ClockEnemy")),"foreign owner");
   no(h,new SkillNetwork.Cast(new SkillRequest(UnitSkill.SHADOW_GOLEM_STRIKE,s.getId(),UUID.randomUUID(),rev,primary.getId(),primary.getUUID())).execute(player(h,"Clock")),"wrong source UUID");
   no(h,new SkillNetwork.Cast(new SkillRequest(UnitSkill.SHADOW_GOLEM_STRIKE,s.getId(),s.getUUID(),rev,primary.getId(),UUID.randomUUID())).execute(player(h,"Clock")),"wrong target UUID");
   no(h,cast(h,s,friend),"friendly primary");
   no(h,new SkillNetwork.Cast(new SkillRequest(UnitSkill.SHADOW_GOLEM_STRIKE,friend.getId(),friend.getUUID(),friend.rts().skills().revision(),primary.getId(),primary.getUUID())).execute(player(h,"Clock")),"wrong paid source subtype");
   primary.setInvisible(true);no(h,cast(h,s,primary),"invisible primary");primary.setInvisible(false);
   var prior=primary.position();primary.moveTo(prior.x+8,prior.y,prior.z);no(h,cast(h,s,primary),"range3 refuses chase/remote target");primary.moveTo(prior.x,prior.y,prior.z);
   for(int y=1;y<6;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(10,y,8)),Blocks.STONE.defaultBlockState(),2);
   no(h,cast(h,s,primary),"real opaque LOS blocks request");for(int y=1;y<6;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(10,y,8)),Blocks.AIR.defaultBlockState(),2);
   var wild=EntityType.COW.create(h.getLevel());wild.setNoAi(true);wild.moveTo(s.getX(),s.getY(),s.getZ()+2);h.assertTrue(h.getLevel().addFreshEntity(wild),"published nearby wild nonUnit");ENTITIES.add(wild);no(h,cast(h,s,wild),"nearby wild nonUnit rejected");wild.discard();
   no(h,cast(h,s,player(h,"ClockEnemy")),"player is not eligible RTS target");h.assertTrue(cd(s)==0&&s.rts().skills().revision()==rev,"all rejected inputs leave state untouched");
   at[0]=h.getLevel().getGameTime();ok(h,decoded.execute(player(h,"Clock")),"actual release reservation");
   h.assertTrue(cd(s)==480&&ShadowGolemStrike.remainingTicks(s)==20&&s.isCasting()&&!s.isIdle()&&hits[0]==0,"paid480 and exact20 independent windup");
   no(h,decoded.execute(player(h,"Clock")),"stale packet replay");no(h,cast(h,s,primary),"new request still cooling");
  }).thenIdle(10).thenExecute(()->{damage(s,20);order(h,s,UnitAction.MOVE,null,h.absolutePos(new BlockPos(12,1,12)),true);h.assertTrue(queued(s)&&ShadowGolemStrike.active(s),"genuine Shift queue retained while current cast is nonidle");h.assertTrue(cd(s)==470&&ShadowGolemStrike.remainingTicks(s)==10,"real half windup");})
  .thenIdle(9).thenExecute(()->{hp(h,primary,1000,"no instant or19tick damage");h.assertTrue(queued(s)&&ShadowGolemStrike.active(s),"Shift leg not consumed during windup");selected.addAll(candidates.stream().sorted(Comparator.comparingDouble((LivingEntity t)->ShadowGolemStrike.distanceSquared(s.position(),t.getBoundingBox())).thenComparing(t->t.getUUID().toString())).limit(2).toList());h.assertTrue(hits[0]==0&&cd(s)==461,"no early write");})
  .thenIdle(1).thenExecute(()->{
   hp(h,primary,970,"release reads changed AD20 not request AD10");for(var t:candidates)hp(h,t,selected.contains(t)?970:1000,"stable distance/UUID direct cap3");hp(h,friend,1000,"friendly secondary excluded");
   h.assertTrue(hits[0]==3&&receipts.get(0).equals(primary.getUUID())&&receipts.subList(1,3).equals(selected.stream().map(Entity::getUUID).toList())&&!ShadowGolemStrike.active(s)&&cd(s)==460,"exact20 primary-first and fixed ordered two secondaries");
   h.assertTrue(!primary.hasEffect(MobEffectRegistrar.STUN.get())&&!ShadowHandGrip.hasRoot(primary),"skill adds neither stun nor root");
  }).thenIdle(459).thenExecute(()->{h.assertTrue(h.getLevel().getGameTime()==at[0]+479&&cd(s)==1,"natural479 boundary");no(h,cast(h,s,primary),"cannot recast one tick early");})
  .thenIdle(1).thenExecute(()->{h.assertTrue(cd(s)==0,"natural exact480 expiry");ok(h,cast(h,s,primary),"fresh actual cast after deadline");h.assertTrue(cd(s)==480&&ShadowGolemStrike.remainingTicks(s)==20,"new real reservation no deadline rewrite");order(h,s,UnitAction.STOP,null,BlockPos.ZERO);bank(h);pass("ENCODED_AD_AT_RELEASE_20_480_STABLE_CAP3_WORKER_SUPPORT");}).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=130)
 public static void physicalDefenseAbsorptionGelAndOrdinaryReceiptExclusion(GameTestHelper h){
  room(h);List<ShadowGolemUnit> s=new ArrayList<>();List<GoldenFoxUnit> t=new ArrayList<>();
  for(int i=0;i<4;i++){double x=i%2==0?5.5:21.5,z=i<2?6.5:22.5;s.add(golem(h,"Defense"+i,x,z));t.add(target(h,"DefenseEnemy"+i,x+2.5,z));damage(s.get(i),20);}
  t.get(0).getAttribute(Attributes.ARMOR).setBaseValue(5);t.get(1).getAttribute(Attributes.ARMOR).setBaseValue(5);t.get(1).setAbsorptionAmount(5);
  var gel=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,gel,"DefenseEnemy1",25.5,9.5);
  var bone=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());owned(h,bone,"Defense2",11.5,22.5);
  var debt=DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(h.getLevel());owned(h,debt,"Defense2",11.5,25.5);
  t.get(2).addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,100));
  t.get(3).getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);s.get(3).getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
  Object motionProbe=new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void golemMotionBeforeDedicatedAttack(LivingAttackEvent event){if(event.getEntity()==t.get(3)&&ShadowGolemStrike.isStrike(event.getSource()))motionTrace(h,"living_attack",s.get(3),t.get(3));}
   @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void golemMotionInsideFinalDamage(LivingDamageEvent event){if(event.getEntity()==t.get(3)&&ShadowGolemStrike.isStrike(event.getSource()))motionTrace(h,"living_damage",s.get(3),t.get(3));}};listen(motionProbe);
  double[] defended={0};List<Entity> all=new ArrayList<>(s);all.addAll(t);all.addAll(List.of(gel,bone,debt));
  // Native join nudges each horizontal component by +/-0.005. Locked native aiStep scales
  // by .98 and clamps abs(component)<.003 to zero: <=26 total natural ticks from spawn.
  // Admission only; no velocity assignment, no changed windup, no post-hit tolerance change.
  ready(h,all.toArray(Entity[]::new)).thenWaitUntil(()->{
   for(var body:List.of(s.get(3),t.get(3))){var v=body.getDeltaMovement();h.assertTrue(Math.abs(v.x)<1e-8&&Math.abs(v.z)<1e-8,"wait for genuine native spawn-nudge settling before any skill reservation; ticks="+body.tickCount+" velocity="+v);}
  }).thenExecute(()->{
   System.out.println("SHADOW_GOLEM_STRIKE_SETTLED_ADMISSION time="+h.getLevel().getGameTime()+" sourceTicks="+s.get(3).tickCount+" targetTicks="+t.get(3).tickCount+" sourceVelocity="+s.get(3).getDeltaMovement()+" targetVelocity="+t.get(3).getDeltaMovement()+" absComponentThreshold=1e-8 nativeSpawnNudgeBoundTicks=26 windup=20 timeout=130");
   defended[0]=30*(1-t.get(0).getUnitPhysicalArmorPercentage())*(1-t.get(0).getUnitResistPercentage());h.assertTrue(defended[0]>17&&defended[0]<30,"real nonzero physical defense with positive post shield remainder");
   ok(h,other(h,gel,t.get(1),UnitSkill.GEL_GUARD),"actual enemy Gel12");
   ok(h,other(h,bone,t.get(2),UnitSkill.COORDINATED_MARK),"real ordinary-only Bone mark");ok(h,other(h,debt,t.get(2),UnitSkill.FRIENDLY_DEBT_CONTRACT),"real debt target");
   h.assertTrue(CoordinatedMark.ticksRemaining(t.get(2),s.get(2).getOwnerName())>0&&FriendlyDebtContract.contributors(t.get(2),s.get(2).getOwnerName())==0,"both real ordinary receipts primed");
   motionTrace(h,"before_reservation",s.get(3),t.get(3));for(int i=0;i<4;i++)ok(h,cast(h,s.get(i),t.get(i)),"actual physical lane"+i);motionTrace(h,"after_reservation",s.get(3),t.get(3));
  }).thenIdle(19).thenExecute(()->motionTrace(h,"real_tick19",s.get(3),t.get(3))).thenIdle(1).thenExecute(()->{
   motionTrace(h,"real_tick20_after_hurt",s.get(3),t.get(3));
   hp(h,t.get(0),1000-defended[0],"native physical armor applied once");hp(h,t.get(1),1000-(defended[0]-5-12),"native armor then absorption then Gel before health");close(h,t.get(1).getAbsorptionAmount(),0,"native absorption consumed5");close(h,SkillEffects.shieldRemaining(t.get(1)),0,"real Gel consumed12 after absorption");
   hp(h,t.get(2),970,"Bone does not multiply dedicated physical skill and slow grants no borrowed Hand bonus");h.assertTrue(FriendlyDebtContract.contributors(t.get(2),s.get(2).getOwnerName())==0&&!ShadowCorrosionTouch.hasDebuff(t.get(2)),"no ordinary Debt contribution or corrosion receipt");
   hp(h,t.get(3),970,"unresisted actual dedicated damage");h.assertTrue(t.get(3).getDeltaMovement().horizontalDistanceSqr()<1e-8&&s.get(3).getDeltaMovement().horizontalDistanceSqr()<1e-8,"zero native knockback with actual resistance0, not hidden by resistance fixture");
   bank(h);pass("REAL_PHYSICAL_ARMOR_ABSORPTION_GEL_NO_ORDINARY_RECEIPTS_NO_KNOCKBACK");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=140)
 public static void exactDamageScopeRejectsReplayReentryAndCancelledSlots(GameTestHelper h){
  room(h);List<ShadowGolemUnit> s=new ArrayList<>();List<GoldenFoxUnit> p=new ArrayList<>();List<List<GoldenFoxUnit>> others=new ArrayList<>();
  for(int i=0;i<6;i++){double x=5.5+(i%3)*10,z=i<3?7.5:23.5;s.add(golem(h,"Scope"+i,x,z));p.add(target(h,"ScopeEnemy"+i,x+2.5,z));others.add(List.of(target(h,"ScopeEnemy"+i,x,z+2.5),target(h,"ScopeEnemy"+i,x,z-2.5),target(h,"ScopeEnemy"+i,x-2.5,z)));}
  DamageSource[] retained=new DamageSource[6];boolean[] entered=new boolean[6],nestedAccepted=new boolean[2];int[] callbacks=new int[6];List<List<GoldenFoxUnit>> chosen=new ArrayList<>();
  Object probe=new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void golemScopeOnRealHurt(LivingHurtEvent e){
   if(!ShadowGolemStrike.isStrike(e.getSource()))return;int i=p.indexOf(e.getEntity());if(i<0||entered[i])return;entered[i]=true;callbacks[i]++;retained[i]=e.getSource();
   if(i==0)e.setCanceled(true);
   if(i==1)nestedAccepted[0]=p.get(i).hurt(e.getSource(),999);
   if(i==2)nestedAccepted[1]=others.get(i).get(2).hurt(e.getSource(),999);
  }
  @SubscribeEvent(priority=EventPriority.HIGHEST)public void golemScopeOnRealFinalDamage(LivingDamageEvent e){if(e.getEntity()==p.get(3)&&ShadowGolemStrike.isStrike(e.getSource())){String owner=s.get(3).getOwnerName();s.get(3).setOwnerName(account("ScopeForeign"));s.get(3).setOwnerName(owner);}if(e.getEntity()==p.get(5)&&ShadowGolemStrike.isStrike(e.getSource()))p.get(5).hurt(p.get(5).damageSources().genericKill(),10000);}
  };listen(probe);p.get(4).setHealth(1);
  List<Entity> all=new ArrayList<>(s);all.addAll(p);others.forEach(all::addAll);
  ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
   for(int i=0;i<6;i++){final int n=i;chosen.add(others.get(i).stream().sorted(Comparator.comparingDouble((GoldenFoxUnit t)->ShadowGolemStrike.distanceSquared(s.get(n).position(),t.getBoundingBox())).thenComparing(t->t.getUUID().toString())).limit(2).toList());ok(h,cast(h,s.get(i),p.get(i)),"real scope cast"+i);}
  }).thenIdle(20).thenExecute(()->{
   h.assertTrue(Arrays.stream(callbacks).allMatch(v->v==1)&&!nestedAccepted[0]&&!nestedAccepted[1],"genuine callback once, exact same/wrong target DS reentry rejected");
   hp(h,p.get(0),1000,"Forge canceled primary no write");hp(h,p.get(1),1000,"sameDS recursion revokes outer primary too");hp(h,p.get(2),985,"wrong-target exactDS replay cannot steal genuine primary");hp(h,p.get(3),1000,"owner ABA during final callback revokes current write");h.assertTrue(!p.get(4).isAlive()&&!p.get(5).isAlive(),"own exact lethal and independent nested lethal both genuinely killed original primary");
   for(int i=0;i<6;i++)for(var t:others.get(i))hp(h,t,(i==3||i==5)||!chosen.get(i).contains(t)?1000:985,"fixed list cap: canceled slot never refilled; invalid source stops later writes lane"+i);
   for(int i=0;i<6;i++){float before=p.get(i).getHealth();p.get(i).setAbsorptionAmount(50);h.assertTrue(!p.get(i).hurt(retained[i],999),"retained exact DS rejected outside original scope");hp(h,p.get(i),before,"retained source never causes later HP");close(h,p.get(i).getAbsorptionAmount(),50,"retained source rejected before absorption");h.assertTrue(cd(s.get(i))==460,"cancel/reentry/ABA never refunds paid480");}
   bank(h);pass("SAME_DS_REENTRY_RETAINED_REPLAY_WRONG_TARGET_CANCEL_NO_REFILL_OWNER_ABA_OWN_LETHAL_CONTINUES_EXTERNAL_LETHAL_STOPS");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=150)
 public static void commandsOwnerEpochAndPrimaryInvalidationKeepPaidCooldown(GameTestHelper h){
  room(h);List<ShadowGolemUnit> s=new ArrayList<>();List<GoldenFoxUnit> t=new ArrayList<>();
  for(int i=0;i<16;i++){double x=4.5+(i%4)*7,z=4.5+(i/4)*7;s.add(golem(h,"Cancel"+i,x,z));t.add(target(h,"CancelEnemy"+i,x+2.5,z));}
  var ownReserve=target(h,"Cancel10",2.5,29);var enemyReserve=target(h,"CancelEnemy9",5,29);var follow=target(h,"Cancel14",10,29);List<Entity> all=new ArrayList<>(s);all.addAll(t);all.addAll(List.of(ownReserve,enemyReserve,follow));long[] at={-1};
  ready(h,all.toArray(Entity[]::new)).thenExecute(()->{at[0]=h.getLevel().getGameTime();for(int i=0;i<16;i++)ok(h,cast(h,s.get(i),t.get(i)),"real paid canceled lane"+i);})
  .thenIdle(1).thenExecute(()->{
   UnitAction[] actions={UnitAction.STOP,UnitAction.HOLD,UnitAction.MOVE,UnitAction.ATTACK_MOVE,UnitAction.ATTACK};
   for(int i=0;i<actions.length;i++){order(h,s.get(i),actions[i],actions[i]==UnitAction.ATTACK?t.get(i):null,h.absolutePos(new BlockPos(3,1,3)));h.assertTrue(UnitServerEvents.hasPendingImmediateAction(s.get(i).getId())&&!ShadowGolemStrike.active(s.get(i)),"pending explicit command invalidates before dispatcher lane"+i);}
   String original=s.get(5).getOwnerName();s.get(5).setOwnerName(account("CancelForeign"));s.get(5).setOwnerName(original);
   original=t.get(6).getOwnerName();t.get(6).setOwnerName(s.get(6).getOwnerName());t.get(6).setOwnerName(original);
   t.get(7).setInvisible(true);
   t.get(8).moveTo(t.get(8).getX(),t.get(8).getY(),t.get(8).getZ()+4);
   t.get(9).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
   s.get(10).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
   AlliancesServerEvents.addAlliance(s.get(11).getOwnerName(),t.get(11).getOwnerName());
   s.get(12).addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffectRegistrar.STUN.get(),80));
   syncMembership(player(h,s.get(13).getOwnerName()),false);
   order(h,s.get(14),UnitAction.FOLLOW,follow,BlockPos.ZERO);
   s.get(15).setMoveTarget(h.absolutePos(new BlockPos(3,1,28)));
   for(int i=5;i<16;i++)h.assertTrue(!ShadowGolemStrike.active(s.get(i))&&cd(s.get(i))==479,"ownership/primary/lifecycle/direct command invalidation keeps paid deadline lane"+i);
  }).thenIdle(20).thenExecute(()->{
   for(int i=0;i<16;i++){hp(h,t.get(i),1000,"canceled primary never replaced or damaged lane"+i);h.assertTrue(!ShadowGolemStrike.active(s.get(i))&&cd(s.get(i))==459,"no delayed release/refund lane"+i);}
   h.assertTrue(h.getLevel().getGameTime()==at[0]+21,"real world clock not synthetic cancellation");bank(h);pass("PENDING_COMMANDS_OWNER_AND_TARGET_EPOCH_INVIS_RANGE_UNLOAD_ALLY_STUN_OFFLINE");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=160)
 public static void disarmRootAllowIndependentSkillAndLifecycleDoesNotReplay(GameTestHelper h){
  room(h);var disarmed=golem(h,"Disabled0",7.5,7.5);var rooted=golem(h,"Disabled1",7.5,23.5);var old=golem(h,"Disabled2",23.5,7.5);
  var a=target(h,"DisabledEnemy0",5,7.5);var b=target(h,"DisabledEnemy1",5,23.5);var c=target(h,"DisabledEnemy2",26,7.5);
  var lifecycleReserve=target(h,"Disabled2",23.5,26.5); // Genuine same-owner survivor across the original body removal; never recreate a defeated bank.
  var roar=DreamseekersMod.TERRORBEAK_UNIT.get().create(h.getLevel());owned(h,roar,"DisabledEnemy0",10,7.5);
  var hand=DreamseekersMod.SHADOW_HAND_UNIT.get().create(h.getLevel());owned(h,hand,"DisabledEnemy1",10,23.5);
  ShadowGolemUnit[] loaded={null};long[] reserved={-1};
  ready(h,disarmed,rooted,old,a,b,c,roar,hand,lifecycleReserve).thenExecute(()->{order(h,disarmed,UnitAction.ATTACK,a,BlockPos.ZERO);order(h,rooted,UnitAction.ATTACK,b,BlockPos.ZERO);})
  .thenWaitUntil(()->{selected(h,disarmed,a);selected(h,rooted,b);}).thenExecute(()->{
   ok(h,other(h,roar,roar,UnitSkill.TERRORBEAK_DREAD_ROAR),"real enemy disarm30");ok(h,other(h,hand,rooted,UnitSkill.SHADOW_HAND_GRIP),"real enemy root10 windup");
   h.assertTrue(TerrorbeakDreadRoar.hasDisarm(disarmed),"actual managed disarm present");ordinaryGoal(h,disarmed,a);hp(h,a,1000,"actual installed original goal attempted its ready ordinary cycle but managed disarm prevented HP write");
  }).thenIdle(10).thenExecute(()->{
   h.assertTrue(TerrorbeakDreadRoar.hasDisarm(disarmed)&&ShadowHandGrip.remainingRootTicks(rooted)==20,"actual independent disarm/root present before requests");
   ordinaryGoal(h,rooted,b);hp(h,b,990,"existing root only blocks movement: genuine original ordinary goal remains allowed before independent cast");
   rooted.setDeltaMovement(.015,rooted.getDeltaMovement().y,0);var inertia=rooted.getDeltaMovement();
   ok(h,cast(h,disarmed,a),"disarm explicitly permits independent skill");ok(h,cast(h,rooted,b),"root explicitly permits independent skill");
   h.assertTrue(rooted.getDeltaMovement().equals(inertia),"windup admission never clears gravity or external inertia");
   h.assertTrue(!disarmed.doHurtTarget(a)&&!rooted.doHurtTarget(b),"all ordinary attack bodies blocked while separate skill winds up");
   reserved[0]=h.getLevel().getGameTime();ok(h,cast(h,old,c),"real saved active charge");
   var nbt=old.saveWithoutId(new CompoundTag());h.assertTrue(nbt.getCompound("DreamseekersUnitSkills").getLong("SHADOW_GOLEM_STRIKE")==reserved[0]+480&&ShadowGolemStrike.remainingTicks(old)==20,"native NBT saves original paid deadline, real active charge exists");
   old.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);loaded[0]=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(h.getLevel());loaded[0].load(nbt);
   h.assertTrue(loaded[0].getUUID().equals(old.getUUID())&&!loaded[0].isNoAi(),"same NBT identity with original native load NoAI=false observed before containment");loaded[0].setNoAi(true);ENTITIES.add(loaded[0]);h.assertTrue(h.getLevel().addFreshEntity(loaded[0]),"actual local NBT publication, not two-JVM claim");
   h.assertTrue(!ShadowGolemStrike.active(loaded[0])&&cd(loaded[0])==480,"local load retains paid480 but no transient charge");
  }).thenIdle(20).thenExecute(()->{
   hp(h,a,985,"disarmed source still completed independent20 release");hp(h,b,975,"rooted source completed independent20 release15 after prior legitimate ordinary10");hp(h,c,1000,"old saved active charge never replays on loaded body");
   h.assertTrue(cd(disarmed)==460&&cd(rooted)==460&&cd(loaded[0])==460&&!ShadowGolemStrike.active(loaded[0]),"three actual deadlines remain exact, original NoAI containment does not fabricate clock");
   h.assertTrue(lifecycleReserve.isAlive()&&h.getLevel().getEntity(lifecycleReserve.getUUID())==lifecycleReserve&&lifecycleReserve.distanceTo(old)>10,"original reserve remains alive, published and outside this combat lane across unload/load");hp(h,lifecycleReserve,1000,"survivor reserve never participates in damage");h.assertTrue(disarmed.getTarget()==null&&rooted.getTarget()==null,"paid cast never restores old ordinary target after completion");h.assertTrue(!a.hasEffect(MobEffectRegistrar.STUN.get())&&!b.hasEffect(MobEffectRegistrar.STUN.get()),"no new stun application");bank(h);pass("REAL_DISARM_ROOT_SKILL_ALLOWED_ORDINARY_BLOCKED_INERTIA_NBT_NO_CHARGE_REPLAY");
  }).thenSucceed();
 }

 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=180)
 public static void encodedBuildingBudgetAndLegacyAftermath(GameTestHelper h){ShadowGolemStrikeBuildingCases.encodedBuildingBudgetAndLegacyAftermath(h);}
 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=180)
 public static void buildingIdentityAndCallbackInvalidation(GameTestHelper h){ShadowGolemStrikeBuildingCases.buildingIdentityAndCallbackInvalidation(h);}
 @GameTest(template="unit_isolation_arena",batch=CORE,timeoutTicks=180)
 public static void guardedReentryFractionAndCommittedAccounting(GameTestHelper h){ShadowGolemStrikeBuildingCases.guardedReentryFractionAndCommittedAccounting(h);}

    static void owned(GameTestHelper h,Mob e,String suffix,double x,double z){h.assertTrue(e instanceof Unit,"registered actual RTS fixture");((Unit)e).setOwnerName(account(suffix));e.setNoAi(true);e.setPersistenceRequired();neutral(e);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);if(e instanceof AttackerUnit){damage(e,10);e.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).setBaseValue(1);}var aggro=e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get());if(aggro!=null)aggro.setBaseValue(0);var origin=h.absolutePos(BlockPos.ZERO);e.moveTo(origin.getX()+x,origin.getY()+1,origin.getZ()+z,0,0);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"genuine entity publication");player(h,suffix);}
    static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"actual published IDs UUIDs and natural entity ticks; NoAI does not promise ground: "+e.getUUID());}).thenExecute(()->{h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual focused fog-off environment, no fabricated visibility");bank(h);});}
    static String account(String suffix){String name=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;if(OWNERS.add(name)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));}return name;}
    static FakePlayer player(GameTestHelper h,String suffix){String name=account(suffix);return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);var origin=h.absolutePos(BlockPos.ZERO);p.moveTo(origin.getX()+2.5,origin.getY()+1,origin.getZ()+29.5);ENTITIES.add(p);h.getLevel().addNewPlayer(p);PlayerServerEvents.players.add(p);syncMembership(p,true);return p;});}
    static void order(GameTestHelper h,Mob source,UnitAction action,Entity target,BlockPos destination){order(h,source,action,target,destination,false);}
    static void order(GameTestHelper h,Mob source,UnitAction action,Entity target,BlockPos destination,boolean shift){try{String owner=((Unit)source).getOwnerName();var sender=player(h,owner);var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"actual Forge command context resolves exact owner");h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(owner,action,target==null?-1:target.getId(),new int[]{source.getId()},destination,BlockPos.ZERO,shift).handle(()->context),"actual authenticated native order accepted");}catch(ReflectiveOperationException failure){throw new RuntimeException("actual native command context",failure);}}
    static void damage(LivingEntity source,double amount){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    static void neutral(LivingEntity target){target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setAbsorptionAmount(0);for(var attr:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(target.getAttribute(attr)!=null)target.getAttribute(attr).setBaseValue(0);}
    static void bank(GameTestHelper h){for(String owner:OWNERS){var r=ResourcesServerEvents.resourcesList.stream().filter(v->v.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null&&r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"no new currency/pending mutation or defeated-account recreation: "+owner);}for(Entity e:ENTITIES)if(e instanceof Unit u)h.assertTrue(u.getItems().isEmpty(),"no manufactured unit cargo");}
    static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    static void room(GameTestHelper h){var p=h.absolutePos(BlockPos.ZERO);var level=h.getLevel();for(int x=p.getX()>>4;x<=(p.getX()+31)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+31)>>4;z++){var c=new ChunkPos(x,z);if(!level.getForcedChunks().contains(c.toLong())){level.setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(p.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+32,level.getMaxBuildHeight(),p.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(p.offset(x,y,z),(x==0||x==31||z==0||z==31?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(p,p.offset(32,10,32))).forEach(Entity::discard);}
    @SuppressWarnings("unchecked") static void syncMembership(ServerPlayer player,boolean present){try{var list=player.serverLevel().getServer().getPlayerList();var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);byId.setAccessible(true);List<ServerPlayer> actual=(List<ServerPlayer>)players.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)byId.get(list);if(present){if(!actual.contains(player))actual.add(player);ids.put(player.getUUID(),player);SYNC_MEMBERS.add(player);}else{actual.remove(player);ids.remove(player.getUUID(),player);SYNC_MEMBERS.remove(player);}}catch(ReflectiveOperationException error){throw new RuntimeException("real local PlayerList membership",error);}}
    private static final class SilentHandler extends ServerGamePacketListenerImpl {SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection {SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
 static ShadowGolemUnit golem(GameTestHelper h,String name,double x,double z){var e=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(h.getLevel());owned(h,e,name,x,z);return e;}
 static GoldenFoxUnit target(GameTestHelper h,String name,double x,double z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,e,name,x,z);return e;}
 static SkillRequest request(ShadowGolemUnit s,LivingEntity t){return new SkillRequest(UnitSkill.SHADOW_GOLEM_STRIKE,s.getId(),s.getUUID(),s.rts().skills().revision(),t.getId(),t.getUUID());}
 static SkillResult cast(GameTestHelper h,ShadowGolemUnit s,LivingEntity t){return new SkillNetwork.Cast(request(s,t)).execute(player(h,s.getOwnerName()));}
 static int cd(ShadowGolemUnit s){return s.rts().skills().cooldownTicks(UnitSkill.SHADOW_GOLEM_STRIKE);}
 static SkillResult other(GameTestHelper h,SkillUnit s,LivingEntity t,UnitSkill skill){return new SkillNetwork.Cast(new SkillRequest(skill,s.mob().getId(),s.mob().getUUID(),s.rts().skills().revision(),t.getId(),t.getUUID())).execute(player(h,s.getOwnerName()));}
 static void ok(GameTestHelper h,SkillResult result,String why){h.assertTrue(result==SkillResult.SUCCESS,why+" actual="+result);}
 static void no(GameTestHelper h,SkillResult result,String why){h.assertTrue(result!=SkillResult.SUCCESS,why+" actual="+result);}
 static void listen(Object o){LISTENERS.add(o);MinecraftForge.EVENT_BUS.register(o);}
 static void pass(String text){System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_PASS "+text);}
 static void motionTrace(GameTestHelper h,String stage,LivingEntity... bodies){for(var body:bodies){var nearby=h.getLevel().getEntities(body,body.getBoundingBox().inflate(.3)).stream().map(e->e.getType()+":"+e.getUUID()+":pos="+e.position()+":box="+e.getBoundingBox()+":velocity="+e.getDeltaMovement()).toList();System.out.println("SHADOW_GOLEM_STRIKE_MOTION stage="+stage+" time="+h.getLevel().getGameTime()+" uuid="+body.getUUID()+" type="+body.getType()+" entityTicks="+body.tickCount+" pos="+body.position()+" velocity="+body.getDeltaMovement()+" box="+body.getBoundingBox()+" width="+body.getBbWidth()+" height="+body.getBbHeight()+" noAi="+(body instanceof Mob m&&m.isNoAi())+" noGravity="+body.isNoGravity()+" onGround="+body.onGround()+" horizontalCollision="+body.horizontalCollision+" verticalCollision="+body.verticalCollision+" blockAndEntityCollisionFree="+h.getLevel().noCollision(body)+" water="+body.isInWater()+" passenger="+body.isPassenger()+" nearby="+nearby);}}
 static void selected(GameTestHelper h,ShadowGolemUnit s,LivingEntity t){h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(s.getId())&&s.getTarget()==t&&s.getTargetGoal().getTarget()==t&&s.getTargetGoal().forced,"actual native authenticated ATTACK dispatcher selected original target");}
 static int ordinaryCooldown(Object goal){for(Class<?> type=goal.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField("ticksUntilNextAttack");field.setAccessible(true);return field.getInt(goal);}catch(NoSuchFieldException absent){}catch(ReflectiveOperationException error){throw new RuntimeException(error);}throw new IllegalStateException("missing original ordinary attack cooldown");}
 static void ordinaryGoal(GameTestHelper h,ShadowGolemUnit s,LivingEntity t){selected(h,s,t);var goal=s.getAttackGoal();h.assertTrue(s.isNoAi()&&goal instanceof com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal&&ordinaryCooldown(goal)<=0&&t.invulnerableTime==0,"actual original installed ready goal and native victim iframe, no cooldown rewrite");float width=s.getBbWidth()+s.getBonusMeleeRange(),tw=t.getBbWidth()+((Unit)t).getBonusMeleeRangeForAttackers();h.assertTrue(s.distanceToSqr(t)<=width*2*width*2+tw&&!s.getBoundingBox().intersects(t.getBoundingBox())&&s.hasLineOfSight(t),"genuine nonoverlapping native melee reach and LOS");goal.tick();h.assertTrue(ordinaryCooldown(goal)>0,"original ready ordinary goal really consumed attack cycle");}
 static boolean queued(Mob body){return UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(a->java.util.Arrays.stream(a.getUnitIds()).anyMatch(id->id==body.getId()));}
 static void hp(GameTestHelper h,LivingEntity target,double expected,String why){close(h,target.getHealth(),expected,why);}
}
