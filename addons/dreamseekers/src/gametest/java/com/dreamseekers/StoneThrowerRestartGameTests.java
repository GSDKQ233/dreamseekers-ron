// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.stone.StoneThrowerBalance;
import com.dreamseekers.unit.StoneSpiritThrowerUnit;
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
@GameTestHolder("dreamseekers_stone_restart")
@PrefixGameTestTemplate(false)
public final class StoneThrowerRestartGameTests {
    private static int expectedOre=345;
    private static final String OWNER="StoneRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(12288,90,12288);
    private static final File MANIFEST=new File("stone-restart-expectations.nbt");

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_stone_restart",timeoutTicks=600)
    public static void ordinaryRestartPreservesPaidIdentityAndNormalGroundAi(GameTestHelper h) throws Exception {
        String phase=System.getProperty("dreamseekers.stone.test.phase","");
        if(phase.equals("prepare"))prepare(h);else if(phase.equals("verify"))verify(h);
        else throw new IllegalArgumentException("Unknown stone thrower restart phase "+phase);
    }
    private static void prepare(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null&&!MANIFEST.exists(),"fresh isolated stone thrower restart fixture");
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
        // Seal lateral natural fluids before clearing the one-time interior. Roof alone cannot stop side flow.
        for(int x=0;x<30;x++)for(int z=0;z<20;z++)if(x==0||x==29||z==0||z==19)
            for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        for(int x=0;x<30;x++)for(int z=0;z<20;z++){
            level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            if(x>0&&x<29&&z>0&&z<19)for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));
        StoneSpiritThrowerUnit slime=DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(level);
        h.assertTrue(slime!=null,"actual registered paid slime created");
        slime.setOwnerName(OWNER);slime.setScenarioRoleIndex(87);slime.setOnDeathCommand("");
        slime.getPersistentData().putDouble("size",0);
        slime.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5,0,0);
        h.assertTrue(level.addFreshEntity(slime),"paid slime enters normal world entity storage");
        expected.putUUID("slime",slime.getUUID());
        var legacy=DreamseekersMod.STONE_SPIRIT_WORKER.get().create(level);legacy.setOwnerName(OWNER);legacy.setScenarioRoleIndex(31);legacy.setNoAi(true);
        legacy.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+15.5);
        legacy.getItems().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON,7));
        h.assertTrue(level.addFreshEntity(legacy),"old worker ID also enters ordinary entity storage, never converted");expected.putUUID("worker",legacy.getUUID());
        int settledOre=Resources.getTotalResourcesFromItems(legacy.getItems()).ore;
        h.assertTrue(settledOre==455,"seven actual raw iron valued by existing RoN resource table");
        expected.putInt("legacySettledOre",settledOre);expected.putInt("ledgerOreAfterRestart",345+settledOre);
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2&&legacy.tickCount>2&&level.getEntity(slime.getId())==slime&&level.getEntity(slime.getUUID())==slime&&level.getEntity(legacy.getId())==legacy&&level.getEntity(legacy.getUUID())==legacy,"wait genuine ordinary ground ticks: "+describe(slime))).thenExecute(()->{
            h.assertTrue(slime.hurt(slime.damageSources().generic(),5)&&slime.getHealth()<StoneThrowerBalance.MAX_HEALTH,"paid foundation takes ordinary damage before restart");
            order(slime,UnitAction.MOVE,ORIGIN.offset(12,0,5));
            h.runAfterDelay(210,()->{
                h.assertTrue(near(slime,ORIGIN.offset(12,0,5)),"first JVM actually moves ground slime: "+describe(slime));
                order(slime,UnitAction.STOP,BlockPos.ZERO);
            });
            h.runAfterDelay(240,()->{
                assertFoundation(h,slime);assertLedger(h);
                h.assertTrue(legacy.getItems().size()==1&&legacy.getItems().get(0).is(net.minecraft.world.item.Items.RAW_IRON)&&legacy.getItems().get(0).getCount()==7,"before shutdown legacy still carries the exact unpaid-in-live-ledger cargo");
                h.assertTrue(slime.getMoveGoal().getMoveTarget()==null&&slime.getNavigation().isDone(),"stopped ordinary state before persistence");
                expected.putFloat("health",slime.getHealth());expected.putDouble("x",slime.getX());expected.putDouble("y",slime.getY());expected.putDouble("z",slime.getZ());
                try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
                System.out.println("DREAMSEEKERS_STONE_THROWER_RESTART_PREPARED uuid="+slime.getUUID()+" health="+slime.getHealth()+" "+describe(slime));
                h.succeed(); // Normal server shutdown owns entity/RTS/resource saving; no manual entity save.
            });
        });
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM manifest exists");
        CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("legacySettledOre")==455&&expected.getInt("ledgerOreAfterRestart")==800,"prepare manifest records existing resource-settlement protocol");expectedOre=expected.getInt("ledgerOreAfterRestart");
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM");
        for(long key:expected.getLongArray("forcedChunks")){
            h.assertTrue(level.getForcedChunks().contains(key),"ordinary world loaded its own forced chunks");
            ChunkPos chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);
        }
        h.startSequence().thenWaitUntil(()->{
            Entity entity=level.getEntity(expected.getUUID("slime"));
            h.assertTrue(entity instanceof StoneSpiritThrowerUnit slime&&slime.tickCount>2,"wait ordinary asynchronous UUID load and real ticks");
            h.assertTrue(level.getEntity(expected.getUUID("worker")) instanceof com.dreamseekers.unit.StoneSpiritWorker worker&&worker.tickCount>2,"old worker UUID also loads through real world storage");
        }).thenExecute(()->{
            StoneSpiritThrowerUnit slime=(StoneSpiritThrowerUnit)level.getEntity(expected.getUUID("slime"));
            assertFoundation(h,slime);assertLedger(h);
            var legacy=(com.dreamseekers.unit.StoneSpiritWorker)level.getEntity(expected.getUUID("worker"));
            h.assertTrue(legacy.getType()==DreamseekersMod.STONE_SPIRIT_WORKER.get()&&legacy instanceof WorkerUnit&&legacy.getOwnerName().equals(OWNER)&&legacy.getScenarioRoleIndex()==31,"legacy worker registry identity, profession and scenario preserved separately");
            h.assertTrue(legacy.getItems().isEmpty(),"legacy valued cargo is settled exactly once into saved owner ledger, not also reloaded or converted to combat cargo");
            h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(legacy.getUUID())).count()==1,"old worker also registered exactly once");
            h.assertTrue(slime.getScenarioRoleIndex()==87&&slime.getAnchor().equals(BlockPos.ZERO)&&slime.getOnDeathCommand().isEmpty(),"scenario and unanchored paid-unit state survive normal storage");
            h.assertTrue(slime.getHealth()==expected.getFloat("health")&&slime.getHealth()<StoneThrowerBalance.MAX_HEALTH,"health is not reset by native constructor/load");
            Vec3 saved=new Vec3(expected.getDouble("x"),expected.getDouble("y"),expected.getDouble("z"));
            h.assertTrue(slime.position().distanceToSqr(saved)<.2&&slime.getMoveGoal().getMoveTarget()==null&&slime.getFollowTarget()==null,"saved stopped position has no sudden wild or combat goal");
            h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(slime.getUUID())).count()==1,"exact UUID registered once");
            com.dreamseekers.unit.GoldenFoxUnit[] enemy={null};float[] enemyHp={0};Vec3[] attackStart={null};double[] projectileTravel={0};
            for(int x=1;x<29;x++)for(int z=1;z<19;z++)for(int y=1;y<10;y++)
                h.assertTrue(level.getBlockState(ORIGIN.offset(x,y,z)).isAir(),"restart MOVE fixture must be a dry enclosed room before issuing orders: "+ORIGIN.offset(x,y,z));
            Vec3 start=slime.position();BlockPos destination=ORIGIN.offset(24,0,5);
            order(slime,UnitAction.MOVE,destination);
            for(int tick=1;tick<=220;tick++)h.runAfterDelay(tick,()->{
                if(slime.tickCount<20||slime.tickCount%10==0)System.out.println("STONE_RESTART_MOVE_TRACE "+describe(slime)+" delta="+slime.getDeltaMovement()+" rts="+UnitServerEvents.rtsPathfinding+" path="+pathDescription(slime));
                h.assertTrue(level.noCollision(slime)&&!slime.isNoAi()&&!slime.isNoGravity()&&!slime.noPhysics,"second JVM preserves active solid ground body");
                h.assertTrue(slime.getY()<ORIGIN.getY()+1.6,"second JVM does not switch to flying controller");
            });
            h.runAfterDelay(230,()->{
                h.assertTrue(slime.position().distanceToSqr(start)>16&&near(slime,destination),"second JVM really obeys MOVE: "+describe(slime));
                order(slime,UnitAction.HOLD,BlockPos.ZERO);
            });
            h.runAfterDelay(270,()->{
                h.assertTrue(slime.getHoldPosition()&&slime.getMoveGoal().getMoveTarget()==null&&slime.getNavigation().isDone(),"post-restart HOLD ends paths");
                order(slime,UnitAction.STOP,BlockPos.ZERO);
            });
            // Command enqueue is not dispatch: observe a later genuine server tick.
            h.runAfterDelay(290,()->{
                assertFoundation(h,slime);assertLedger(h);
                h.assertTrue(slime.getItems().isEmpty()&&!slime.getHoldPosition()
                        &&slime.getMoveGoal().getMoveTarget()==null&&slime.getNavigation().isDone(),
                        "no passive gifts/cargo and genuinely dispatched STOP remains controllable");
                slime.setHoldPosition(true);
                PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER+"_Enemy",DreamseekersMod.DREAMSEEKERS));
                ResourcesServerEvents.resourcesList.add(new Resources(OWNER+"_Enemy",500,500,500));
                enemy[0]=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);enemy[0].setOwnerName(OWNER+"_Enemy");enemy[0].setNoAi(true);
                enemy[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);enemy[0].setHealth(500);
                enemy[0].moveTo(ORIGIN.getX()+8.5,ORIGIN.getY()+1,ORIGIN.getZ()+10.5);
                h.assertTrue(level.addFreshEntity(enemy[0]),"real second-JVM ground enemy created after movement/STOP checks");
            });
            h.runAfterDelay(315,()->{
                h.assertTrue(enemy[0].tickCount>2&&level.getEntity(enemy[0].getId())==enemy[0]&&level.getEntity(enemy[0].getUUID())==enemy[0],"post-restart target really published");
                enemyHp[0]=enemy[0].getHealth();attackStart[0]=slime.position();
                new com.solegendary.reignofnether.unit.UnitActionItem(OWNER,UnitAction.ATTACK,enemy[0].getId(),new int[]{slime.getId()},BlockPos.ZERO,BlockPos.ZERO).action(level);
            });
            for(int tick=316;tick<420;tick++)h.runAfterDelay(tick,()->{
                for(var stone:level.getEntitiesOfClass(com.dreamseekers.stone.StoneProjectile.class,new AABB(ORIGIN,ORIGIN.offset(30,10,20))))
                    if(stone.getOwner()==slime)projectileTravel[0]=Math.max(projectileTravel[0],stone.position().distanceTo(slime.position()));
            });
            h.runAfterDelay(420,()->{
                h.assertTrue(enemy[0].getHealth()<enemyHp[0]&&slime.position().distanceToSqr(attackStart[0])>1&&projectileTravel[0]>1,"loaded actual ground ranged goal approaches and physically throws at enemy after restart");
                assertLedger(h);
                // Remove roster first, then entity, avoiding synthetic defeat during fixture disposal.
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(OWNER));
                enemy[0].discard();legacy.discard();slime.discard();
                for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
                System.out.println("DREAMSEEKERS_STONE_THROWER_RESTART_UUID_HEALTH_GROUND_NOAI_PASS uuid="+expected.getUUID("slime")+" legacy="+expected.getUUID("worker")+" projectileTravel="+projectileTravel[0]);h.succeed();
            });
        });
    }
    private static void assertFoundation(GameTestHelper h,StoneSpiritThrowerUnit slime){
        h.assertTrue(Math.abs(slime.getBbWidth()-.7f)<.001&&Math.abs(slime.getBbHeight()-1f)<.001&&slime.getPersistentData().getDouble("size")==0,"new fixed-size ground thrower survives without native growth");
        h.assertTrue(slime.getOwnerName().equals(OWNER)&&Factions.getFaction(slime)==DreamseekersMod.DREAMSEEKERS,"paid owner and faction retained");
        h.assertTrue(!slime.isNoAi()&&!slime.isNoGravity()&&!slime.isFlyingUnit()&&!slime.noPhysics&&slime.isPersistenceRequired()&&!slime.canPickUpLoot(),"NoAI/gravity/collision/persistence remain paid-ground settings");
        h.assertTrue(slime.getNavigation() instanceof GroundPathNavigation&&slime.getAttributeValue(Attributes.MOVEMENT_SPEED)==StoneThrowerBalance.SPEED,"ground navigator and provisional speed retained");
        h.assertTrue(((Object)slime instanceof AttackerUnit)&&!((Object)slime instanceof WorkerUnit)&&slime.getAbilities().get().equals(List.of(com.dreamseekers.skill.UnitSkillAbility.STONE_HEAVY_SHOT))&&slime.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.STONE_HEAVY_SHOT)==0&&slime.getTarget()==null&&slime.canGarrison()&&slime.getGarrisonGoal()!=null,"ranged contract retains exactly the approved unused heavy-shot active at zero cooldown with approved native garrison but without unapproved siege or work");
    }
    private static void assertLedger(GameTestHelper h){
        Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER)).findFirst().orElseThrow();
        h.assertTrue(r.food==123&&r.wood==234&&r.ore==expectedOre&&r.emerald==456,"exact native cargo-settlement ledger; movement grants no currency or duplicate settlement: actual="+r.food+","+r.wood+","+r.ore+","+r.emerald+" expectedOre="+expectedOre);
    }
    private static void order(StoneSpiritThrowerUnit slime,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(OWNER,action,-1,new int[]{slime.getId()},pos,BlockPos.ZERO);}
    private static boolean near(StoneSpiritThrowerUnit slime,BlockPos p){return Math.abs(slime.getX()-(p.getX()+.5))<1.8&&Math.abs(slime.getZ()-(p.getZ()+.5))<1.8;}
    private static String pathDescription(StoneSpiritThrowerUnit unit){
        var p=unit.getNavigation().getPath();if(p==null)return "null";
        java.util.List<String> nodes=new java.util.ArrayList<>();for(int i=0;i<p.getNodeCount();i++)nodes.add(p.getNode(i).asBlockPos().toString());
        return "reach="+p.canReach()+",next="+p.getNextNodeIndex()+"/"+p.getNodeCount()+",target="+p.getTarget()+",nodes="+nodes;
    }
    private static String describe(StoneSpiritThrowerUnit slime){return "pos="+slime.position()+" ticks="+slime.tickCount+" move="+slime.getMoveGoal().getMoveTarget()+" navDone="+slime.getNavigation().isDone();}
}
