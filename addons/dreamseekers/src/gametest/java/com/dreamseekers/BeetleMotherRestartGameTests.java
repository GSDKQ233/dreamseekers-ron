// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.beetlemother.BeetleMotherBalance;
import com.dreamseekers.unit.BeetleMotherUnit;
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
@GameTestHolder("dreamseekers_mother_restart")
@PrefixGameTestTemplate(false)
public final class BeetleMotherRestartGameTests {
    private static final String OWNER="MotherRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(14336,90,14336);
    private static final File MANIFEST=new File("beetlemother-restart-expectations.nbt");

    @GameTest(template="mother_unit_arena",batch="dreamseekers_mother_restart",timeoutTicks=600)
    public static void ordinaryRestartPreservesPaidIdentityAndNormalGroundAi(GameTestHelper h) throws Exception {
        String phase=System.getProperty("dreamseekers.beetlemother.test.phase","");
        if(phase.equals("prepare"))prepare(h);else if(phase.equals("verify"))verify(h);
        else throw new IllegalArgumentException("Unknown mother restart phase "+phase);
    }
    private static void prepare(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null&&!MANIFEST.exists(),"fresh isolated mother restart fixture");
        CompoundTag expected=new CompoundTag();
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int cx=896;cx<=897;cx++)for(int cz=896;cz<=897;cz++){
            long key=ChunkPos.asLong(cx,cz);h.assertTrue(!level.getForcedChunks().contains(key),"do not adopt another fixture's chunk");
            level.setChunkForced(cx,cz,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);
        // Keep natural falling terrain out without touching neighboring worlds or fixtures.
        for(int x=0;x<30;x++)for(int z=0;z<20;z++)level.setBlock(ORIGIN.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+30,level.getMaxBuildHeight(),ORIGIN.getZ()+20)).forEach(Entity::discard);
        // Seal lateral natural fluids before clearing the one-time interior. Roof alone cannot stop side flow.
        for(int x=0;x<30;x++)for(int z=0;z<20;z++)if(x==0||x==29||z==0||z==19)
            for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        for(int x=0;x<30;x++)for(int z=0;z<20;z++){
            level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            if(x>0&&x<29&&z>0&&z<19)for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));
        BeetleMotherUnit mother=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(level);
        h.assertTrue(mother!=null,"actual registered paid mother created");
        mother.setOwnerName(OWNER);mother.setScenarioRoleIndex(87);mother.setOnDeathCommand("");
        
        mother.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5,0,0);
        h.assertTrue(level.addFreshEntity(mother),"paid mother enters normal world entity storage");
        expected.putUUID("mother",mother.getUUID());
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother),"wait genuine ordinary ground ticks: "+describe(mother))).thenExecute(()->{
            h.assertTrue(mother.hurt(mother.damageSources().generic(),5)&&mother.getHealth()<BeetleMotherBalance.MAX_HEALTH,"paid foundation takes ordinary damage before restart");
            order(mother,UnitAction.MOVE,ORIGIN.offset(12,0,5));
            h.runAfterDelay(210,()->{
                h.assertTrue(near(mother,ORIGIN.offset(12,0,5)),"first JVM actually moves ground mother: "+describe(mother));
                order(mother,UnitAction.STOP,BlockPos.ZERO);
            });
            h.runAfterDelay(240,()->{
                assertFoundation(h,mother);assertLedger(h);
                h.assertTrue(mother.getMoveGoal().getMoveTarget()==null&&mother.getNavigation().isDone(),"stopped ordinary state before persistence");
                expected.putFloat("health",mother.getHealth());expected.putDouble("x",mother.getX());expected.putDouble("y",mother.getY());expected.putDouble("z",mother.getZ());
                try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
                System.out.println("DREAMSEEKERS_BEETLE_MOTHER_RESTART_PREPARED uuid="+mother.getUUID()+" health="+mother.getHealth()+" "+describe(mother));
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
            Entity entity=level.getEntity(expected.getUUID("mother"));
            h.assertTrue(entity instanceof BeetleMotherUnit mother&&published(h,mother),"wait ordinary asynchronous UUID load and real ticks");
        }).thenExecute(()->{
            BeetleMotherUnit mother=(BeetleMotherUnit)level.getEntity(expected.getUUID("mother"));
            assertFoundation(h,mother);assertLedger(h);
            h.assertTrue(mother.getScenarioRoleIndex()==87&&mother.getAnchor().equals(BlockPos.ZERO)&&mother.getOnDeathCommand().isEmpty(),"scenario and unanchored paid-unit state survive normal storage");
            h.assertTrue(mother.getHealth()==expected.getFloat("health")&&mother.getHealth()<BeetleMotherBalance.MAX_HEALTH,"health is not reset by native constructor/load");
            Vec3 saved=new Vec3(expected.getDouble("x"),expected.getDouble("y"),expected.getDouble("z"));
            h.assertTrue(mother.position().distanceToSqr(saved)<.2&&mother.getMoveGoal().getMoveTarget()==null&&mother.getFollowTarget()==null,"saved stopped position has no sudden wild or combat goal");
            h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(mother.getUUID())).count()==1,"exact UUID registered once");
            com.dreamseekers.unit.GoldenFoxUnit[] enemy={null};float[] enemyHp={0};Vec3[] attackStart={null};
            for(int x=1;x<29;x++)for(int z=1;z<19;z++)for(int y=1;y<10;y++)
                h.assertTrue(level.getBlockState(ORIGIN.offset(x,y,z)).isAir(),"restart MOVE fixture must be a dry enclosed room before issuing orders: "+ORIGIN.offset(x,y,z));
            Vec3 start=mother.position();BlockPos destination=ORIGIN.offset(24,0,5);
            order(mother,UnitAction.MOVE,destination);
            for(int tick=1;tick<=220;tick++)h.runAfterDelay(tick,()->{
                if(mother.tickCount<20||mother.tickCount%10==0)System.out.println("MOTHER_RESTART_MOVE_TRACE "+describe(mother)+" delta="+mother.getDeltaMovement()+" rts="+UnitServerEvents.rtsPathfinding+" path="+pathDescription(mother));
                h.assertTrue(level.noCollision(mother)&&!mother.isNoAi()&&!mother.isNoGravity()&&!mother.noPhysics,"second JVM preserves active solid ground body");
                h.assertTrue(mother.getY()<ORIGIN.getY()+1.6,"second JVM does not switch to flying controller");
            });
            h.runAfterDelay(230,()->{
                h.assertTrue(mother.position().distanceToSqr(start)>16&&near(mother,destination),"second JVM really obeys MOVE: "+describe(mother));
                order(mother,UnitAction.HOLD,BlockPos.ZERO);
            });
            h.runAfterDelay(270,()->{
                h.assertTrue(mother.getHoldPosition()&&mother.getMoveGoal().getMoveTarget()==null&&mother.getNavigation().isDone(),"post-restart HOLD ends paths");
                order(mother,UnitAction.STOP,BlockPos.ZERO);
            });
            // Command enqueue is not dispatch: observe a later genuine server tick.
            h.runAfterDelay(290,()->{
                assertFoundation(h,mother);assertLedger(h);
                h.assertTrue(mother.getItems().isEmpty()&&!mother.getHoldPosition()
                        &&mother.getMoveGoal().getMoveTarget()==null&&mother.getNavigation().isDone(),
                        "no passive gifts/cargo and genuinely dispatched STOP remains controllable");
                mother.setHoldPosition(true);
                PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER+"_Enemy",DreamseekersMod.DREAMSEEKERS));
                ResourcesServerEvents.resourcesList.add(new Resources(OWNER+"_Enemy",500,500,500));
                enemy[0]=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);enemy[0].setOwnerName(OWNER+"_Enemy");enemy[0].setNoAi(true);
                enemy[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);enemy[0].setHealth(500);
                enemy[0].moveTo(ORIGIN.getX()+26.5,ORIGIN.getY()+1,ORIGIN.getZ()+15.5);
                h.assertTrue(level.addFreshEntity(enemy[0]),"real second-JVM ground enemy created outside the width2 melee reach after movement/STOP checks");
            });
            h.runAfterDelay(315,()->{
                h.assertTrue(enemy[0].tickCount>2&&level.getEntity(enemy[0].getId())==enemy[0]&&level.getEntity(enemy[0].getUUID())==enemy[0],"post-restart target really published");
                enemyHp[0]=enemy[0].getHealth();attackStart[0]=mother.position();
                new com.solegendary.reignofnether.unit.UnitActionItem(OWNER,UnitAction.ATTACK,enemy[0].getId(),new int[]{mother.getId()},BlockPos.ZERO,BlockPos.ZERO).action(level);
            });
            for(int t=316;t<=420;t+=5)h.runAfterDelay(t,()->System.out.println("MOTHER_RESTART_ATTACK_TRACE "+describe(mother)+" hp="+enemy[0].getHealth()+" target="+(mother.getTarget()==enemy[0])+" hold="+mother.getHoldPosition()+" forced="+mother.getTargetGoal().forced+" enemy="+enemy[0].position()+" dist="+mother.distanceTo(enemy[0])+" movedSqr="+mother.position().distanceToSqr(attackStart[0])+" los="+mother.hasLineOfSight(enemy[0])+" path="+pathDescription(mother)));
            h.runAfterDelay(420,()->{
                h.assertTrue(enemy[0].getHealth()<enemyHp[0]&&mother.position().distanceToSqr(attackStart[0])>1,"loaded actual melee goal approaches and damages an enemy after restart: hp="+enemy[0].getHealth()+" before="+enemyHp[0]+" movedSqr="+mother.position().distanceToSqr(attackStart[0])+" "+describe(mother));
                assertLedger(h);
                // Remove roster first, then entity, avoiding synthetic defeat during fixture disposal.
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(OWNER));
                enemy[0].discard();mother.discard();
                for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
                System.out.println("DREAMSEEKERS_BEETLE_MOTHER_RESTART_UUID_HEALTH_GROUND_NOAI_PASS uuid="+expected.getUUID("mother"));h.succeed();
            });
        });
    }
    private static void assertFoundation(GameTestHelper h,BeetleMotherUnit mother){
        h.assertTrue(Math.abs(mother.getBbWidth()-2.0f)<.001&&Math.abs(mother.getBbHeight()-1.0f)<.001,"actual2x1 mother body retained");
        h.assertTrue(mother.getOwnerName().equals(OWNER)&&Factions.getFaction(mother)==DreamseekersMod.DREAMSEEKERS,"paid owner and faction retained");
        h.assertTrue(!mother.isNoAi()&&!mother.isNoGravity()&&!mother.isFlyingUnit()&&!mother.noPhysics&&mother.isPersistenceRequired()&&!mother.canPickUpLoot(),"NoAI/gravity/collision/persistence remain paid-ground settings");
        h.assertTrue(mother.getNavigation() instanceof GroundPathNavigation&&mother.getAttributeValue(Attributes.MOVEMENT_SPEED)==BeetleMotherBalance.SPEED,"ground navigator and provisional speed retained");
        h.assertTrue(((Object)mother instanceof AttackerUnit)&&!((Object)mother instanceof WorkerUnit)&&mother.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.MOTHER_BROOD,com.dreamseekers.skill.UnitSkillAbility.MOTHER_NEST))&&mother.getTarget()==null&&!mother.canGarrison(),"melee contract retained with the exact approved summon skill, no work or garrison capability");
    }
    private static void assertLedger(GameTestHelper h){
        Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER)).findFirst().orElseThrow();
        h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"restart/movement grants no currency and charges nothing twice");
    }
    private static void order(BeetleMotherUnit mother,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(OWNER,action,-1,new int[]{mother.getId()},pos,BlockPos.ZERO);}
    private static boolean near(BeetleMotherUnit mother,BlockPos p){return Math.abs(mother.getX()-(p.getX()+.5))<1.8&&Math.abs(mother.getZ()-(p.getZ()+.5))<1.8;}
    private static String pathDescription(BeetleMotherUnit unit){
        var p=unit.getNavigation().getPath();if(p==null)return "null";
        java.util.List<String> nodes=new java.util.ArrayList<>();for(int i=0;i<p.getNodeCount();i++)nodes.add(p.getNode(i).asBlockPos().toString());
        return "reach="+p.canReach()+",next="+p.getNextNodeIndex()+"/"+p.getNodeCount()+",target="+p.getTarget()+",nodes="+nodes;
    }
    private static String describe(BeetleMotherUnit mother){return "pos="+mother.position()+" ticks="+mother.tickCount+" move="+mother.getMoveGoal().getMoveTarget()+" navDone="+mother.getNavigation().isDone();}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

}
