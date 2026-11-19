package com.dreamseekers;

import com.dreamseekers.neutralcontract.*;
import com.dreamseekers.economy.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import net.pasterdream.init.PasterdreamModEntities;
import java.io.File;
import java.util.*;

/** Real normal-save double JVM. Verify never respawns/deserializes the expected entities or reconstructs the ledger. */
@GameTestHolder("dreamseekers_neutral_contract_restart") @PrefixGameTestTemplate(false)
public final class NeutralContractRestartGameTests {
    private static final String OWNER="NeutralContractRestart";
    private static final BlockPos ORIGIN=new BlockPos(167936,100,167936);
    private static final File EXPECTED=new File("neutral-contract-restart.nbt");
    private static final List<Long> CHUNKS=new ArrayList<>();
    private static PassiveIncomePeer peer;private static Resources account;private static BuildingPlacement court;
    private static FireflyWorker worker;private static Mob original,waiting;private static Mob converted;
    private static long pendingStarted;
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH) public void stop(ServerStoppingEvent e){
            if(!NeutralContracts.active(worker)||NeutralContracts.remaining(worker)<=0||NeutralContracts.remaining(worker)>=160)throw new AssertionError("actual unfinished8secondchannel required at normal stop");
            if(account.food!=950||account.extra.get(DreamPassiveEconomy.SHADOW)!=485||ContractLedger.get(e.getServer()).count(OWNER,account.accountEpoch)!=1)throw new AssertionError("one paidcontract, pendingchannelunpaid");
            var tag=new CompoundTag();tag.putInt("schema",1);tag.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
            tag.putUUID("epoch",account.accountEpoch);tag.putUUID("worker",worker.getUUID());tag.putUUID("original",original.getUUID());tag.putUUID("converted",converted.getUUID());tag.putUUID("waiting",waiting.getUUID());
            tag.putFloat("ratio",converted.getHealth()/converted.getMaxHealth());tag.putLong("pendingStart",pendingStarted);tag.putLong("stopTime",worker.level().getGameTime());tag.putLongArray("chunks",CHUNKS);
            try{NbtIo.writeCompressed(tag,EXPECTED);}catch(Exception ex){throw new RuntimeException(ex);}System.out.println("NEUTRAL_CONTRACT_RESTART_STOP_WITNESS");peer.close();
        }
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract_restart",template="dreamseekers_test_arena",batch="neutral_contract_restart",timeoutTicks=500)
    public static void permanentFreeOriginAndSlotsPersistButUnpaidChannelDoesNotResume(GameTestHelper h)throws Exception {
        switch(System.getProperty("dreamseekers.neutralContract.phase","")){case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare|verify required");}
    }
    private static void prepare(GameTestHelper h){var level=h.getLevel();h.assertTrue(!EXPECTED.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh isolatedworld");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(OWNER,DreamseekersMod.DREAMSEEKERS,97550,0,false,0L,0L));account=new Resources(OWNER,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);
        ResearchServerEvents.addCheat(OWNER,"foodforthought");peer=new PassiveIncomePeer(level,OWNER);
        for(int x=(ORIGIN.getX()>>4)-3;x<=(ORIGIN.getX()>>4)+3;x++)for(int z=(ORIGIN.getZ()>>4)-3;z<=(ORIGIN.getZ()>>4)+3;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"own fresh tickets");level.setChunkForced(x,z,true);level.getChunk(x,z);CHUNKS.add(key);}
        for(int x=-25;x<=30;x++)for(int z=-25;z<=30;z++){level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=16;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.CONTRACT_ECOLOGY.getRelativeBlockData(level),level,ORIGIN,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        court=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.CONTRACT_ECOLOGY,level,ORIGIN,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(court);court.onBuilt();court.getDataStorage();
        worker=DreamseekersMod.FIREFLY_WORKER.get().create(level);worker.setOwnerName(OWNER);worker.setNoAi(true);worker.moveTo(ORIGIN.getX()+3.5,101,ORIGIN.getZ()-9.5);h.assertTrue(level.addFreshEntity(worker),"realworker");
        original=PasterdreamModEntities.BLACK_BEETLE.get().create(level);original.setNoAi(false);original.setPersistenceRequired();original.moveTo(ORIGIN.getX()+5.5,101,ORIGIN.getZ()-9.5);original.setHealth(original.getMaxHealth()/2);h.assertTrue(level.addFreshEntity(original),"originalnative");
        waiting=PasterdreamModEntities.BASALT_SNAIL.get().create(level);waiting.setNoAi(false);waiting.setPersistenceRequired();waiting.moveTo(ORIGIN.getX()+3.5,101,ORIGIN.getZ()-11.5);h.assertTrue(level.addFreshEntity(waiting),"waitingnative");
        h.startSequence().thenWaitUntil(()->h.assertTrue(worker.tickCount>3&&original.tickCount>3&&waiting.tickCount>3&&original.onGround()&&waiting.onGround()&&level.getEntity(original.getUUID())==original&&level.getEntity(original.getId())==original&&level.getEntity(waiting.getUUID())==waiting&&level.getEntity(waiting.getId())==waiting&&level.getEntity(worker.getUUID())==worker&&level.getEntity(worker.getId())==worker,"ordinary originalpublication readiness"))
            .thenExecute(()->{original.setNoAi(true);waiting.setNoAi(true);h.assertTrue(NeutralContracts.begin(peer.actor,peer.connection,worker,worker.rts().ownershipRevision,original,UUID.randomUUID()).equals("approaching"),"actualpaidchannelbegins");})
            .thenWaitUntil(()->h.assertTrue(original.isRemoved()&&!NeutralContracts.active(worker),"actual160tickchannelconvertednative"))
            .thenExecute(()->{converted=(Mob)UnitServerEvents.getAllUnits().stream().filter(e->e instanceof BeetleUnit b&&b.getOwnerName().equals(OWNER)&&e.getPersistentData().contains(NeutralContracts.ORIGIN)).findFirst().orElseThrow();converted.setNoAi(true);converted.setPersistenceRequired();
                h.assertTrue(NeutralContracts.begin(peer.actor,peer.connection,worker,worker.rts().ownershipRevision,waiting,UUID.randomUUID()).equals("approaching"),"secondrealunpaidchannelbegins");pendingStarted=level.getGameTime();})
            .thenExecuteAfter(5,()->{h.assertTrue(NeutralContracts.remaining(worker)>0&&NeutralContracts.remaining(worker)<160,"activepartialchannelnotfabricated");MinecraftForge.EVENT_BUS.register(new StopWitness());
                PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(level);ResourcesServerEvents.saveResources(level);ResearchServerEvents.saveResearch();System.out.println("NEUTRAL_CONTRACT_RESTART_PREPARED");h.succeed();});
    }
    private static void verify(GameTestHelper h)throws Exception {var expected=NbtIo.readCompressed(EXPECTED);var level=h.getLevel();h.assertTrue(expected.getInt("schema")==1&&expected.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"separate realJVM");
        h.assertTrue(expected.getLong("stopTime")-expected.getLong("pendingStart")<160,"original160tickpendingwindow notexpanded");
        for(long key:expected.getLongArray("chunks")){h.assertTrue(level.getForcedChunks().contains(key),"ordinary forcedticket saved");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        account=ExtendedResourcesSync.account(OWNER);h.assertTrue(account!=null&&account.accountEpoch.equals(expected.getUUID("epoch"))&&account.food==950&&account.ore==1000&&account.extra.get(DreamPassiveEconomy.SHADOW)==485,"sameaccount onepayment nofree refund");
        h.assertTrue(ContractLedger.get(level.getServer()).count(OWNER,account.accountEpoch)==1&&ContractLedger.get(level.getServer()).consumed(expected.getUUID("original")),"nativeSavedData slot+tombstonepersist evenbeforeentityload");
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(expected.getUUID("worker")) instanceof FireflyWorker&&level.getEntity(expected.getUUID("converted")) instanceof BeetleUnit&&level.getEntity(expected.getUUID("waiting")) instanceof net.pasterdream.entity.BasaltSnailEntity,"alloriginalnativeentityUUIDs loaded ordinarily"))
            .thenExecute(()->{worker=(FireflyWorker)level.getEntity(expected.getUUID("worker"));converted=(Mob)level.getEntity(expected.getUUID("converted"));waiting=(Mob)level.getEntity(expected.getUUID("waiting"));
                h.assertTrue(level.getEntity(expected.getUUID("original"))==null&&RtsKillValueClaims.isFree(converted)&&!NeutralContracts.pending(converted),"oldnativeabsent andcompletedfreeorigin retained");
                h.assertTrue(Math.abs(converted.getHealth()/converted.getMaxHealth()-expected.getFloat("ratio"))<.001&&!NeutralContracts.active(worker)&&NeutralContracts.used(worker)==1,"HP preserved, pendingreservation cleared without slotrefund");})
            .thenExecuteAfter(180,()->{h.assertTrue(waiting.isAlive()&&!waiting.isRemoved()&&NeutralContracts.nativeKind(waiting)==NeutralContracts.Kind.SNAIL&&!NeutralContracts.active(worker),"noauto-resume across fullchannelwindow");
                h.assertTrue(account.food==950&&account.extra.get(DreamPassiveEconomy.SHADOW)==485&&ContractLedger.get(level.getServer()).count(OWNER,account.accountEpoch)==1,"no secondpayment or freeconversion");System.out.println("NEUTRAL_CONTRACT_RESTART_VERIFIED");h.succeed();});
    }
}
