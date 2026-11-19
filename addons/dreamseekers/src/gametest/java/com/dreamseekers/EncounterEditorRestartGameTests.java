package com.dreamseekers;
import com.dreamseekers.encounter.*;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.economy.DreamSandboxEconomy;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

@GameTestHolder("dreamseekers_encounter_editor_restart") @PrefixGameTestTemplate(false)
public final class EncounterEditorRestartGameTests {
 static final String OWNER="EncounterRestartEditor";static final File EXPECTED=new File("encounter-editor-restart.nbt");static final BlockPos ORIGIN=new BlockPos(249856,100,249856);
 static final List<Long> CHUNKS=new ArrayList<>();static PassiveIncomePeer peer;static Resources account;static Mob beetle,snail;static long firstPlacement,baselineRevision;static int baselineShadow;
 public static final class StopWitness {
  @SubscribeEvent(priority=EventPriority.HIGH) public void stop(ServerStoppingEvent event){var ledger=EncounterLedger.get(event.getServer());if(account.food!=1000||account.wood!=1000||account.ore!=1000||account.extra.get(DreamPassiveEconomy.SHADOW)!=baselineShadow||account.revision!=baselineRevision)throw new AssertionError("editor financially unchanged after real sandbox initialization");if(ledger.count()!=2||!ledger.matches(beetle.getUUID(),NeutralEncounters.Kind.BEETLE)||!ledger.matches(snail.getUUID(),NeutralEncounters.Kind.SNAIL)||beetle.isNoAi()||snail.isNoAi()||!beetle.isAlive()||!snail.isAlive())throw new AssertionError("two genuine live AI-on native encounters required at normal save");
   CompoundTag tag=new CompoundTag();tag.putInt("schema",1);tag.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putLong("firstPlacement",firstPlacement);tag.putLong("stopTime",beetle.level().getGameTime());tag.putUUID("beetle",beetle.getUUID());tag.putUUID("snail",snail.getUUID());tag.putUUID("epoch",account.accountEpoch);tag.putInt("shadow",baselineShadow);tag.putLong("revision",baselineRevision);tag.putLongArray("chunks",CHUNKS);
   try{NbtIo.writeCompressed(tag,EXPECTED);}catch(Exception ex){throw new RuntimeException(ex);}System.out.println("ENCOUNTER_EDITOR_RESTART_STOP_WITNESS");peer.close();
  }
 }
 @GameTest(templateNamespace="dreamseekers_encounter_editor_restart",template="editor_arena",batch="encounter_editor_restart",timeoutTicks=500)
 public static void NativeEditorBodiesMarkersAndGlobalQuotaPersistAcrossRealJvm(GameTestHelper h)throws Exception{switch(System.getProperty("dreamseekers.encounterEditor.phase","")){case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare|verify required");}}
 static String place(ServerLevel level,NeutralEncounters.Kind kind,BlockPos floor,long sequence){var token=EconomyActionGate.query(peer.actor,peer.connection);return EncounterPackets.perform(peer.actor,peer.connection,new EncounterPackets.Payload(false,level.dimension().location(),kind,floor,UUID.randomUUID()),token.session(),token.epoch(),sequence);}
 static Mob at(ServerLevel level,BlockPos floor){return level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(floor.above()).inflate(1),e->e.getPersistentData().contains(NeutralEncounters.MARKER)).stream().findFirst().orElseThrow();}
 static void prepare(GameTestHelper h){var level=h.getLevel();h.assertTrue(!EXPECTED.exists()&&EncounterLedger.get(level.getServer()).count()==0,"fresh dedicated saved world");PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(OWNER,Factions.NONE,97600,0,false,0L,0L));account=new Resources(OWNER,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,OWNER);h.assertTrue(DreamSandboxEconomy.initialize(level.getServer(),OWNER),"real saved sandbox initialization before editor placement");baselineShadow=account.extra.get(DreamPassiveEconomy.SHADOW);baselineRevision=account.revision;h.assertTrue(baselineShadow==DreamPassiveEconomy.SHADOW_CAP,"actual sandbox shadow cap baseline");
  var originChunk=new ChunkPos(ORIGIN);for(int x=originChunk.x-3;x<=originChunk.x+3;x++)for(int z=originChunk.z-3;z<=originChunk.z+3;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"fresh persistent native chunk tickets");level.setChunkForced(x,z,true);level.getChunk(x,z);CHUNKS.add(key);}
  for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++){level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=8;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}peer.actor.moveTo(ORIGIN.getX()-15.5,101,ORIGIN.getZ()-15.5);
  h.startSequence().thenExecute(()->{h.assertTrue(place(level,NeutralEncounters.Kind.BEETLE,ORIGIN,1).equals("placed"),"actual editor packet admission publishes native beetle");beetle=at(level,ORIGIN);firstPlacement=level.getGameTime();})
   .thenExecuteAfter(8,()->{h.assertTrue(place(level,NeutralEncounters.Kind.SNAIL,ORIGIN.offset(8,0,0),2).equals("placed"),"actual editor admission publishes native snail");snail=at(level,ORIGIN.offset(8,0,0));})
   .thenWaitUntil(()->h.assertTrue(beetle.tickCount>3&&snail.tickCount>3&&beetle.onGround()&&snail.onGround()&&level.getEntity(beetle.getId())==beetle&&level.getEntity(beetle.getUUID())==beetle&&level.getEntity(snail.getId())==snail&&level.getEntity(snail.getUUID())==snail,"original native AI physically lands and publishes under exact UUIDs"))
   .thenExecute(()->{h.assertTrue(!beetle.isNoAi()&&!snail.isNoAi()&&beetle.isPersistenceRequired()&&snail.isPersistenceRequired()&&NeutralContracts.nativeKind(beetle)==NeutralContracts.Kind.BEETLE&&NeutralContracts.nativeKind(snail)==NeutralContracts.Kind.SNAIL,"no fake owner, disabled AI or incompatible marker in product output");MinecraftForge.EVENT_BUS.register(new StopWitness());PlayerServerEvents.saveRTSPlayers();ResourcesServerEvents.saveResources(level);System.out.println("ENCOUNTER_EDITOR_RESTART_PREPARED");h.succeed();});
 }
 static void verify(GameTestHelper h)throws Exception{var expected=NbtIo.readCompressed(EXPECTED);var level=h.getLevel();h.assertTrue(expected.getInt("schema")==1&&expected.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"actual separate JVM");var ledger=EncounterLedger.get(level.getServer());h.assertTrue(ledger.count()==2&&ledger.matches(expected.getUUID("beetle"),NeutralEncounters.Kind.BEETLE)&&ledger.matches(expected.getUUID("snail"),NeutralEncounters.Kind.SNAIL),"same native SavedData registrations already present before entity lookup");
  account=ExtendedResourcesSync.account(OWNER);h.assertTrue(account!=null&&account.accountEpoch.equals(expected.getUUID("epoch"))&&account.food==1000&&account.wood==1000&&account.ore==1000&&account.extra.get(DreamPassiveEconomy.SHADOW)==expected.getInt("shadow")&&expected.getInt("shadow")==DreamPassiveEconomy.SHADOW_CAP&&account.revision==expected.getLong("revision")&&account.addonData.contains(DreamSandboxEconomy.INITIALIZED),"original initialized editor account unchanged, not re-created");
  for(long key:expected.getLongArray("chunks")){h.assertTrue(level.getForcedChunks().contains(key),"ordinary native chunk tickets persisted");var cp=new ChunkPos(key);level.getChunk(cp.x,cp.z);}
  h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(expected.getUUID("beetle")) instanceof net.pasterdream.entity.BlackBeetleEntity&&level.getEntity(expected.getUUID("snail")) instanceof net.pasterdream.entity.BasaltSnailEntity,"actual original entities load from chunks, no factory reconstruction"))
   .thenExecute(()->{beetle=(Mob)level.getEntity(expected.getUUID("beetle"));snail=(Mob)level.getEntity(expected.getUUID("snail"));h.assertTrue(!beetle.isNoAi()&&!snail.isNoAi()&&beetle.isPersistenceRequired()&&snail.isPersistenceRequired()&&NeutralContracts.nativeKind(beetle)==NeutralContracts.Kind.BEETLE&&NeutralContracts.nativeKind(snail)==NeutralContracts.Kind.SNAIL,"original native AI/persistence/marker survive normal world save");beetle.hurt(level.damageSources().genericKill(),100000);})
   .thenWaitUntil(()->h.assertTrue(beetle.isRemoved()&&ledger.count()==1&&!ledger.matches(beetle.getUUID(),NeutralEncounters.Kind.BEETLE),"true native death after reload releases one persisted registration"))
   .thenExecute(()->{snail.discard();h.assertTrue(ledger.count()==0,"real discard after reload releases remaining registration");snail.discard();h.assertTrue(ledger.count()==0,"no duplicate release underflow");System.out.println("ENCOUNTER_EDITOR_RESTART_VERIFIED");h.succeed();});
 }
}
