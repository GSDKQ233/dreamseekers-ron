// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.shadow.ShadowSquealGhostBalance;
import com.dreamseekers.unit.ShadowSquealGhostUnit;
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
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
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
@GameTestHolder("dreamseekers_shadow_squeal_restart")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowSquealGhostRestartGameTests {
    private static final String OWNER="SquealRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(15360,90,15360);
    private static final File MANIFEST=new File("squeal-restart-expectations.nbt");

    private static com.dreamseekers.shadow.SquealWaveBolt loadedBolt;
    private static int loadedBoltJoins;
    private static boolean joinedInertNotRemoved;
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void loaded(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(!"verify".equals(System.getProperty("dreamseekers.shadowsqueal.test.phase"))||event.getLevel().isClientSide()||!MANIFEST.isFile())return;
        try{CompoundTag expected=NbtIo.readCompressed(MANIFEST);if(expected.hasUUID("armedBolt")&&event.getEntity().getUUID().equals(expected.getUUID("armedBolt"))){loadedBolt=(com.dreamseekers.shadow.SquealWaveBolt)event.getEntity();loadedBoltJoins++;joinedInertNotRemoved=!boltFlag(loadedBolt,"armed")&&boltFlag(loadedBolt,"spent")&&!loadedBolt.isRemoved();System.out.println("SQUEAL_LOADED_BOLT_FROM_NORMAL_STORAGE uuid="+loadedBolt.getUUID());}}catch(Exception e){throw new RuntimeException(e);}
    }
    @GameTest(template="squeal_unit_arena",batch="dreamseekers_shadow_squeal_restart",timeoutTicks=740)
    public static void ordinaryRestartPreservesPaidIdentityAndNormalFlightAi(GameTestHelper h) throws Exception {
        String phase=System.getProperty("dreamseekers.shadowsqueal.test.phase","");
        if(phase.equals("prepare"))prepare(h);else if(phase.equals("verify"))verify(h);
        else throw new IllegalArgumentException("Unknown mother restart phase "+phase);
    }
    private static void prepare(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null&&!MANIFEST.exists(),"fresh isolated mother restart fixture");
        CompoundTag expected=new CompoundTag();
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int cx=960;cx<=961;cx++)for(int cz=960;cz<=961;cz++){
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
        ShadowSquealGhostUnit mother=DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(level);
        h.assertTrue(mother!=null,"actual registered paid mother created");
        mother.setOwnerName(OWNER);mother.setScenarioRoleIndex(87);mother.setOnDeathCommand("");
        
        mother.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5,0,0);
        h.assertTrue(level.addFreshEntity(mother),"paid mother enters normal world entity storage");
        expected.putUUID("mother",mother.getUUID());
        PreparedTimeline timeline=new PreparedTimeline(h);
        timeline.at(0,unit->{
            h.assertTrue(unit.hurt(unit.damageSources().generic(),5)&&unit.getHealth()<ShadowSquealGhostBalance.MAX_HEALTH,"paid foundation takes ordinary damage before restart");
            order(unit,UnitAction.MOVE,ORIGIN.offset(12,0,5));
        });
        timeline.at(210,unit->{
            h.assertTrue(near(unit,ORIGIN.offset(12,0,5)),"first JVM actually moves flying ghost: "+describe(unit));
            order(unit,UnitAction.STOP,BlockPos.ZERO);
        });
        timeline.at(240,unit->{
            assertFoundation(h,unit);assertLedger(h);
            h.assertTrue(unit.getMoveGoal().getMoveTarget()==null&&unit.getNavigation().isDone(),"stopped ordinary state before persistence");
            expected.putFloat("health",unit.getHealth());expected.putDouble("x",unit.getX());expected.putDouble("y",unit.getY());expected.putDouble("z",unit.getZ());
            try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
            System.out.println("DREAMSEEKERS_SHADOW_SQUEAL_GHOST_RESTART_PREPARED uuid="+unit.getUUID()+" health="+unit.getHealth()+" "+describe(unit));
            java.util.function.Consumer<net.minecraftforge.event.server.ServerStoppingEvent> persist=event->{
                var target=DreamseekersMod.JELLYFISH_UNIT.get().create(level);target.setOwnerName(OWNER+"_UnsavedTarget");target.moveTo(unit.getX()+8,unit.getY(),unit.getZ());
                var bolt=new com.dreamseekers.shadow.SquealWaveBolt(DreamseekersMod.SQUEAL_WAVE_BOLT.get(),level);bolt.arm(unit,target);
                if(!level.addFreshEntity(bolt)||!bolt.isAddedToWorld()||bolt.tickCount!=0||!boltFlag(bolt,"armed")||boltFlag(bolt,"spent")||!validBoltCaster(bolt))throw new IllegalStateException("armed projectile ordinary stop insertion");
                expected.putUUID("armedBolt",bolt.getUUID());expected.putBoolean("armedAtStopping",true);
                try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
                System.out.println("SQUEAL_ARMED_BOLT_SAVED_AT_NORMAL_STOP uuid="+bolt.getUUID()+" tick="+bolt.tickCount);
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,net.minecraftforge.event.server.ServerStoppingEvent.class,persist);
            h.succeed(); // Normal shutdown owns real storage, never manual replacement deserialization.
        });
        timeline.start(()->mother);
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM manifest exists");
        CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM");
        for(long key:expected.getLongArray("forcedChunks")){
            h.assertTrue(level.getForcedChunks().contains(key),"ordinary world loaded its own forced chunks");
            ChunkPos chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);
        }
        PreparedTimeline timeline=new PreparedTimeline(h);
        com.dreamseekers.unit.GoldenFoxUnit[] enemy={null};float[] enemyHp={0};Vec3[] attackStart={null},start={null};double[] travel={0};
        BlockPos destination=ORIGIN.offset(24,0,5);
        timeline.at(0,mother->{
            assertFoundation(h,mother);assertLedger(h);
            h.assertTrue(expected.getBoolean("armedAtStopping")&&loadedBoltJoins==1&&loadedBolt!=null&&joinedInertNotRemoved&&loadedBolt.isRemoved()&&loadedBolt.getRemovalReason()==Entity.RemovalReason.DISCARDED,"ordinary saved projectile reloads once and actually expires unarmed");
            h.assertTrue(mother.getScenarioRoleIndex()==87&&mother.getAnchor().equals(BlockPos.ZERO)&&mother.getOnDeathCommand().isEmpty(),"scenario and unanchored paid-unit state survive normal storage");
            h.assertTrue(mother.getHealth()==expected.getFloat("health")&&mother.getHealth()<ShadowSquealGhostBalance.MAX_HEALTH,"health is not reset by native constructor/load");
            Vec3 saved=new Vec3(expected.getDouble("x"),expected.getDouble("y"),expected.getDouble("z"));
            h.assertTrue(mother.position().distanceToSqr(saved)<.2&&mother.getMoveGoal().getMoveTarget()==null&&mother.getFollowTarget()==null,"saved stopped position has no sudden wild or combat goal");
            h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(mother.getUUID())).count()==1,"exact UUID registered once");
            for(int x=1;x<29;x++)for(int z=1;z<19;z++)for(int y=1;y<10;y++)
                h.assertTrue(level.getBlockState(ORIGIN.offset(x,y,z)).isAir(),"restart MOVE fixture must be a dry enclosed room before issuing orders: "+ORIGIN.offset(x,y,z));
            start[0]=mother.position();
            order(mother,UnitAction.MOVE,destination);
        });
        for(int tick=1;tick<=220;tick++)timeline.at(tick,mother->{
                if(mother.tickCount<20||mother.tickCount%10==0)System.out.println("SQUEAL_RESTART_MOVE_TRACE "+describe(mother)+" delta="+mother.getDeltaMovement()+" rts="+UnitServerEvents.rtsPathfinding+" path="+pathDescription(mother));
                h.assertTrue(level.noCollision(mother)&&!mother.isNoAi()&&mother.isNoGravity()&&!mother.noPhysics,"second JVM preserves active solid flying body");
                h.assertTrue(mother.getY()<ORIGIN.getY()+3.6,"second JVM remains within the low-flight arrival band");
            });
            timeline.at(230,mother->{
                h.assertTrue(mother.position().distanceToSqr(start[0])>16&&near(mother,destination),"second JVM really obeys MOVE: "+describe(mother));
                order(mother,UnitAction.HOLD,BlockPos.ZERO);
            });
            timeline.at(270,mother->{
                h.assertTrue(mother.getHoldPosition()&&mother.getMoveGoal().getMoveTarget()==null&&mother.getNavigation().isDone(),"post-restart HOLD ends paths");
                order(mother,UnitAction.STOP,BlockPos.ZERO);
            });
            // Command enqueue is not dispatch: observe a later genuine server tick.
            timeline.at(290,mother->{
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
                h.assertTrue(level.addFreshEntity(enemy[0]),"real second-JVM ground enemy created outside the ordinary melee reach after movement/STOP checks");
            });
            timeline.at(315,mother->{
                h.assertTrue(enemy[0].tickCount>2&&level.getEntity(enemy[0].getId())==enemy[0]&&level.getEntity(enemy[0].getUUID())==enemy[0],"post-restart target really published");
                enemyHp[0]=enemy[0].getHealth();attackStart[0]=mother.position();
                new com.solegendary.reignofnether.unit.UnitActionItem(OWNER,UnitAction.ATTACK,enemy[0].getId(),new int[]{mother.getId()},BlockPos.ZERO,BlockPos.ZERO).action(level);
            });
            for(int t=316;t<=535;t++)timeline.at(t,mother->{for(Entity e:level.getAllEntities())if(e instanceof com.dreamseekers.shadow.SquealWaveBolt b&&b.getOwner()==mother)travel[0]=Math.max(travel[0],b.distanceTo(mother));});
            for(int t=316;t<=535;t+=5)timeline.at(t,mother->System.out.println("SQUEAL_RESTART_ATTACK_TRACE "+describe(mother)+" hp="+enemy[0].getHealth()+" target="+(mother.getTarget()==enemy[0])+" hold="+mother.getHoldPosition()+" forced="+mother.getTargetGoal().forced+" enemy="+enemy[0].position()+" dist="+mother.distanceTo(enemy[0])+" movedSqr="+mother.position().distanceToSqr(attackStart[0])+" los="+mother.hasLineOfSight(enemy[0])+" path="+pathDescription(mother)));
            timeline.at(535,mother->{
                h.assertTrue(enemy[0].getHealth()<enemyHp[0]&&travel[0]>1,"loaded actual ranged goal physically damages an enemy after restart: hp="+enemy[0].getHealth()+" travel="+travel[0]+" before="+enemyHp[0]+" movedSqr="+mother.position().distanceToSqr(attackStart[0])+" "+describe(mother));
                assertLedger(h);
                // Remove roster first, then entity, avoiding synthetic defeat during fixture disposal.
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(OWNER));
                enemy[0].discard();mother.discard();
                for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
                System.out.println("DREAMSEEKERS_SHADOW_SQUEAL_GHOST_RESTART_UUID_HEALTH_FLIGHT_NOAI_PASS uuid="+expected.getUUID("mother"));h.succeed();
            });
        timeline.start(()->level.getEntity(expected.getUUID("mother")) instanceof ShadowSquealGhostUnit unit ? unit : null);
    }
    /** All actions are registered before start; readiness and delays use genuine actor ticks. */
    private static final class PreparedTimeline {
        private final GameTestHelper helper;
        private final java.util.TreeMap<Integer,java.util.List<java.util.function.Consumer<ShadowSquealGhostUnit>>> events=new java.util.TreeMap<>();
        PreparedTimeline(GameTestHelper helper){this.helper=helper;}
        void at(int tick,java.util.function.Consumer<ShadowSquealGhostUnit> action){events.computeIfAbsent(tick,k->new java.util.ArrayList<>()).add(action);}
        void start(java.util.function.Supplier<ShadowSquealGhostUnit> supplier){
            ShadowSquealGhostUnit[] actor={null};int[] start={0};
            var sequence=helper.startSequence().thenWaitUntil(()->{
                var found=supplier.get();helper.assertTrue(found!=null&&published(helper,found),"wait ordinary UUID-loaded actor and real simulation");actor[0]=found;
            }).thenExecute(()->start[0]=actor[0].tickCount);
            events.forEach((tick,actions)->sequence.thenWaitUntil(()->helper.assertTrue(actor[0].tickCount>=start[0]+tick,"wait real actor ticks "+tick))
                .thenExecute(()->actions.forEach(action->action.accept(actor[0]))));
        }
    }
    private static boolean boltFlag(com.dreamseekers.shadow.SquealWaveBolt bolt,String name){try{var f=com.dreamseekers.shadow.SquealWaveBolt.class.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(bolt);}catch(Exception e){throw new RuntimeException(e);}}
    private static boolean validBoltCaster(com.dreamseekers.shadow.SquealWaveBolt bolt){try{var m=com.dreamseekers.shadow.SquealWaveBolt.class.getDeclaredMethod("validCaster");m.setAccessible(true);return (boolean)m.invoke(bolt);}catch(Exception e){throw new RuntimeException(e);}}
    private static void assertFoundation(GameTestHelper h,ShadowSquealGhostUnit mother){
        h.assertTrue(Math.abs(mother.getBbWidth()-.7f)<.001&&Math.abs(mother.getBbHeight()-1.2f)<.001,"actual0.7x1.2 mother body retained");
        h.assertTrue(mother.getOwnerName().equals(OWNER)&&Factions.getFaction(mother)==DreamseekersMod.DREAMSEEKERS,"paid owner and faction retained");
        h.assertTrue(!mother.isNoAi()&&mother.isNoGravity()&&mother.isFlyingUnit()&&!mother.noPhysics&&mother.isPersistenceRequired()&&!mother.canPickUpLoot(),"NoAI/gravity/collision/persistence remain paid-ground settings");
        h.assertTrue(mother.getNavigation() instanceof FlyingPathNavigation&&mother.getAttributeValue(Attributes.MOVEMENT_SPEED)==ShadowSquealGhostBalance.SPEED,"flying navigator and provisional speed retained");
        h.assertTrue(((Object)mother instanceof AttackerUnit)&&!((Object)mother instanceof WorkerUnit)&&mother.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.SQUEAL_SUPPRESSION_WAVE))&&mother.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.SQUEAL_SUPPRESSION_WAVE)==0&&mother.getTarget()==null&&!mother.canGarrison()&&mother.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal&&mother.getAttackBuildingGoal()==null,"ranged contract retained without unapproved skills, work or garrison capability");
    }
    private static void assertLedger(GameTestHelper h){
        Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER)).findFirst().orElseThrow();
        h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"restart/movement grants no currency and charges nothing twice");
    }
    private static void order(ShadowSquealGhostUnit mother,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(OWNER,action,-1,new int[]{mother.getId()},pos,BlockPos.ZERO);}
    private static boolean near(ShadowSquealGhostUnit mother,BlockPos p){return Math.abs(mother.getX()-(p.getX()+.5))<1.8&&Math.abs(mother.getZ()-(p.getZ()+.5))<1.8;}
    private static String pathDescription(ShadowSquealGhostUnit unit){
        var p=unit.getNavigation().getPath();if(p==null)return "null";
        java.util.List<String> nodes=new java.util.ArrayList<>();for(int i=0;i<p.getNodeCount();i++)nodes.add(p.getNode(i).asBlockPos().toString());
        return "reach="+p.canReach()+",next="+p.getNextNodeIndex()+"/"+p.getNodeCount()+",target="+p.getTarget()+",nodes="+nodes;
    }
    private static String describe(ShadowSquealGhostUnit mother){return "pos="+mother.position()+" ticks="+mother.tickCount+" move="+mother.getMoveGoal().getMoveTarget()+" navDone="+mother.getNavigation().isDone();}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

}
