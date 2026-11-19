package com.dreamseekers;

import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Real normal-save JVM pair. Verify never rebuilds original world objects or account. */
@GameTestHolder("dreamseekers_resonance_restart") @PrefixGameTestTemplate(false)
public final class ResonanceRestartGameTests {
    private static final String OWNER="ResonanceRestart_Account";
    private static final BlockPos ROOT=new BlockPos(37000,100,37000),DAIS=ROOT.offset(24,0,0);
    private static final File EXPECTED=new File("resonance-restart-expectations.nbt");
    private static final Set<Long> CHUNKS=new HashSet<>();
    private static BuildingPlacement root,dais;private static VillagerUnit body;private static Resources account;private static PassiveIncomePeer peer;private static long paidAt;
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH) public void stopping(ServerStoppingEvent event){
            if(body==null||SkillEffects.bonus(body,BuffKind.ATTACK_SPEED)!=.15||dais.level.getGameTime()>=paidAt+160)throw new AssertionError("real transient pulse must still exist at normal save");
            var state=dais.getDataStorage().getData(ResonanceState.TYPE);if(state.readyAt()!=paidAt+600||account.extra.get(DreamPassiveEconomy.SHADOW)!=485)throw new AssertionError("actual paid cooldown and exact balance");
            CompoundTag tag=new CompoundTag();tag.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putUUID("epoch",account.accountEpoch);
            tag.putUUID("instance",ResonanceNetwork.view(dais).instance());tag.putUUID("body",body.getUUID());tag.putLong("paidAt",paidAt);tag.putLong("stopTime",dais.level.getGameTime());tag.putLong("readyAt",state.readyAt());
            tag.putFloat("hp",body.getHealth());tag.putLongArray("chunks",new ArrayList<>(CHUNKS));
            try{NbtIo.writeCompressed(tag,EXPECTED);}catch(Exception e){throw new RuntimeException(e);}peer.close();System.out.println("RESONANCE_RESTART_STOP_WITNESS");
        }
    }
    @GameTest(templateNamespace="dreamseekers_resonance_restart",template="dreamseekers_test_arena",batch="resonance_restart",timeoutTicks=900)
    public static void cooldownPersistsAndTransientPulseNeverReplays(GameTestHelper h)throws Exception{
        switch(System.getProperty("dreamseekers.resonance.restart.phase","")){case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare|verify required");}}
    private static BuildingPlacement install(ServerLevel level,Building type,BlockPos pos){
        for(int x=0;x<22;x++)for(int z=0;z<22;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<20;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(var b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var p=BuildingUtils.getNewBuildingPlacement(type,level,pos,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(p);p.onBuilt();return p;}
    private static void prepare(GameTestHelper h){ServerLevel level=h.getLevel();h.assertTrue(!EXPECTED.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh independent preparation");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(OWNER,DreamseekersMod.DREAMSEEKERS,97550,0,false,0L,0L));account=new Resources(OWNER,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,OWNER);
        for(int x=(ROOT.getX()-48)>>4;x<=(ROOT.getX()+80)>>4;x++)for(int z=(ROOT.getZ()-48)>>4;z<=(ROOT.getZ()+64)>>4;z++){
            long key=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);CHUNKS.add(key);}level.getChunk(x,z);}
        root=install(level,DreamseekersMod.DREAM_ANCHOR,ROOT);dais=install(level,DreamseekersMod.RESONANCE_DAIS,DAIS);
        var pos=DAIS.offset(3,1,-4);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)level.setBlock(pos.above(y),Blocks.AIR.defaultBlockState(),2);
        body=EntityRegistrar.VILLAGER_UNIT.get().create(level);body.setOwnerName(OWNER);body.setNoAi(true);body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(level.addFreshEntity(body),"real fixture body");ResearchServerEvents.addResearch(OWNER,ResonanceResearch.ID);
        h.startSequence().thenWaitUntil(()->h.assertTrue(body.tickCount>2&&level.getEntity(body.getId())==body&&level.getEntity(body.getUUID())==body&&UnitServerEvents.getAllUnits().contains(body),"exact published and ticking original"))
        .thenExecute(()->{var v=ResonanceNetwork.view(dais);paidAt=level.getGameTime();h.assertTrue(ResonanceNetwork.pulse(peer.actor,dais,v.instance(),v.ownerRevision(),v.capture(),v.generation()).equals("success"),"genuine paid pulse");
            h.assertTrue(SkillEffects.bonus(body,BuffKind.ATTACK_SPEED)==.15&&account.extra.get(DreamPassiveEconomy.SHADOW)==485,"effect actual andpaid");
            MinecraftForge.EVENT_BUS.register(new StopWitness());PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(level);ResourcesServerEvents.saveResources(level);ResearchServerEvents.saveResearch();
            System.out.println("RESONANCE_RESTART_PREPARED");h.succeed();});}
    private static void verify(GameTestHelper h)throws Exception{var expected=NbtIo.readCompressed(EXPECTED);var level=h.getLevel();
        h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"separate actual JVM");
        h.assertTrue(expected.getLong("stopTime")<expected.getLong("paidAt")+160&&expected.getLong("readyAt")==expected.getLong("paidAt")+600,"hard stop witness captures unexpired effect andcooldown");
        account=ExtendedResourcesSync.account(OWNER);h.assertTrue(account!=null&&account.accountEpoch.equals(expected.getUUID("epoch"))&&account.extra.get(DreamPassiveEconomy.SHADOW)==485,"original native accountandpaidbalance restored");
        for(long key:expected.getLongArray("chunks")){var chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);}
        root=BuildingServerEvents.getBuildings().stream().filter(p->p.level==level&&p.ownerName.equals(OWNER)&&p.originPos.equals(ROOT)).findFirst().orElseThrow();
        dais=BuildingServerEvents.getBuildings().stream().filter(p->p.level==level&&p.ownerName.equals(OWNER)&&p.originPos.equals(DAIS)).findFirst().orElseThrow();peer=new PassiveIncomePeer(level,OWNER);
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(expected.getUUID("body")) instanceof VillagerUnit,"native entity diskchunk loading without replacement"))
        .thenExecute(()->{body=(VillagerUnit)level.getEntity(expected.getUUID("body"));var v=ResonanceNetwork.view(dais);
            h.assertTrue(v.instance().equals(expected.getUUID("instance"))&&v.rank()>0&&v.tuned()&&v.limit()==6,"same saved node identityandnetworkresearch restored");
            h.assertTrue(body.getHealth()==expected.getFloat("hp")&&SkillEffects.bonus(body,BuffKind.ATTACK_SPEED)==0,"health retained but transientpulse not reconstructed");
            ResonanceNetworkGameTests.near(h,body.getNonBaseAttacksPerSecond(),body.getBaseAttacksPerSecond(),"no stale savedattribute");
            h.assertTrue(dais.getDataStorage().getData(ResonanceState.TYPE).readyAt()==expected.getLong("readyAt")&&v.cooldown()>0,"absolute paidcooldown persisted acrossfreshJVM");
            h.assertTrue(ResonanceNetwork.pulse(peer.actor,dais,v.instance(),v.ownerRevision(),v.capture(),v.generation()).equals("cooldown")&&account.extra.get(DreamPassiveEconomy.SHADOW)==485,"reload cannotcastorrefund early");})
        .thenWaitUntil(()->h.assertTrue(level.getGameTime()>expected.getLong("readyAt"),"native serverclock reaches originaldeadline"))
        .thenExecute(()->{var v=ResonanceNetwork.view(dais);h.assertTrue(ResonanceNetwork.pulse(peer.actor,dais,v.instance(),v.ownerRevision(),v.capture(),v.generation()).equals("success")&&account.extra.get(DreamPassiveEconomy.SHADOW)==470,"only fresh authorized cast afternaturalCD paysagain");
            peer.close();body.discard();for(var p:List.of(root,dais)){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}
            ResearchServerEvents.removeAllResearchFor(OWNER);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r==account);
            for(long key:expected.getLongArray("chunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}System.out.println("RESONANCE_RESTART_VERIFY_PASS");h.succeed();});}
}
