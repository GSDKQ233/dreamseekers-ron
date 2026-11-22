package com.dreamseekers;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.unit.BeetleMotherUnit;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Normal save/load across two real JVMs. No synthetic reconstruction in verify. */
@GameTestHolder("dreamseekers_summons_restart")
@PrefixGameTestTemplate(false)
public final class BoundedSummonRestartGameTests {
 private static final String OWNER="SummonRestart";
 private static final BlockPos ORIGIN=new BlockPos(135168,100,135168);
 private static final File MANIFEST=new File("bounded-summons-restart.nbt");
 @GameTest(templateNamespace="dreamseekers_summons_restart",template="ore_mine_test_arena",timeoutTicks=180)
 public static void actualSavedChildrenNeverRestoreAndCasterCooldownPersists(GameTestHelper h)throws Exception {
  switch(System.getProperty("dreamseekers.summons.phase","")){
   case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare/verify required");
  }
 }
 private static void prepare(GameTestHelper h)throws Exception {
  h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh independent world");
  var level=h.getLevel();
  for(int x=(ORIGIN.getX()-12)>>4;x<=(ORIGIN.getX()+12)>>4;x++)for(int z=(ORIGIN.getZ()-12)>>4;z<=(ORIGIN.getZ()+12)>>4;z++)level.setChunkForced(x,z,true);
  for(BlockPos p:BlockPos.betweenClosed(ORIGIN.offset(-10,-1,-10),ORIGIN.offset(10,5,10)))level.setBlock(p,p.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
  Resources account=new Resources(OWNER,111,222,333);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);
  ResearchServerEvents.addCheat(OWNER,"foodforthought");
  var caster=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(level);caster.setOwnerName(OWNER);caster.moveTo(ORIGIN.getX()+.5,100,ORIGIN.getZ()+.5);caster.setNoAi(true);h.assertTrue(level.addFreshEntity(caster),"originalcaster enters");
  var ordinary=DreamseekersMod.BEETLE.get().create(level);ordinary.setOwnerName(OWNER);ordinary.moveTo(ORIGIN.getX()+8.5,100,ORIGIN.getZ()+8.5);ordinary.setNoAi(true);h.assertTrue(level.addFreshEntity(ordinary),"ordinarysamekind enters");
  h.startSequence().thenWaitUntil(()->{
   h.assertTrue(caster.tickCount>0&&caster.isAddedToWorld()&&level.getEntity(caster.getId())==caster&&level.getEntity(caster.getUUID())==caster,
       "original caster fully published to both authoritative lookups before real skill input");
   h.assertTrue(ordinary.tickCount>0&&ordinary.isAddedToWorld()&&level.getEntity(ordinary.getId())==ordinary&&level.getEntity(ordinary.getUUID())==ordinary,
       "original ordinary unit fully published before snapshot");
  }).thenExecute(()->{
   try{castAndSnapshot(h,caster,ordinary,account);}catch(Exception e){throw new RuntimeException(e);}
  }).thenSucceed();
 }
 private static void castAndSnapshot(GameTestHelper h,BeetleMotherUnit caster,com.dreamseekers.unit.BeetleUnit ordinary,Resources account)throws Exception {
  var level=h.getLevel();
  try(var peer=new PassiveIncomePeer(level,OWNER)) {
   var request=new SkillRequest(UnitSkill.MOTHER_BROOD,caster.getId(),caster.getUUID(),caster.rts().skills().revision(),caster.getId(),caster.getUUID());
   long revision=account.revision;var result=SkillService.request(peer.actor,request);
   String diagnostic="result="+result+" casterAdded="+caster.isAddedToWorld()+" casterAlive="+caster.isAlive()
       +" casterRemoved="+caster.isRemoved()+" casterTick="+caster.tickCount+" casterById="+(level.getEntity(caster.getId())==caster)
       +" casterByUuid="+(level.getEntity(caster.getUUID())==caster)+" casterOwner="+caster.getOwnerName()
       +" actorAlive="+peer.actor.isAlive()+" actorRemoved="+peer.actor.isRemoved()+" actorSameLevel="+(peer.actor.serverLevel()==level)
       +" actorByName="+(level.getServer().getPlayerList().getPlayerByName(OWNER)==peer.actor)
       +" actorByUuid="+(level.getServer().getPlayerList().getPlayer(peer.actor.getUUID())==peer.actor)
       +" sourceChunk="+level.hasChunkAt(caster.blockPosition())+" sourceBelow="+level.getBlockState(caster.blockPosition().below())
       +" pop="+UnitServerEvents.getCurrentPopulation(OWNER)+" supply="+com.solegendary.reignofnether.building.BuildingServerEvents.getTotalPopulationSupply(OWNER)
       +" shadow="+account.extra.get(DreamPassiveEconomy.SHADOW)+" sourcePos="+caster.position()+" stageTime="+level.getGameTime();
   System.out.println("BOUNDED_SUMMONS_PREPARE_CAST_DIAGNOSTIC "+diagnostic);
   h.assertTrue(result==SkillResult.SUCCESS,"real group before normal save: "+diagnostic);
   h.assertTrue(account.revision==revision&&account.extra.get(DreamPassiveEconomy.SHADOW)==500,"birth left the original balance and ledger revision unchanged");
  }
  var children=UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Unit u&&OWNER.equals(u.getOwnerName())&&BoundedSummons.isTemporary(e)).toList();
  h.assertTrue(children.size()==2&&BoundedSummons.activeCount(OWNER)==2,"two real live children at save checkpoint");
  children.forEach(e->((Mob)e).setNoAi(true));
  var tag=new CompoundTag();tag.putInt("schema",1);tag.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putUUID("caster",caster.getUUID());tag.putUUID("ordinary",ordinary.getUUID());tag.putUUID("child0",children.get(0).getUUID());tag.putUUID("child1",children.get(1).getUUID());tag.putUUID("epoch",account.accountEpoch);
  tag.putLong("now",level.getGameTime());tag.putLong("deadline",level.getGameTime()+caster.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD));tag.putInt("shadow",account.extra.get(DreamPassiveEconomy.SHADOW));
  h.assertTrue(tag.getLong("deadline")==tag.getLong("now")+900,"original normal cooldown900 not inflated for restart");NbtIo.writeCompressed(tag,MANIFEST);
  System.out.println("BOUNDED_SUMMONS_PREPARED now="+tag.getLong("now")+" deadline="+tag.getLong("deadline")+" children="+children.size());
 }
 private static void verify(GameTestHelper h)throws Exception {
  h.assertTrue(MANIFEST.isFile(),"originalmanifest present");var tag=NbtIo.readCompressed(MANIFEST);var level=h.getLevel();
  h.assertTrue(tag.getInt("schema")==1&&tag.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"genuinely distinct JVM");
  for(int x=(ORIGIN.getX()-12)>>4;x<=(ORIGIN.getX()+12)>>4;x++)for(int z=(ORIGIN.getZ()-12)>>4;z<=(ORIGIN.getZ()+12)>>4;z++)level.getChunk(x,z);
  h.startSequence().thenWaitUntil(()->{
   h.assertTrue(level.getEntity(tag.getUUID("caster")) instanceof BeetleMotherUnit,"original casterUUID from disk");h.assertTrue(level.getEntity(tag.getUUID("ordinary")) instanceof com.dreamseekers.unit.BeetleUnit,"original ordinarysamekind fromdisk");
  }).thenExecute(()->{
   var caster=(BeetleMotherUnit)level.getEntity(tag.getUUID("caster"));var account=ExtendedResourcesSync.account(OWNER);long now=level.getGameTime();
   h.assertTrue(now>=tag.getLong("now")&&now<tag.getLong("deadline"),"normal world time advanced but original cooldown still live");
   h.assertTrue(caster.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==tag.getLong("deadline")-now,"cooldown exactly original deadline minus loaded clock");
   h.assertTrue(account!=null&&account.accountEpoch.equals(tag.getUUID("epoch"))&&account.extra.get(DreamPassiveEconomy.SHADOW)==tag.getInt("shadow"),"same account and unchanged shadow balance survive restart");
   h.assertTrue(level.getEntity(tag.getUUID("child0"))==null&&level.getEntity(tag.getUUID("child1"))==null&&BoundedSummons.activeCount(OWNER)==0,"both saved temporary children absent, no recreated leases");
   h.assertTrue(UnitServerEvents.getAllUnits().stream().noneMatch(e->e instanceof Unit u&&OWNER.equals(u.getOwnerName())&&BoundedSummons.isTemporary(e)),"no temporary child in population roster");
   h.assertTrue(!BoundedSummons.isTemporary(level.getEntity(tag.getUUID("ordinary"))),"ordinary paid same-type unit retained");
   h.assertTrue(UnitServerEvents.getCurrentPopulation(OWNER)==6,"native population is only caster4 plus ordinary2 after restart");
   System.out.println("BOUNDED_SUMMONS_RESTART_VERIFIED now="+now+" remaining="+caster.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD));
  }).thenSucceed();
 }
}
