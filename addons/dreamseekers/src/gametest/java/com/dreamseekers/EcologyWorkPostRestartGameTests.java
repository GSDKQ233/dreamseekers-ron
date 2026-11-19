package com.dreamseekers;
import com.dreamseekers.ecologyworkposts.*;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;
/** Separate real JVM phases; verify never inserts/reconstructs original accounts, buildings or units. */
@GameTestHolder("dreamseekers_ecology_restart") @PrefixGameTestTemplate(false)
public final class EcologyWorkPostRestartGameTests {
    private static final String OWNER="EcologyRestart_Account";
    private static final BlockPos ORIGIN=new BlockPos(14000,100,14000);
    private static final File EXPECTED=new File("ecology-workposts-restart-expectations.nbt");
    private static final Set<Long> CHUNKS=new HashSet<>();
    private static Resources account;private static BuildingPlacement court;private static VillagerUnit resting,travelling;private static PassiveIncomePeer peer;
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH) public void stop(ServerStoppingEvent e){
            var snapshot=EcologyWorkPosts.snapshot(court);
            if(snapshot.size()!=2||snapshot.stream().filter(EcologyWorkPosts.Occupant::arrived).count()!=1)throw new AssertionError("actual resting and blocked travelling orders required at normal stop");
            CompoundTag tag=new CompoundTag();tag.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putUUID("epoch",account.accountEpoch);
            tag.putUUID("court",ResearchPlacementIdentity.serverIdentity(court));tag.putUUID("resting",resting.getUUID());tag.putUUID("travelling",travelling.getUUID());
            tag.putFloat("restingHp",resting.getHealth());tag.putFloat("travellingHp",travelling.getHealth());tag.putLongArray("chunks",new ArrayList<>(CHUNKS));
            try{NbtIo.writeCompressed(tag,EXPECTED);}catch(Exception error){throw new RuntimeException(error);}peer.close();System.out.println("ECOLOGY_RESTART_STOP_WITNESS");
        }
    }
    @GameTest(templateNamespace="dreamseekers_ecology_restart",template="dreamseekers_test_arena",batch="ecology_restart",timeoutTicks=900)
    public static void transientWorkOrdersDoNotResumeAfterRealRestart(GameTestHelper h)throws Exception {
        switch(System.getProperty("dreamseekers.ecology.restart.phase","")){case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare|verify required");}
    }
    private static void prepare(GameTestHelper h){
        ServerLevel level=h.getLevel();h.assertTrue(!EXPECTED.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh independent restart setup");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(OWNER,DreamseekersMod.DREAMSEEKERS,97548,0,false,0L,0L));account=new Resources(OWNER,1000,1000,1000);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,OWNER);
        for(int x=(ORIGIN.getX()-25)>>4;x<=(ORIGIN.getX()+30)>>4;x++)for(int z=(ORIGIN.getZ()-25)>>4;z<=(ORIGIN.getZ()+30)>>4;z++) {long k=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(k)){level.setChunkForced(x,z,true);CHUNKS.add(k);}}
        for(int x=-25;x<=30;x++)for(int z=-25;z<=30;z++){level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=18;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.CONTRACT_ECOLOGY.getRelativeBlockData(level),level,ORIGIN,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        court=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.CONTRACT_ECOLOGY,level,ORIGIN,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(court);court.onBuilt();
        resting=spawn(level,4,-8);travelling=spawn(level,4,-18);
        BlockPos cage=travelling.blockPosition();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=4;y++)if(Math.abs(x)==2||Math.abs(z)==2||y==4)level.setBlock(cage.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        ResearchServerEvents.addResearch(OWNER,ResourceLocation.fromNamespaceAndPath("dreamseekers","contract_care"));
        h.startSequence().thenWaitUntil(()->h.assertTrue(resting.tickCount>3&&travelling.tickCount>3&&resting.onGround()&&travelling.onGround()&&UnitServerEvents.getAllUnits().contains(resting)&&UnitServerEvents.getAllUnits().contains(travelling),"real published units"))
        .thenExecute(()->h.assertTrue(EcologyWorkPosts.assign(peer.actor,court,ResearchPlacementIdentity.serverIdentity(court),court.getCaptureRevision(),List.of(new EcologyPackets.Target(resting.getId(),resting.getUUID()),new EcologyPackets.Target(travelling.getId(),travelling.getUUID()))).equals("assigned"),"two explicit leases"))
        .thenWaitUntil(()->h.assertTrue(EcologyWorkPosts.snapshot(court).stream().filter(EcologyWorkPosts.Occupant::arrived).count()==1,"one natural arrival, one genuinely blocked route"))
        .thenExecute(()->{
            MinecraftForge.EVENT_BUS.register(new StopWitness());PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(level);ResourcesServerEvents.saveResources(level);ResearchServerEvents.saveResearch();
            System.out.println("ECOLOGY_RESTART_PREPARED");h.succeed();
        });
    }
    private static VillagerUnit spawn(ServerLevel level,int x,int z){var u=EntityRegistrar.VILLAGER_UNIT.get().create(level);u.setOwnerName(OWNER);u.moveTo(ORIGIN.getX()+x+.5,ORIGIN.getY()+1,ORIGIN.getZ()+z+.5);u.setNoAi(false);u.setHealth(u.getMaxHealth()-5);if(!level.addFreshEntity(u))throw new AssertionError("spawn failed");return u;}
    private static void verify(GameTestHelper h)throws Exception {
        CompoundTag expected=NbtIo.readCompressed(EXPECTED);h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"separate JVM proven");
        var r=ExtendedResourcesSync.account(OWNER);h.assertTrue(r!=null&&r.accountEpoch.equals(expected.getUUID("epoch")),"native original account epoch restored");
        court=BuildingServerEvents.getBuildings().stream().filter(p->p.ownerName.equals(OWNER)&&p.originPos.equals(ORIGIN)).findFirst().orElseThrow();
        h.assertTrue(ResearchPlacementIdentity.serverIdentity(court).equals(expected.getUUID("court"))&&DreamSpecialistResearch.careUnlocked(OWNER,false),"same court and earned research restored");
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getEntity(expected.getUUID("resting")) instanceof VillagerUnit&&h.getLevel().getEntity(expected.getUUID("travelling")) instanceof VillagerUnit,"original vanilla entity chunks loaded without replacement"))
        .thenExecute(()->{
            resting=(VillagerUnit)h.getLevel().getEntity(expected.getUUID("resting"));travelling=(VillagerUnit)h.getLevel().getEntity(expected.getUUID("travelling"));
            h.assertTrue(EcologyWorkPosts.snapshot(court).isEmpty(),"neither old lease resumes");h.assertTrue(resting.getHealth()==expected.getFloat("restingHp")&&travelling.getHealth()==expected.getFloat("travellingHp"),"original saved health preserved");
        }).thenExecuteAfter(240,()->{
            h.assertTrue(EcologyWorkPosts.snapshot(court).isEmpty()&&resting.getHealth()==expected.getFloat("restingHp")&&travelling.getHealth()==expected.getFloat("travellingHp"),"no free healing or delayed auto re-assignment across full rest interval");
            for(var u:List.of(resting,travelling))if(!u.isRemoved())u.discard();court.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(court);
            ResearchServerEvents.removeAllResearchFor(OWNER);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(OWNER));
            for(long key:expected.getLongArray("chunks")){var p=new ChunkPos(key);h.getLevel().setChunkForced(p.x,p.z,false);}System.out.println("ECOLOGY_RESTART_VERIFY_PASS");h.succeed();
        });
    }
}
