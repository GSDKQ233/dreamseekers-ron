// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.jellyfish.JellyfishBalance;
import com.dreamseekers.production.DreamProductionPlacement;
import com.solegendary.reignofnether.building.*;
import net.minecraft.world.level.block.Rotation;
import com.dreamseekers.unit.JellyfishUnit;
import com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;
/** Ordinary entity/RTS/resource storage across two real JVMs, no manually substituted deserialize. */
@GameTestHolder("dreamseekers_jellyfish_restart")
@PrefixGameTestTemplate(false)
public final class JellyfishRestartGameTests {
    private static final String OWNER="JellyRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(14336,90,14336);
    private static final File MANIFEST=new File("jellyfish-restart-expectations.nbt");
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_restart",timeoutTicks=680)
    public static void ordinaryRestartRetainsIdentityHealthAndGenuineCollisionFlight(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.jellyfish.test.phase","")) {
            case "prepare" -> prepare(h); case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("Unknown jellyfish restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        ServerLevel level=h.getLevel();h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null&&!MANIFEST.exists(),"fresh isolated restart fixture");
        CompoundTag expected=new CompoundTag();expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int cx=896;cx<=897;cx++)for(int cz=896;cz<=899;cz++) {
            long key=ChunkPos.asLong(cx,cz);h.assertTrue(!level.getForcedChunks().contains(key),"never adopt existing forced chunks");level.setChunkForced(cx,cz,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);
        for(int x=0;x<30;x++)for(int z=0;z<50;z++)level.setBlock(ORIGIN.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+30,level.getMaxBuildHeight(),ORIGIN.getZ()+50)).forEach(Entity::discard);
        for(int x=0;x<30;x++)for(int z=0;z<50;z++) {
            level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));
        BlockPos producerPos=ORIGIN.offset(0,0,25);
        for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.BEETLE_SANCTUARY.getRelativeBlockData(level),level,producerPos,Rotation.NONE))
            level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        BuildingPlacement producer=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.BEETLE_SANCTUARY,level,producerPos,Rotation.NONE,OWNER,false);
        h.assertTrue(producer instanceof DreamProductionPlacement,"real addon producer factory before ordinary save");
        BuildingServerEvents.getBuildings().add(producer);producer.onBuilt();
        ((DreamProductionPlacement)producer).setRallyPoint(ORIGIN.offset(25,0,35));
        expected.putLong("producerOrigin",producerPos.asLong());
        JellyfishUnit jelly=DreamseekersMod.JELLYFISH_UNIT.get().create(level);h.assertTrue(jelly!=null,"registered paid native jellyfish");
        jelly.setOwnerName(OWNER);jelly.setScenarioRoleIndex(88);jelly.setOnDeathCommand("");jelly.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5);
        h.assertTrue(level.addFreshEntity(jelly),"actual normal entity storage");expected.putUUID("jellyfish",jelly.getUUID());
        h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2,"real ordinary ticks before persistence"))
            .thenExecute(()->{h.assertTrue(jelly.hurt(jelly.damageSources().generic(),5)&&jelly.getHealth()<JellyfishBalance.MAX_HEALTH,"ordinary damage before shutdown");order(jelly,UnitAction.MOVE,ORIGIN.offset(12,0,5));})
            .thenIdle(240).thenExecute(()->{h.assertTrue(near(jelly,ORIGIN.offset(12,0,5)),"first JVM genuine flight arrival: "+jelly.position());order(jelly,UnitAction.STOP,BlockPos.ZERO);})
            .thenIdle(30).thenExecute(()->{
                foundation(h,jelly);ledger(h);h.assertTrue(jelly.getMoveGoal().getMoveTarget()==null&&jelly.getNavigation().isDone(),"actual stopped state before storage");
                expected.putFloat("health",jelly.getHealth());expected.putDouble("x",jelly.getX());expected.putDouble("y",jelly.getY());expected.putDouble("z",jelly.getZ());
                try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
                System.out.println("DREAMSEEKERS_JELLYFISH_RESTART_PREPARED uuid="+jelly.getUUID()+" health="+jelly.getHealth()+" pos="+jelly.position());
            }).thenSucceed(); // Normal shutdown owns entity, roster and resource saving.
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM manifest exists");CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"genuine different JVM");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"normal world restored same chunks");ChunkPos c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        JellyfishUnit[] ref={null};Vec3[] start={null};double[] maxY={0};
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(expected.getUUID("jellyfish")) instanceof JellyfishUnit jelly&&jelly.tickCount>2,"actual asynchronous entity loader and real ticks"))
            .thenExecute(()->{
                JellyfishUnit jelly=(JellyfishUnit)level.getEntity(expected.getUUID("jellyfish"));ref[0]=jelly;foundation(h,jelly);ledger(h);
                var loadedProducer=BuildingServerEvents.getBuildings().stream().filter(b->b.originPos.equals(BlockPos.of(expected.getLong("producerOrigin")))).findFirst().orElseThrow();
                h.assertTrue(loadedProducer instanceof DreamProductionPlacement p&&p.getBuilding()==DreamseekersMod.BEETLE_SANCTUARY
                        &&p.ownerName.equals(OWNER)&&p.isBuilt&&!p.shouldBeDestroyed()&&p.productionQueue.isEmpty()
                        &&p.getFinalRallyPoint().equals(ORIGIN.offset(25,0,35)),"ordinary registry-based producer load retains identity, live geometry and rally, without a free production");

                h.assertTrue(jelly.getScenarioRoleIndex()==88&&jelly.getOnDeathCommand().isEmpty()&&jelly.getAnchor().equals(BlockPos.ZERO),"scenario owner and unanchored identity preserved");
                h.assertTrue(jelly.getHealth()==expected.getFloat("health")&&jelly.getHealth()<JellyfishBalance.MAX_HEALTH,"health not reset on native load");
                h.assertTrue(jelly.position().distanceToSqr(new Vec3(expected.getDouble("x"),expected.getDouble("y"),expected.getDouble("z")))<.2&&jelly.getMoveGoal().getMoveTarget()==null&&jelly.getFollowTarget()==null,"stopped position has no wild motion or stale target");
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(jelly.getUUID())).count()==1,"exact UUID appears once");
                for(int x=10;x<=28;x++)for(int y=1;y<=7;y++)for(int z:new int[]{3,7})level.setBlock(ORIGIN.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
                for(int z=4;z<=6;z++)for(int y=1;y<=2;y++)level.setBlock(ORIGIN.offset(18,y,z),Blocks.STONE.defaultBlockState(),2);
                start[0]=jelly.position();maxY[0]=jelly.getY();order(jelly,UnitAction.MOVE,ORIGIN.offset(25,0,5));
            });
        for(int i=0;i<330;i++)sequence.thenIdle(1).thenExecute(()->{
            JellyfishUnit jelly=ref[0];h.assertTrue(level.noCollision(jelly)&&!jelly.isNoAi()&&jelly.isNoGravity()&&!jelly.noPhysics,"every observed second-JVM flight tick preserves body collision and active AI");maxY[0]=Math.max(maxY[0],jelly.getY());
        });
        sequence.thenExecute(()->{
            JellyfishUnit jelly=ref[0];h.assertTrue(jelly.position().distanceToSqr(start[0])>16&&near(jelly,ORIGIN.offset(25,0,5))&&maxY[0]>=ORIGIN.getY()+2.95,"second JVM genuinely flies over new two-block obstacle: "+jelly.position()+" maxY="+maxY[0]);order(jelly,UnitAction.HOLD,BlockPos.ZERO);
        }).thenIdle(20).thenExecute(()->{h.assertTrue(ref[0].getHoldPosition()&&ref[0].getNavigation().isDone(),"post restart HOLD executes");order(ref[0],UnitAction.STOP,BlockPos.ZERO);})
          .thenIdle(20).thenExecute(()->{
            JellyfishUnit jelly=ref[0];foundation(h,jelly);ledger(h);h.assertTrue(!jelly.getHoldPosition()&&jelly.getMoveGoal().getMoveTarget()==null&&jelly.getItems().isEmpty(),"post restart STOP executes without gifts or cargo");
            BuildingServerEvents.getBuildings().removeIf(b->b.ownerName.equals(OWNER));
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));jelly.discard();
            for(long key:expected.getLongArray("forcedChunks")){ChunkPos c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
            System.out.println("DREAMSEEKERS_JELLYFISH_RESTART_UUID_HEALTH_FLIGHT_PASS uuid="+expected.getUUID("jellyfish"));
          }).thenSucceed();
    }
    private static void foundation(GameTestHelper h,JellyfishUnit jelly) {
        h.assertTrue(jelly.getOwnerName().equals(OWNER)&&Factions.getFaction(jelly)==DreamseekersMod.DREAMSEEKERS,"owner faction identity");
        h.assertTrue(!jelly.isNoAi()&&jelly.isNoGravity()&&jelly.isFlyingUnit()&&!jelly.noPhysics&&jelly.isPersistenceRequired()&&!jelly.canPickUpLoot(),"paid active collision flight and persistence flags");
        h.assertTrue(jelly.getNavigation() instanceof LowAltitudeFlyingNavigation&&jelly.getAttributeValue(Attributes.FLYING_SPEED)==JellyfishBalance.SPEED&&jelly.getBbWidth()==.7f&&jelly.getBbHeight()==1.4f,"original adult body and actual provisional flight navigator");
        h.assertTrue(!((Object)jelly instanceof AttackerUnit)&&!((Object)jelly instanceof WorkerUnit)&&jelly.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.JELLY_FLOW))&&jelly.getTarget()==null&&!jelly.canGarrison()&&!jelly.isBaby()&&!jelly.isInLove(),"approved flow does not add attacks, animal breeding or worker duties");
    }
    private static void ledger(GameTestHelper h){Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER)).findFirst().orElseThrow();h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"ordinary save movement grants nothing and debits nothing twice");}
    private static void order(JellyfishUnit jelly,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(OWNER,action,-1,new int[]{jelly.getId()},pos,BlockPos.ZERO);}
    private static boolean near(JellyfishUnit jelly,BlockPos p){return Math.abs(jelly.getX()-(p.getX()+.5))<2&&Math.abs(jelly.getZ()-(p.getZ()+.5))<2;}
}
