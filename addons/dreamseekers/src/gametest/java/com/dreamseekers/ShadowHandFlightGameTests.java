// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.ShadowHandUnit;
import com.dreamseekers.unit.StoneSpiritWorker;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.ShadowHandEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual 0.9 x 1.2 ghost body, real simulation and navigation; never teleport the commanded actor. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowHandFlightGameTests {
    private static final String BATCH = "dreamseekers_shadow_hand_flight";
    private static final String PREFIX = "HandFlight_";
    private static final List<Entity> FIXTURES = new ArrayList<>();
    private static final java.util.Set<net.minecraft.world.level.ChunkPos> ADDED_FORCED_CHUNKS=new java.util.HashSet<>();

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
        for (Entity entity : FIXTURES) if (!entity.isRemoved()) entity.discard();
        FIXTURES.clear();
        for(var chunk:ADDED_FORCED_CHUNKS)level.setChunkForced(chunk.x,chunk.z,false);
        ADDED_FORCED_CHUNKS.clear();
    }

    @GameTest(template = "hand_unit_arena", batch = BATCH, timeoutTicks=740)
    public static void genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper helper) {
        floor(helper);
        SimulationTimeline timeline=new SimulationTimeline(helper);
        String owner = owner("Barrier");
        // Tall side rails rule out walking around the two-block obstacle; an open roof allows flight.
        for (int x = 2; x <= 21; x++) for (int y = 1; y <= 7; y++) {
            set(helper, x, y, 8, Blocks.STONE);
            set(helper, x, y, 12, Blocks.STONE);
        }
        for (int z = 9; z <= 11; z++) for (int y = 1; y <= 2; y++) set(helper, 12, y, z, Blocks.STONE);
        ShadowHandUnit worker = spawn(helper, owner, 5, 1, 10);
        BlockPos target = helper.absolutePos(new BlockPos(19, 0, 10));
        final double[] maxHeight = {worker.getY()};
        final boolean[] crossed = {false};
        final double floorY = helper.absolutePos(BlockPos.ZERO).getY();
        timeline.at(0,()->order(worker,UnitAction.MOVE,target));
        for (int tick = 1; tick <= 300; tick++) {
            timeline.at(tick, () -> {
                helper.assertTrue(worker.isAlive() && worker.isNoGravity() && !worker.noPhysics, "native flight retains gravity policy and normal collision");
                helper.assertTrue(helper.getLevel().noCollision(worker), "worker never enters solid collision boxes during flight");
                maxHeight[0] = Math.max(maxHeight[0], worker.getY());
                if (worker.getX() > helper.absolutePos(new BlockPos(13, 0, 0)).getX()) crossed[0] = true;
            });
        }
        for (int tick : new int[] {5, 80, 209, 260, 309}) {
            final int now = tick;
            timeline.at(tick, () -> System.out.println("DREAMSEEKERS_SHADOW_HAND_BARRIER_DIAGNOSTIC tick="
                    + now + " " + worker.position()+" target="+worker.getMoveGoal().getMoveTarget()+" nav="+worker.getNavigation().isDone()));
        }
        timeline.at(310, () -> {
            helper.assertTrue(worker instanceof ShadowHandEntity && worker.getNavigation() instanceof FlyingPathNavigation
                    && worker.getMoveControl() instanceof FlyingMoveControl && worker.isFlyingUnit(), "actual original shadow_hand, flying navigator/controller and RTS flying classification");
            helper.assertTrue(crossed[0] && maxHeight[0] >= floorY + 2.95, "crossed obstacle by gaining real altitude, not ground step: " + worker.position());
            helper.assertTrue(maxHeight[0] <= floorY + 5.5, "low-altitude order does not use stock ground+10 hovering");
            helper.assertTrue(Math.abs(worker.getX() - (target.getX()+.5)) < 2 && Math.abs(worker.getZ()-(target.getZ()+.5)) < 2,
                    "genuine flight arrives across barrier: " + worker.position());
            System.out.println("DREAMSEEKERS_SHADOW_HAND_BARRIER_PASS maxY=" + maxHeight[0] + " final=" + worker.position());
            helper.succeed();
        });
        timeline.start(worker);
    }


    @GameTest(template = "hand_unit_arena", batch = BATCH, timeoutTicks=820)
    public static void sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper helper) {
        floor(helper);
        SimulationTimeline timeline=new SimulationTimeline(helper);
        String owner = owner("Sealed");
        for (int x=3;x<=11;x++) for(int z=3;z<=11;z++) for(int y=1;y<=6;y++)
            if (x==3 || x==11 || z==3 || z==11 || y==6) set(helper,x,y,z,Blocks.STONE);
        ShadowHandUnit worker=spawn(helper,owner,8,1,7);
        BlockPos outside=helper.absolutePos(new BlockPos(14,0,7));
        BlockPos logPos=helper.absolutePos(new BlockPos(12,2,7));
        helper.getLevel().setBlock(logPos,Blocks.OAK_LOG.defaultBlockState(),2);
        timeline.at(0,()->order(worker,UnitAction.MOVE,outside));
        for(int tick=1;tick<=380;tick++) timeline.at(tick,()->{
            helper.assertTrue(helper.getLevel().noCollision(worker),"sealed route never phases into a solid block");
            helper.assertTrue(worker.getX()<helper.absolutePos(new BlockPos(11,0,0)).getX(),"sealed room cannot be escaped by teleport/clipping");
        });
        timeline.at(100,()->order(worker,UnitAction.MOVE,logPos));
        timeline.at(390,()->{
            helper.assertTrue(helper.getLevel().getBlockState(logPos).is(Blocks.OAK_LOG),"nearby but wall-occluded resource is not remotely harvested");
            helper.assertTrue(Resources.getTotalResourcesFromItems(worker.getItems()).wood==0,"sealed worker gains no inaccessible wood");
            System.out.println("DREAMSEEKERS_SHADOW_HAND_SEALED_WALL_PASS final="+worker.position());
            helper.succeed();
        });
        timeline.start(worker);
    }


    @GameTest(template = "hand_unit_arena", batch = BATCH, timeoutTicks=730)
    public static void stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper helper) {
        floor(helper);
        SimulationTimeline timeline=new SimulationTimeline(helper);
        ShadowHandUnit worker=spawn(helper,owner("Stop"),4,1,5);
        Vec3 start=worker.position();
        Vec3[] stopped={null};
        timeline.at(0,()->order(worker,UnitAction.MOVE,helper.absolutePos(new BlockPos(20,0,5))));
        timeline.at(35,()->{
            helper.assertTrue(worker.position().distanceToSqr(start)>1,"flight began before interruption");
            order(worker,UnitAction.MOVE,helper.absolutePos(new BlockPos(4,0,18)));
        });
        timeline.at(70,()->order(worker,UnitAction.STOP,BlockPos.ZERO));
        timeline.at(90,()->{
            stopped[0]=worker.position();
            helper.assertTrue(worker.getMoveGoal().getMoveTarget()==null && worker.getNavigation().isDone(),"STOP clears pending flight and navigation");
        });
        timeline.at(280,()->{
            helper.assertTrue(worker.position().distanceToSqr(stopped[0])<.20,"no stale order, drifting or wild random flight after STOP: "+worker.position()+" stop="+stopped[0]);
            helper.assertTrue(!((Object) worker instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit),"scout has no resource task");
            System.out.println("DREAMSEEKERS_SHADOW_HAND_STOP_PASS");
            helper.succeed();
        });
        timeline.start(worker);
    }


    @GameTest(template = "hand_unit_arena", batch = BATCH, timeoutTicks=890)
    public static void lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper helper) {
        floor(helper);
        SimulationTimeline timeline=new SimulationTimeline(helper);
        for (int x = 2; x <= 21; x++) for (int z = 8; z <= 12; z++) for (int y = 1; y <= 8; y++)
            if (x == 2 || x == 21 || z == 8 || z == 12) set(helper, x, y, z, Blocks.STONE);
        for (int z = 9; z <= 11; z++) for (int y = 1; y <= 6; y++) set(helper, 12, y, z, Blocks.STONE);
        ShadowHandUnit worker = spawn(helper, owner("LowCeiling"), 5, 1, 10);
        BlockPos target = helper.absolutePos(new BlockPos(19, 0, 10));
        double floorY = helper.absolutePos(BlockPos.ZERO).getY();
        double wallX = helper.absolutePos(new BlockPos(12, 0, 0)).getX();
        timeline.at(0,()->order(worker,UnitAction.MOVE,target));
        for (int tick = 1; tick <= 440; tick++) {
            final int sampleTick = tick;
            timeline.at(tick, () -> {
                helper.assertTrue(helper.getLevel().noCollision(worker), "tall-wall retry preserves body collision");
                helper.assertTrue(worker.getY() < floorY + 4.8, "repath cannot ratchet the low-altitude ceiling upward");
                if (sampleTick < 210) helper.assertTrue(worker.getX() < wallX, "cannot climb the six-block wall above its order's fixed altitude budget");
            });
        }
        timeline.at(210, () -> {
            for (int y = 1; y <= 2; y++) for(int z=9;z<=11;z++) set(helper,12,y,z,Blocks.AIR);
        });
        for (int tick : new int[] {5, 120, 209, 260, 449}) {
            final int now = tick;
            timeline.at(tick, () -> System.out.println("DREAMSEEKERS_SHADOW_HAND_LOW_CEILING_DIAGNOSTIC tick="
                    + now + " " + worker.position()+" target="+worker.getMoveGoal().getMoveTarget()+" nav="+worker.getNavigation().isDone()));
        }
        timeline.at(450, () -> {
            helper.assertTrue(Math.abs(worker.getX() - (target.getX() + .5)) < 2,
                    "same pending order recovers through a genuinely opened route: " + worker.position());
            System.out.println("DREAMSEEKERS_SHADOW_HAND_LOW_CEILING_RETRY_PASS final=" + worker.position());
            helper.succeed();
        });
        timeline.start(worker);
    }

    @GameTest(template="hand_unit_arena", batch=BATCH, timeoutTicks=1120)
    public static void queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper helper) {
        floor(helper);
        SimulationTimeline timeline=new SimulationTimeline(helper);
        ShadowHandUnit scout=spawn(helper,owner("Queue"),4,1,4);
        BlockPos first=helper.absolutePos(new BlockPos(17,0,4));
        BlockPos second=helper.absolutePos(new BlockPos(17,0,17));
        // Ordinary control fixture: a client cannot select an entity before server tracking publishes it.
        // The genuinely earlier production boundary is tested separately with the real producer.
        timeline.at(0,()->{
            helper.assertTrue(helper.getLevel().getEntity(scout.getId())==scout&&helper.getLevel().getEntity(scout.getUUID())==scout,
                    "both real lookups are published before ordinary player-style commands");
            order(scout,UnitAction.MOVE,first);
            UnitServerEvents.addActionItem(scout.getOwnerName(),UnitAction.MOVE,-1,new int[]{scout.getId()},second,BlockPos.ZERO,true);
            System.out.println("BONE_QUEUE issued-after-publication first="+first+" second="+second+" "+queueState(helper,scout));
        });
        final boolean[] passedFirst={false};
        final Vec3[] held={null};
        final double[] closestFirst={Double.POSITIVE_INFINITY};
        final BlockPos[] lastTarget={null};
        // Register upfront: retain publication timing before and after the real control precondition.
        for(int tick=1;tick<=12;tick++)helper.runAtTickTime(tick,()->System.out.println("BONE_QUEUE early "+queueState(helper,scout)));
        for(int tick=1;tick<=490;tick++) timeline.at(tick,()->{
            helper.assertTrue(helper.getLevel().noCollision(scout),"queued flight preserves collision");
            double dx=scout.getX()-(first.getX()+.5),dz=scout.getZ()-(first.getZ()+.5);
            closestFirst[0]=Math.min(closestFirst[0],dx*dx+dz*dz);
            if(!java.util.Objects.equals(lastTarget[0],scout.getMoveGoal().getMoveTarget())) {
                System.out.println("BONE_QUEUE target-change "+queueState(helper,scout));lastTarget[0]=scout.getMoveGoal().getMoveTarget();
            }
            if(Math.abs(dx)<2 && Math.abs(dz)<2)passedFirst[0]=true;
        });
        timeline.at(500,()->{
            helper.assertTrue(passedFirst[0] && Math.abs(scout.getX()-(second.getX()+.5))<2
                    && Math.abs(scout.getZ()-(second.getZ()+.5))<2,"shift queue traverses both waypoints in order: first="+passedFirst[0]+" closestFirstXZSq="+closestFirst[0]+" "+queueState(helper,scout));
            order(scout,UnitAction.HOLD,BlockPos.ZERO);
        });
        timeline.at(520,()->{
            held[0]=scout.position();
            helper.assertTrue(scout.getHoldPosition() && scout.getMoveGoal().getMoveTarget()==null,"HOLD clears movement without falling");
            UnitServerEvents.addActionItem(scout.getOwnerName(),UnitAction.MOVE,-1,new int[]{scout.getId()},first,BlockPos.ZERO,true);
            order(scout,UnitAction.STOP,BlockPos.ZERO);
        });
        timeline.at(650,()->{
            helper.assertTrue(scout.position().distanceToSqr(held[0])<.20,"STOP cancels queued command and stabilizes native hover");
            helper.assertTrue(scout.getMoveGoal().getMoveTarget()==null && scout.getNavigation().isDone() && !scout.getHoldPosition(),"STOP fully clears flight/hold");
            helper.assertTrue(UnitServerEvents.getUnitActionSlowQueue().stream().noneMatch(a->java.util.Arrays.stream(a.getUnitIds()).anyMatch(id->id==scout.getId())),"no stale queued command survives");
            System.out.println("DREAMSEEKERS_SHADOW_HAND_QUEUE_HOLD_STOP_PASS");
            helper.succeed();
        });
        timeline.start(scout);
    }

    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=1060)
    public static void sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h) {
        floor(h);ShadowHandUnit jelly=spawn(h,owner("SameTick"),4,1,4);
        BlockPos a=h.absolutePos(new BlockPos(17,0,4)),b=h.absolutePos(new BlockPos(4,0,17));
        h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2&&published(h,jelly),"real published same-tick fixture ready"))
            .thenExecute(()->{order(jelly,UnitAction.MOVE,a);order(jelly,UnitAction.MOVE,b);})
            .thenIdle(250).thenExecute(()->{
                h.assertTrue(Math.abs(jelly.getX()-(b.getX()+.5))<2&&Math.abs(jelly.getZ()-(b.getZ()+.5))<2,"same tick A to B arrives at final B: "+jelly.position());
                order(jelly,UnitAction.MOVE,a);order(jelly,UnitAction.MOVE,b);order(jelly,UnitAction.MOVE,a);
            }).thenIdle(330).thenExecute(()->{
                h.assertTrue(Math.abs(jelly.getX()-(a.getX()+.5))<2&&Math.abs(jelly.getZ()-(a.getZ()+.5))<2,"same tick A-B-A final A cannot be dropped as duplicate: "+jelly.position()+" target="+jelly.getMoveGoal().getMoveTarget()+" navDone="+jelly.getNavigation().isDone());
                h.assertTrue(h.getLevel().noCollision(jelly),"replacement keeps original body collision");
                System.out.println("DREAMSEEKERS_SHADOW_HAND_SAME_TICK_REPLACEMENT_PASS");
            }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=920)
    public static void tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(GameTestHelper h) {
        floor(h);
        for(int x=2;x<=21;x++)for(int z=8;z<=12;z++)for(int y=1;y<=3;y++)
            if(x==2||x==21||z==8||z==12||y==3)set(h,x,y,z,Blocks.STONE);
        for(int z=9;z<=11;z++)for(int y=1;y<=2;y++)set(h,12,y,z,Blocks.STONE);
        set(h,12,1,10,Blocks.AIR); // One block of height is too short for the genuine1.2-high body.
        ShadowHandUnit jelly=spawn(h,owner("BodyGap"),5,1,10);BlockPos target=h.absolutePos(new BlockPos(18,0,10));
        double wall=h.absolutePos(new BlockPos(12,0,0)).getX();
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2&&published(h,jelly),"published body fixture"))
                .thenExecute(()->order(jelly,UnitAction.MOVE,target));
        for(int i=0;i<180;i++)sequence.thenIdle(1).thenExecute(()->h.assertTrue(jelly.getX()<wall&&h.getLevel().noCollision(jelly),"1.2-high body cannot squeeze through one-high slot or roof/corners"));
        sequence.thenExecute(()->{set(h,12,2,10,Blocks.AIR);});
        for(int i=0;i<250;i++)sequence.thenIdle(1).thenExecute(()->h.assertTrue(h.getLevel().noCollision(jelly)&&jelly.getBoundingBox().maxY<=h.absolutePos(new BlockPos(0,3,0)).getY()+.001,"low roof and opened one-wide two-high route preserve the whole body"));
        sequence.thenExecute(()->{
            h.assertTrue(Math.abs(jelly.getX()-(target.getX()+.5))<2&&Math.abs(jelly.getZ()-(target.getZ()+.5))<2,"pending command recovers through opened height-correct gap: "+jelly.position());
            System.out.println("DREAMSEEKERS_SHADOW_HAND_ADULT_LOW_ROOF_GAP_PASS");
        }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=500)
    public static void malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h) {
        floor(h);ShadowHandUnit jelly=spawn(h,owner("BadTargets"),4,1,4);BlockPos pos=h.absolutePos(new BlockPos(16,1,16));
        ItemEntity item=new ItemEntity(h.getLevel(),pos.getX(),pos.getY(),pos.getZ(),new ItemStack(Items.DIAMOND,7));item.setNoGravity(true);
        Entity boat=EntityType.BOAT.create(h.getLevel());boat.moveTo(pos.getX()+3,pos.getY(),pos.getZ());
        Entity removed=EntityType.BOAT.create(h.getLevel());removed.moveTo(pos.getX()+5,pos.getY(),pos.getZ());
        for(Entity e:List.of(item,boat,removed)){FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"genuine nonliving target registered");}int removedId=removed.getId();removed.discard();Vec3[] start={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2&&published(h,jelly,item,boat),"real controller and actual nonliving targets are published"))
            .thenExecute(()->{
                start[0]=jelly.position();
                for(UnitAction action:List.of(UnitAction.FOLLOW,UnitAction.ATTACK))for(int id:new int[]{item.getId(),boat.getId(),removedId,Integer.MAX_VALUE}){
                    new com.solegendary.reignofnether.unit.UnitActionItem(jelly.getOwnerName(),action,id,new int[]{jelly.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                    h.assertTrue(jelly.getFollowTarget()==null&&jelly.getTarget()==null&&jelly.getMoveGoal().getMoveTarget()==null,"actual dispatcher rejects invalid target without cast crash");
                }
            }).thenIdle(40).thenExecute(()->{
                h.assertTrue(jelly.position().distanceToSqr(start[0])<.2&&item.isAlive()&&item.getItem().getCount()==7&&boat.isAlive()&&jelly.getItems().isEmpty(),"no delayed invalid pursuit or item grant");
                System.out.println("DREAMSEEKERS_SHADOW_HAND_INVALID_TARGET_PASS");
            }).thenSucceed();
    }

    /** Prebuilt schedule: GameTest world time is not proof that a newly loaded entity has simulated. */
    private static String queueState(GameTestHelper h,ShadowHandUnit e) {
        var path=e.getNavigation().getPath();
        return "worldTick="+h.getTick()+" entityTick="+e.tickCount+" id="+e.getId()+" registered="+(h.getLevel().getEntity(e.getId())==e)
                +" pos="+e.position()+" velocity="+e.getDeltaMovement()+" idle="+e.isIdle()+" move="+e.getMoveGoal().getMoveTarget()
                +" navDone="+e.getNavigation().isDone()+" navTarget="+e.getNavigation().getTargetPos()
                +" end="+(path==null?null:path.getEndNode())+" queued="+UnitServerEvents.getUnitActionSlowQueue().stream()
                .filter(a->java.util.Arrays.stream(a.getUnitIds()).anyMatch(id->id==e.getId())).map(a->a.getAction()+":"+a.getPreselectedBlockPos()).toList();
    }
    private static final class SimulationTimeline {
        private final GameTestHelper helper;
        private final java.util.TreeMap<Integer,java.util.List<Runnable>> events=new java.util.TreeMap<>();
        SimulationTimeline(GameTestHelper helper){this.helper=helper;}
        void at(int tick,Runnable action){events.computeIfAbsent(tick,k->new java.util.ArrayList<>()).add(action);}
        void start(ShadowHandUnit entity, Entity... targets){
            int[] start={0};
            var sequence=helper.startSequence().thenWaitUntil(()->helper.assertTrue(entity.tickCount>2&&published(helper,entity)&&published(helper,targets),"wait genuine entity simulation and ID/UUID publication readiness"))
                    .thenExecute(()->{start[0]=entity.tickCount;System.out.println("HAND_SIMULATION_READY id="+entity.getId()+" entityTick="+start[0]+" worldTestTick="+helper.getTick());});
            events.forEach((tick,actions)->sequence.thenWaitUntil(()->helper.assertTrue(entity.tickCount>=start[0]+tick,"wait actual entity ticks "+tick+" current="+entity.tickCount+" start="+start[0]))
                    .thenExecute(()->actions.forEach(Runnable::run)));
        }
    }

    private static boolean published(GameTestHelper h,Entity... entities) {
        for(Entity e:entities)if(h.getLevel().getEntity(e.getId())!=e||h.getLevel().getEntity(e.getUUID())!=e)return false;
        return true;
    }

    private static String owner(String suffix) {
        String owner=PREFIX+suffix;
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));
        return owner;
    }
    private static ShadowHandUnit spawn(GameTestHelper helper,String owner,int x,int y,int z) {
        ShadowHandUnit worker=DreamseekersMod.SHADOW_HAND_UNIT.get().create(helper.getLevel());
        helper.assertTrue(worker!=null,"shadow_hand fixture exists");
        worker.setOwnerName(owner);
        BlockPos p=helper.absolutePos(new BlockPos(x,y,z));
        worker.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        FIXTURES.add(worker);
        helper.assertTrue(helper.getLevel().addFreshEntity(worker),"worker enters world with AI enabled");
        return worker;
    }
    private static void order(ShadowHandUnit worker,UnitAction action,BlockPos target) {
        UnitServerEvents.addActionItem(worker.getOwnerName(),action,-1,new int[]{worker.getId()},target,BlockPos.ZERO);
    }
    private static void set(GameTestHelper helper,int x,int y,int z,net.minecraft.world.level.block.Block block) {
        helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x,y,z)),block.defaultBlockState(),2);
    }
    private static void floor(GameTestHelper helper) {
        // The core test world uses normal terrain: vanilla's structure clearing
        // can release natural gravel above the arena. Isolate this one-time
        // fixture setup, not production flight or per-tick collision assertions.
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        for(int i=-1;i<=24;i++)for(int y=1;y<=10;y++){
            set(helper,-1,y,i,Blocks.STONE);set(helper,24,y,i,Blocks.STONE);set(helper,i,y,-1,Blocks.STONE);set(helper,i,y,24,Blocks.STONE);
        }
        // Ordinary-control fixture only: keep the small arena ticking, like a player's loaded view.
        // Preserve pre-existing forced chunks and release only this batch's newly acquired tickets.
        // The separate fresh-section production publication probe deliberately does NOT use this.
        for(int cx=base.getX()>>4;cx<=(base.getX()+23)>>4;cx++)for(int cz=base.getZ()>>4;cz<=(base.getZ()+23)>>4;cz++){
            var chunk=new net.minecraft.world.level.ChunkPos(cx,cz);
            if(!helper.getLevel().getForcedChunks().contains(chunk.toLong())){
                ADDED_FORCED_CHUNKS.add(chunk);helper.getLevel().setChunkForced(cx,cz,true);
            }
        }
        for(int x=0;x<24;x++) for(int z=0;z<24;z++) set(helper,x,10,z,Blocks.STONE);
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(base.getX(),base.getY()+1,base.getZ(),
                        base.getX()+24,helper.getLevel().getMaxBuildHeight(),base.getZ()+24))
                .forEach(Entity::discard);
        for(int x=0;x<24;x++) for(int z=0;z<24;z++) {
            set(helper,x,0,z,Blocks.STONE);
            for(int y=1;y<10;y++) set(helper,x,y,z,Blocks.AIR);
        }
    }
}
