// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.fox.FoxBalance;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Ordinary world storage across two actual JVMs, never a substituted manual deserialization. */
@GameTestHolder("dreamseekers_fox_restart")
@PrefixGameTestTemplate(false)
public final class GoldenFoxRestartGameTests {
    private static final String OWNER="FoxRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(12288,90,12288);
    private static final File MANIFEST=new File("fox-restart-expectations.nbt");

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_fox_restart",timeoutTicks=600)
    public static void ordinaryRestartPreservesPaidIdentityAndNormalGroundAi(GameTestHelper h) throws Exception {
        String phase=System.getProperty("dreamseekers.fox.test.phase","");
        if(phase.equals("prepare"))prepare(h);else if(phase.equals("verify"))verify(h);
        else throw new IllegalArgumentException("Unknown fox restart phase "+phase);
    }
    private static void prepare(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null&&!MANIFEST.exists(),"fresh isolated fox restart fixture");
        CompoundTag expected=new CompoundTag();
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int cx=768;cx<=769;cx++)for(int cz=768;cz<=769;cz++){
            long key=ChunkPos.asLong(cx,cz);h.assertTrue(!level.getForcedChunks().contains(key),"do not adopt another fixture's chunk");
            level.setChunkForced(cx,cz,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);
        // Keep natural falling terrain out without touching neighboring worlds or fixtures.
        for(int x=0;x<30;x++)for(int z=0;z<20;z++)level.setBlock(ORIGIN.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+30,level.getMaxBuildHeight(),ORIGIN.getZ()+20)).forEach(Entity::discard);
        for(int x=0;x<30;x++)for(int z=0;z<20;z++){
            level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));
        GoldenFoxUnit fox=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);
        h.assertTrue(fox!=null,"actual registered paid fox created");
        fox.setOwnerName(OWNER);fox.setScenarioRoleIndex(87);fox.setOnDeathCommand("");
        fox.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5,0,0);
        h.assertTrue(level.addFreshEntity(fox),"paid fox enters normal world entity storage");
        expected.putUUID("fox",fox.getUUID());
        h.startSequence().thenWaitUntil(()->h.assertTrue(fox.tickCount>2,"wait genuine ordinary ground ticks: "+describe(fox))).thenExecute(()->{
            h.assertTrue(fox.hurt(fox.damageSources().generic(),5)&&fox.getHealth()==FoxBalance.MAX_HEALTH-5,"paid foundation takes ordinary damage before restart");
            order(fox,UnitAction.MOVE,ORIGIN.offset(12,0,5));
            h.runAfterDelay(210,()->{
                h.assertTrue(near(fox,ORIGIN.offset(12,0,5)),"first JVM actually moves ground fox: "+describe(fox));
                order(fox,UnitAction.STOP,BlockPos.ZERO);
            });
            h.runAfterDelay(240,()->{
                assertFoundation(h,fox);assertLedger(h);
                h.assertTrue(fox.getMoveGoal().getMoveTarget()==null&&fox.getNavigation().isDone(),"stopped ordinary state before persistence");
                expected.putFloat("health",fox.getHealth());expected.putDouble("x",fox.getX());expected.putDouble("y",fox.getY());expected.putDouble("z",fox.getZ());
                try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
                System.out.println("DREAMSEEKERS_FOX_RESTART_PREPARED uuid="+fox.getUUID()+" health="+fox.getHealth()+" "+describe(fox));
                h.succeed(); // Normal server shutdown owns entity/RTS/resource saving; no manual entity save.
            });
        });
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM manifest exists");
        CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM");
        for(long key:expected.getLongArray("forcedChunks")){
            h.assertTrue(level.getForcedChunks().contains(key),"ordinary world loaded its own forced chunks");
            ChunkPos chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);
        }
        h.startSequence().thenWaitUntil(()->{
            Entity entity=level.getEntity(expected.getUUID("fox"));
            h.assertTrue(entity instanceof GoldenFoxUnit fox&&fox.tickCount>2,"wait ordinary asynchronous UUID load and real ticks");
        }).thenExecute(()->{
            GoldenFoxUnit fox=(GoldenFoxUnit)level.getEntity(expected.getUUID("fox"));
            assertFoundation(h,fox);assertLedger(h);
            h.assertTrue(fox.getScenarioRoleIndex()==87&&fox.getAnchor().equals(BlockPos.ZERO)&&fox.getOnDeathCommand().isEmpty(),"scenario and unanchored paid-unit state survive normal storage");
            h.assertTrue(fox.getHealth()==expected.getFloat("health")&&fox.getHealth()==FoxBalance.MAX_HEALTH-5,"health is not reset by native constructor/load");
            Vec3 saved=new Vec3(expected.getDouble("x"),expected.getDouble("y"),expected.getDouble("z"));
            h.assertTrue(fox.position().distanceToSqr(saved)<.2&&fox.getMoveGoal().getMoveTarget()==null&&fox.getFollowTarget()==null,"saved stopped position has no sudden wild or combat goal");
            h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(fox.getUUID())).count()==1,"exact UUID registered once");
            Vec3 start=fox.position();BlockPos destination=ORIGIN.offset(24,0,5);
            order(fox,UnitAction.MOVE,destination);
            for(int tick=1;tick<=220;tick++)h.runAfterDelay(tick,()->{
                h.assertTrue(level.noCollision(fox)&&!fox.isNoAi()&&!fox.isNoGravity()&&!fox.noPhysics,"second JVM preserves active solid ground body");
                h.assertTrue(fox.getY()<ORIGIN.getY()+1.6,"second JVM does not switch to flying controller");
            });
            h.runAfterDelay(230,()->{
                h.assertTrue(fox.position().distanceToSqr(start)>16&&near(fox,destination),"second JVM really obeys MOVE: "+describe(fox));
                order(fox,UnitAction.HOLD,BlockPos.ZERO);
            });
            h.runAfterDelay(270,()->{
                h.assertTrue(fox.getHoldPosition()&&fox.getMoveGoal().getMoveTarget()==null&&fox.getNavigation().isDone(),"post-restart HOLD ends paths");
                order(fox,UnitAction.STOP,BlockPos.ZERO);
            });
            // Command enqueue is not dispatch: observe a later genuine server tick.
            h.runAfterDelay(290,()->{
                assertFoundation(h,fox);assertLedger(h);
                h.assertTrue(fox.getItems().isEmpty()&&!fox.getHoldPosition()
                        &&fox.getMoveGoal().getMoveTarget()==null&&fox.getNavigation().isDone(),
                        "no passive gifts/cargo and genuinely dispatched STOP remains controllable");
                // Remove roster first, then entity, to avoid manufacturing a lost-all defeat during cleanup.
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));
                fox.discard();
                for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
                System.out.println("DREAMSEEKERS_FOX_RESTART_UUID_HEALTH_GROUND_NOAI_PASS uuid="+expected.getUUID("fox"));h.succeed();
            });
        });
    }
    private static void assertFoundation(GameTestHelper h,GoldenFoxUnit fox){
        h.assertTrue(fox.getOwnerName().equals(OWNER)&&Factions.getFaction(fox)==DreamseekersMod.DREAMSEEKERS,"paid owner and faction retained");
        h.assertTrue(!fox.isNoAi()&&!fox.isNoGravity()&&!fox.isFlyingUnit()&&!fox.noPhysics&&fox.isPersistenceRequired()&&!fox.canPickUpLoot(),"NoAI/gravity/collision/persistence remain paid-ground settings");
        h.assertTrue(fox.getNavigation() instanceof GroundPathNavigation&&fox.getAttributeValue(Attributes.MOVEMENT_SPEED)==FoxBalance.SPEED,"ground navigator and provisional speed retained");
        h.assertTrue(!((Object)fox instanceof AttackerUnit)&&!((Object)fox instanceof WorkerUnit)&&fox.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.FOX_BLESSING,com.dreamseekers.skill.UnitSkillAbility.FOX_DREAM_SEED))&&fox.getTarget()==null&&!fox.canGarrison()&&!fox.canUsePortal(),"only approved blessing and dream seed, no combat, work, portal or garrison capability");
    }
    private static void assertLedger(GameTestHelper h){
        Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER)).findFirst().orElseThrow();
        h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"restart/movement grants no currency and charges nothing twice");
    }
    private static void order(GoldenFoxUnit fox,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(OWNER,action,-1,new int[]{fox.getId()},pos,BlockPos.ZERO);}
    private static boolean near(GoldenFoxUnit fox,BlockPos p){return Math.abs(fox.getX()-(p.getX()+.5))<1.8&&Math.abs(fox.getZ()-(p.getZ()+.5))<1.8;}
    private static String describe(GoldenFoxUnit fox){return "pos="+fox.position()+" ticks="+fox.tickCount+" move="+fox.getMoveGoal().getMoveTarget()+" navDone="+fox.getNavigation().isDone();}
}
