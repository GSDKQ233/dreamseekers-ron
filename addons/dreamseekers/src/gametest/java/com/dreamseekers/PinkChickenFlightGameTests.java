// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.PinkChickenScout;
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
import net.pasterdream.entity.PinkChickenEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real ticking tests: movement orders never manually move the worker after fixture spawn. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PinkChickenFlightGameTests {
    private static final String BATCH = "dreamseekers_scout_flight";
    private static final String PREFIX = "ScoutFlight_";
    private static final List<Entity> FIXTURES = new ArrayList<>();

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (Entity entity : FIXTURES) if (!entity.isRemoved()) entity.discard();
        FIXTURES.clear();
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 340)
    public static void genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper helper) {
        floor(helper);
        String owner = owner("Barrier");
        // Tall side rails rule out walking around the two-block obstacle; an open roof allows flight.
        for (int x = 2; x <= 21; x++) for (int y = 1; y <= 7; y++) {
            set(helper, x, y, 8, Blocks.STONE);
            set(helper, x, y, 12, Blocks.STONE);
        }
        for (int z = 9; z <= 11; z++) for (int y = 1; y <= 2; y++) set(helper, 12, y, z, Blocks.STONE);
        PinkChickenScout worker = spawn(helper, owner, 5, 1, 10);
        BlockPos target = helper.absolutePos(new BlockPos(19, 0, 10));
        final double[] maxHeight = {worker.getY()};
        final boolean[] crossed = {false};
        final double floorY = helper.absolutePos(BlockPos.ZERO).getY();
        order(worker, UnitAction.MOVE, target);
        for (int tick = 1; tick <= 300; tick++) {
            helper.runAtTickTime(tick, () -> {
                helper.assertTrue(worker.isAlive() && worker.isNoGravity() && !worker.noPhysics, "native flight retains gravity policy and normal collision");
                helper.assertTrue(helper.getLevel().noCollision(worker), "worker never enters solid collision boxes during flight");
                maxHeight[0] = Math.max(maxHeight[0], worker.getY());
                if (worker.getX() > helper.absolutePos(new BlockPos(13, 0, 0)).getX()) crossed[0] = true;
            });
        }
        for (int tick : new int[] {5, 80, 209, 260, 309}) {
            final int now = tick;
            helper.runAtTickTime(tick, () -> System.out.println("DREAMSEEKERS_SCOUT_BARRIER_DIAGNOSTIC tick="
                    + now + " " + worker.position()+" target="+worker.getMoveGoal().getMoveTarget()+" nav="+worker.getNavigation().isDone()));
        }
        helper.runAtTickTime(310, () -> {
            helper.assertTrue(worker instanceof PinkChickenEntity && worker.getNavigation() instanceof FlyingPathNavigation
                    && worker.getMoveControl() instanceof FlyingMoveControl && worker.isFlyingUnit(), "actual original firefly, flying navigator/controller and RTS flying classification");
            helper.assertTrue(crossed[0] && maxHeight[0] >= floorY + 2.95, "crossed obstacle by gaining real altitude, not ground step: " + worker.position());
            helper.assertTrue(maxHeight[0] <= floorY + 5.5, "low-altitude order does not use stock ground+10 hovering");
            helper.assertTrue(Math.abs(worker.getX() - (target.getX()+.5)) < 2 && Math.abs(worker.getZ()-(target.getZ()+.5)) < 2,
                    "genuine flight arrives across barrier: " + worker.position());
            System.out.println("DREAMSEEKERS_SCOUT_BARRIER_PASS maxY=" + maxHeight[0] + " final=" + worker.position());
            helper.succeed();
        });
    }


    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 420)
    public static void sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper helper) {
        floor(helper);
        String owner = owner("Sealed");
        for (int x=3;x<=11;x++) for(int z=3;z<=11;z++) for(int y=1;y<=6;y++)
            if (x==3 || x==11 || z==3 || z==11 || y==6) set(helper,x,y,z,Blocks.STONE);
        PinkChickenScout worker=spawn(helper,owner,8,1,7);
        BlockPos outside=helper.absolutePos(new BlockPos(14,0,7));
        BlockPos logPos=helper.absolutePos(new BlockPos(12,2,7));
        helper.getLevel().setBlock(logPos,Blocks.OAK_LOG.defaultBlockState(),2);
        order(worker,UnitAction.MOVE,outside);
        for(int tick=1;tick<=380;tick++) helper.runAtTickTime(tick,()->{
            helper.assertTrue(helper.getLevel().noCollision(worker),"sealed route never phases into a solid block");
            helper.assertTrue(worker.getX()<helper.absolutePos(new BlockPos(11,0,0)).getX(),"sealed room cannot be escaped by teleport/clipping");
        });
        helper.runAtTickTime(100,()->order(worker,UnitAction.MOVE,logPos));
        helper.runAtTickTime(390,()->{
            helper.assertTrue(helper.getLevel().getBlockState(logPos).is(Blocks.OAK_LOG),"nearby but wall-occluded resource is not remotely harvested");
            helper.assertTrue(Resources.getTotalResourcesFromItems(worker.getItems()).wood==0,"sealed worker gains no inaccessible wood");
            System.out.println("DREAMSEEKERS_SCOUT_SEALED_WALL_PASS final="+worker.position());
            helper.succeed();
        });
    }


    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 330)
    public static void stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper helper) {
        floor(helper);
        PinkChickenScout worker=spawn(helper,owner("Stop"),4,1,5);
        Vec3 start=worker.position();
        Vec3[] stopped={null};
        order(worker,UnitAction.MOVE,helper.absolutePos(new BlockPos(20,0,5)));
        helper.runAtTickTime(35,()->{
            helper.assertTrue(worker.position().distanceToSqr(start)>1,"flight began before interruption");
            order(worker,UnitAction.MOVE,helper.absolutePos(new BlockPos(4,0,18)));
        });
        helper.runAtTickTime(70,()->order(worker,UnitAction.STOP,BlockPos.ZERO));
        helper.runAtTickTime(90,()->{
            stopped[0]=worker.position();
            helper.assertTrue(worker.getMoveGoal().getMoveTarget()==null && worker.getNavigation().isDone(),"STOP clears pending flight and navigation");
        });
        helper.runAtTickTime(280,()->{
            helper.assertTrue(worker.position().distanceToSqr(stopped[0])<.20,"no stale order, drifting or wild random flight after STOP: "+worker.position()+" stop="+stopped[0]);
            helper.assertTrue(!((Object) worker instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit),"scout has no resource task");
            System.out.println("DREAMSEEKERS_SCOUT_STOP_PASS");
            helper.succeed();
        });
    }


    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 490)
    public static void lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper helper) {
        floor(helper);
        for (int x = 2; x <= 21; x++) for (int z = 8; z <= 12; z++) for (int y = 1; y <= 8; y++)
            if (x == 2 || x == 21 || z == 8 || z == 12) set(helper, x, y, z, Blocks.STONE);
        for (int z = 9; z <= 11; z++) for (int y = 1; y <= 6; y++) set(helper, 12, y, z, Blocks.STONE);
        PinkChickenScout worker = spawn(helper, owner("LowCeiling"), 5, 1, 10);
        BlockPos target = helper.absolutePos(new BlockPos(19, 0, 10));
        double floorY = helper.absolutePos(BlockPos.ZERO).getY();
        double wallX = helper.absolutePos(new BlockPos(12, 0, 0)).getX();
        order(worker, UnitAction.MOVE, target);
        for (int tick = 1; tick <= 440; tick++) {
            final int sampleTick = tick;
            helper.runAtTickTime(tick, () -> {
                helper.assertTrue(helper.getLevel().noCollision(worker), "tall-wall retry preserves body collision");
                helper.assertTrue(worker.getY() < floorY + 4.8, "repath cannot ratchet the low-altitude ceiling upward");
                if (sampleTick < 210) helper.assertTrue(worker.getX() < wallX, "cannot climb the six-block wall above its order's fixed altitude budget");
            });
        }
        helper.runAtTickTime(210, () -> {
            for (int y = 1; y <= 2; y++) set(helper, 12, y, 10, Blocks.AIR);
        });
        for (int tick : new int[] {5, 120, 209, 260, 449}) {
            final int now = tick;
            helper.runAtTickTime(tick, () -> System.out.println("DREAMSEEKERS_SCOUT_LOW_CEILING_DIAGNOSTIC tick="
                    + now + " " + worker.position()+" target="+worker.getMoveGoal().getMoveTarget()+" nav="+worker.getNavigation().isDone()));
        }
        helper.runAtTickTime(450, () -> {
            helper.assertTrue(Math.abs(worker.getX() - (target.getX() + .5)) < 2,
                    "same pending order recovers through a genuinely opened route: " + worker.position());
            System.out.println("DREAMSEEKERS_SCOUT_LOW_CEILING_RETRY_PASS final=" + worker.position());
            helper.succeed();
        });
    }

    @GameTest(template="unit_isolation_arena", batch=BATCH, timeoutTicks=560)
    public static void queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper helper) {
        floor(helper);
        PinkChickenScout scout=spawn(helper,owner("Queue"),4,1,4);
        BlockPos first=helper.absolutePos(new BlockPos(17,0,4));
        BlockPos second=helper.absolutePos(new BlockPos(17,0,17));
        order(scout,UnitAction.MOVE,first);
        UnitServerEvents.addActionItem(scout.getOwnerName(),UnitAction.MOVE,-1,new int[]{scout.getId()},second,BlockPos.ZERO,true);
        final boolean[] passedFirst={false};
        final Vec3[] held={null};
        for(int tick=1;tick<=380;tick++) helper.runAtTickTime(tick,()->{
            helper.assertTrue(helper.getLevel().noCollision(scout),"queued flight preserves collision");
            if(Math.abs(scout.getX()-(first.getX()+.5))<2 && Math.abs(scout.getZ()-(first.getZ()+.5))<2)passedFirst[0]=true;
        });
        helper.runAtTickTime(390,()->{
            helper.assertTrue(passedFirst[0] && Math.abs(scout.getX()-(second.getX()+.5))<2
                    && Math.abs(scout.getZ()-(second.getZ()+.5))<2,"shift queue traverses both waypoints in order: first="+passedFirst[0]+" current="+scout.position()+" move="+scout.getMoveGoal().getMoveTarget());
            order(scout,UnitAction.HOLD,BlockPos.ZERO);
        });
        helper.runAtTickTime(410,()->{
            held[0]=scout.position();
            helper.assertTrue(scout.getHoldPosition() && scout.getMoveGoal().getMoveTarget()==null,"HOLD clears movement without falling");
            UnitServerEvents.addActionItem(scout.getOwnerName(),UnitAction.MOVE,-1,new int[]{scout.getId()},first,BlockPos.ZERO,true);
            order(scout,UnitAction.STOP,BlockPos.ZERO);
        });
        helper.runAtTickTime(530,()->{
            helper.assertTrue(scout.position().distanceToSqr(held[0])<.20,"STOP cancels queued command and stabilizes native hover");
            helper.assertTrue(scout.getMoveGoal().getMoveTarget()==null && scout.getNavigation().isDone() && !scout.getHoldPosition(),"STOP fully clears flight/hold");
            helper.assertTrue(UnitServerEvents.getUnitActionSlowQueue().stream().noneMatch(a->java.util.Arrays.stream(a.getUnitIds()).anyMatch(id->id==scout.getId())),"no stale queued command survives");
            System.out.println("DREAMSEEKERS_SCOUT_QUEUE_HOLD_STOP_PASS");
            helper.succeed();
        });
    }

    @GameTest(template="unit_isolation_arena", batch=BATCH, timeoutTicks=160)
    public static void noncombatAttackAndHostileFollowDoNotBecomeHiddenEnemyTracking(GameTestHelper helper) {
        floor(helper);
        PinkChickenScout scout=spawn(helper,owner("Passive"),5,1,5);
        PinkChickenScout enemy=spawn(helper,owner("Enemy"),9,1,5);
        Vec3 initial=scout.position();
        float health=enemy.getHealth();
        UnitServerEvents.addActionItem(scout.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{scout.getId()},BlockPos.ZERO,BlockPos.ZERO);
        helper.runAtTickTime(30,()->{
            helper.assertTrue(scout.getFollowTarget()==null && scout.getTarget()==null,"noncombat attack cannot follow an enemy");
            scout.setFollowTarget(enemy);
            helper.assertTrue(scout.getFollowTarget()==null,"direct hostile follow also rejected");
            helper.assertTrue(!scout.doHurtTarget(enemy),"direct vanilla attack blocked");
        });
        helper.runAtTickTime(100,()->{
            helper.assertTrue(enemy.getHealth()==health && scout.position().distanceToSqr(initial)<.20,"enemy remains unharmed; scout cannot secretly chase");
            order(scout,UnitAction.ATTACK_MOVE,helper.absolutePos(new BlockPos(14,0,5)));
        });
        helper.runAtTickTime(140,()->{
            helper.assertTrue(scout.position().distanceToSqr(initial)>1,"noncombat attack-move is an ordinary movement order");
            System.out.println("DREAMSEEKERS_SCOUT_NONCOMBAT_FOLLOW_PASS");
            helper.succeed();
        });
    }

    private static String owner(String suffix) {
        String owner=PREFIX+suffix;
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));
        return owner;
    }
    private static PinkChickenScout spawn(GameTestHelper helper,String owner,int x,int y,int z) {
        PinkChickenScout worker=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(helper.getLevel());
        helper.assertTrue(worker!=null,"firefly fixture exists");
        worker.setOwnerName(owner);
        BlockPos p=helper.absolutePos(new BlockPos(x,y,z));
        worker.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        FIXTURES.add(worker);
        helper.assertTrue(helper.getLevel().addFreshEntity(worker),"worker enters world with AI enabled");
        return worker;
    }
    private static void order(PinkChickenScout worker,UnitAction action,BlockPos target) {
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
