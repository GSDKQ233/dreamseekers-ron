// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.shadow.ShadowGolemBalance;
import com.dreamseekers.unit.ShadowGolemUnit;
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
@GameTestHolder("dreamseekers_shadow_golem_restart")
@PrefixGameTestTemplate(false)
public final class ShadowGolemRestartGameTests {
    private static final String OWNER="GolemRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(23552,90,23552);
    private static final File MANIFEST=new File("shadow_golem-restart-expectations.nbt");

    @GameTest(template="golem_unit_arena",batch="dreamseekers_shadow_golem_restart",timeoutTicks=600)
    public static void ordinaryRestartPreservesPaidIdentityAndNormalGroundAi(GameTestHelper h) throws Exception {
        String phase=System.getProperty("dreamseekers.shadow_golem.test.phase","");
        if(phase.equals("prepare"))prepare(h);else if(phase.equals("verify"))verify(h);
        else throw new IllegalArgumentException("Unknown terror restart phase "+phase);
    }
    private static void prepare(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null&&!MANIFEST.exists(),"fresh isolated terror restart fixture");
        CompoundTag expected=new CompoundTag();
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int cx=1472;cx<=1473;cx++)for(int cz=1472;cz<=1473;cz++){
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
        ShadowGolemUnit terror=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(level);
        h.assertTrue(terror!=null,"actual registered paid terror created");
        terror.setOwnerName(OWNER);terror.setScenarioRoleIndex(87);terror.setOnDeathCommand("");
        
        terror.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5,0,0);
        h.assertTrue(level.addFreshEntity(terror),"paid terror enters normal world entity storage");
        expected.putUUID("terror",terror.getUUID());
        var timeline=new ShadowGolemFixture.Timeline(h);
        timeline.at(0,()->{h.assertTrue(terror.hurt(terror.damageSources().generic(),5)&&terror.getHealth()<ShadowGolemBalance.MAX_HEALTH,"ordinary real damage");order(terror,UnitAction.MOVE,ORIGIN.offset(12,0,5));});
        timeline.at(210,()->{h.assertTrue(near(terror,ORIGIN.offset(12,0,5)),"first JVM actual MOVE: "+describe(terror));order(terror,UnitAction.STOP,BlockPos.ZERO);});
        timeline.at(240,()->{assertFoundation(h,terror);assertLedger(h);h.assertTrue(terror.getMoveGoal().getMoveTarget()==null&&terror.getNavigation().isDone(),"ordinary STOP before save");expected.putFloat("health",terror.getHealth());expected.putDouble("x",terror.getX());expected.putDouble("y",terror.getY());expected.putDouble("z",terror.getZ());try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}System.out.println("DREAMSEEKERS_SHADOW_GOLEM_RESTART_PREPARED uuid="+terror.getUUID()+" health="+terror.getHealth());});
        timeline.start(terror,()->published(h,terror));
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM manifest exists");
        CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM");
        for(long key:expected.getLongArray("forcedChunks")){
            h.assertTrue(level.getForcedChunks().contains(key),"ordinary world loaded its own forced chunks");
            ChunkPos chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);
        }
        ShadowGolemUnit[] ref={null};com.dreamseekers.unit.GoldenFoxUnit[] enemy={null};Vec3[] start={null},attackStart={null};float[] enemyHp={0};
        var timeline=new ShadowGolemFixture.Timeline(h);
        timeline.at(0,()->{var terror=ref[0];assertFoundation(h,terror);assertLedger(h);h.assertTrue(terror.getScenarioRoleIndex()==87&&terror.getAnchor().equals(BlockPos.ZERO)&&terror.getOnDeathCommand().isEmpty(),"normal scenario and command data persist");h.assertTrue(terror.getHealth()==expected.getFloat("health")&&terror.getHealth()<ShadowGolemBalance.MAX_HEALTH,"saved damage not reset");h.assertTrue(terror.position().distanceToSqr(new Vec3(expected.getDouble("x"),expected.getDouble("y"),expected.getDouble("z")))<.2&&terror.getMoveGoal().getMoveTarget()==null&&terror.getFollowTarget()==null,"saved stopped state");h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(terror.getUUID())).count()==1,"one exact loaded UUID");for(int x=1;x<29;x++)for(int z=1;z<19;z++)for(int y=1;y<10;y++)h.assertTrue(level.getBlockState(ORIGIN.offset(x,y,z)).isAir(),"dry restart room");start[0]=terror.position();order(terror,UnitAction.MOVE,ORIGIN.offset(24,0,5));});
        for(int tick=1;tick<=220;tick++)timeline.at(tick,()->{var terror=ref[0];h.assertTrue(level.noCollision(terror)&&!terror.isNoAi()&&!terror.isNoGravity()&&!terror.noPhysics&&terror.getY()<ORIGIN.getY()+1.6,"second JVM active collision-safe ground simulation");});
        timeline.at(230,()->{var terror=ref[0];h.assertTrue(terror.position().distanceToSqr(start[0])>16&&near(terror,ORIGIN.offset(24,0,5)),"second JVM actually obeys MOVE: "+describe(terror));order(terror,UnitAction.HOLD,BlockPos.ZERO);});
        timeline.at(270,()->{var terror=ref[0];h.assertTrue(terror.getHoldPosition()&&terror.getMoveGoal().getMoveTarget()==null&&terror.getNavigation().isDone(),"real post-restart HOLD");order(terror,UnitAction.STOP,BlockPos.ZERO);});
        timeline.at(290,()->{var terror=ref[0];assertFoundation(h,terror);assertLedger(h);h.assertTrue(!terror.getHoldPosition()&&terror.getMoveGoal().getMoveTarget()==null&&terror.getItems().isEmpty(),"real post-restart STOP and no free cargo");terror.setHoldPosition(true);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER+"_Enemy",DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(OWNER+"_Enemy",500,500,500));enemy[0]=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);enemy[0].setOwnerName(OWNER+"_Enemy");enemy[0].setNoAi(true);enemy[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);enemy[0].setHealth(500);enemy[0].moveTo(ORIGIN.getX()+26.5,ORIGIN.getY()+1,ORIGIN.getZ()+15.5);h.assertTrue(level.addFreshEntity(enemy[0]),"post-restart real distant enemy");});
        timeline.at(315,()->{var terror=ref[0];h.assertTrue(published(h,enemy[0]),"real target published before attack");enemyHp[0]=enemy[0].getHealth();attackStart[0]=terror.position();new com.solegendary.reignofnether.unit.UnitActionItem(OWNER,UnitAction.ATTACK,enemy[0].getId(),new int[]{terror.getId()},BlockPos.ZERO,BlockPos.ZERO).action(level);});
        timeline.at(440,()->{var terror=ref[0];h.assertTrue(enemy[0].getHealth()<enemyHp[0]&&terror.position().distanceToSqr(attackStart[0])>1,"loaded real melee moves and damages: hp="+enemy[0].getHealth()+" moved="+terror.position().distanceToSqr(attackStart[0]));assertLedger(h);System.out.println("DREAMSEEKERS_SHADOW_GOLEM_RESTART_UUID_HEALTH_GROUND_NOAI_PASS uuid="+terror.getUUID());});
        // Disposal after last actor-relative wait; no manual serialization/tick is substituted.
        timeline.at(441,()->{PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(OWNER));enemy[0].discard();ref[0].discard();for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}});
        timeline.start(()->ref[0],()->{var e=level.getEntity(expected.getUUID("terror"));if(e instanceof ShadowGolemUnit u&&published(h,u)){ref[0]=u;return true;}return false;});
    }
    private static void assertFoundation(GameTestHelper h,ShadowGolemUnit terror){
        h.assertTrue(Math.abs(terror.getBbWidth()-2.2f)<.001&&Math.abs(terror.getBbHeight()-3.5f)<.001,"actual2.2x3.5 terror body retained");
        h.assertTrue(terror.getOwnerName().equals(OWNER)&&Factions.getFaction(terror)==DreamseekersMod.DREAMSEEKERS,"paid owner and faction retained");
        h.assertTrue(!terror.isNoAi()&&!terror.isNoGravity()&&!terror.isFlyingUnit()&&!terror.noPhysics&&terror.isPersistenceRequired()&&!terror.canPickUpLoot(),"NoAI/gravity/collision/persistence remain paid-ground settings");
        h.assertTrue(terror.getNavigation() instanceof GroundPathNavigation&&terror.getAttributeValue(Attributes.MOVEMENT_SPEED)==ShadowGolemBalance.SPEED,"ground navigator and provisional speed retained");
        h.assertTrue(((Object)terror instanceof AttackerUnit)&&!((Object)terror instanceof WorkerUnit)&&terror.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.SHADOW_GOLEM_STRIKE))&&terror.getTarget()==null&&!terror.canGarrison(),"melee contract retained with only the authorized strike, no work or garrison capability");
    }
    private static void assertLedger(GameTestHelper h){
        Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER)).findFirst().orElseThrow();
        h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"restart/movement grants no currency and charges nothing twice");
    }
    private static void order(ShadowGolemUnit terror,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(OWNER,action,-1,new int[]{terror.getId()},pos,BlockPos.ZERO);}
    private static boolean near(ShadowGolemUnit terror,BlockPos p){return Math.abs(terror.getX()-(p.getX()+.5))<1.8&&Math.abs(terror.getZ()-(p.getZ()+.5))<1.8;}
    private static String pathDescription(ShadowGolemUnit unit){
        var p=unit.getNavigation().getPath();if(p==null)return "null";
        java.util.List<String> nodes=new java.util.ArrayList<>();for(int i=0;i<p.getNodeCount();i++)nodes.add(p.getNode(i).asBlockPos().toString());
        return "reach="+p.canReach()+",next="+p.getNextNodeIndex()+"/"+p.getNodeCount()+",target="+p.getTarget()+",nodes="+nodes;
    }
    private static String describe(ShadowGolemUnit terror){return "pos="+terror.position()+" ticks="+terror.tickCount+" move="+terror.getMoveGoal().getMoveTarget()+" navDone="+terror.getNavigation().isDone();}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

}
