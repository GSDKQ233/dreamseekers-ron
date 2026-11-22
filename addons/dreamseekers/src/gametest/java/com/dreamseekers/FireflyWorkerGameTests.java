// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.FireflyWorker;
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
import net.pasterdream.entity.FireflyEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real ticking tests: movement orders never manually move the worker after fixture spawn. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireflyWorkerGameTests {
    private static final String BATCH = "dreamseekers_firefly";
    private static final String PREFIX = "FireflyGT_";
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
        FireflyWorker worker = spawn(helper, owner, 5, 1, 10);
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
            helper.runAtTickTime(tick, () -> System.out.println("DREAMSEEKERS_FIREFLY_BARRIER_DIAGNOSTIC tick="
                    + now + " " + FireflyNavigationTestDiagnostics.describeMove(worker, target)));
        }
        helper.runAtTickTime(310, () -> {
            helper.assertTrue(worker instanceof FireflyEntity && worker.getNavigation() instanceof FlyingPathNavigation
                    && worker.getMoveControl() instanceof FlyingMoveControl && worker.isFlyingUnit(), "actual original firefly, flying navigator/controller and RTS flying classification");
            helper.assertTrue(crossed[0] && maxHeight[0] >= floorY + 2.95, "crossed obstacle by gaining real altitude, not ground step: " + worker.position());
            helper.assertTrue(maxHeight[0] <= floorY + 5.5, "low-altitude order does not use stock ground+10 hovering");
            helper.assertTrue(Math.abs(worker.getX() - (target.getX()+.5)) < 2 && Math.abs(worker.getZ()-(target.getZ()+.5)) < 2,
                    "genuine flight arrives across barrier: " + worker.position());
            System.out.println("DREAMSEEKERS_FIREFLY_BARRIER_PASS maxY=" + maxHeight[0] + " final=" + worker.position());
            helper.succeed();
        });
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 420)
    public static void sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper helper) {
        floor(helper);
        String owner = owner("Sealed");
        for (int x=3;x<=11;x++) for(int z=3;z<=11;z++) for(int y=1;y<=6;y++)
            if (x==3 || x==11 || z==3 || z==11 || y==6) set(helper,x,y,z,Blocks.STONE);
        FireflyWorker worker=spawn(helper,owner,8,1,7);
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
            System.out.println("DREAMSEEKERS_FIREFLY_SEALED_WALL_PASS final="+worker.position());
            helper.succeed();
        });
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 330)
    public static void stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper helper) {
        floor(helper);
        FireflyWorker worker=spawn(helper,owner("Stop"),4,1,5);
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
            helper.assertTrue(worker.getGatherResourceGoal().getGatherTarget()==null,"STOP leaves no resource task");
            System.out.println("DREAMSEEKERS_FIREFLY_STOP_PASS");
            helper.succeed();
        });
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 100)
    public static void nativeCaptureAndLootAreIsolatedAndLegacyStoneStillLoads(GameTestHelper helper) {
        floor(helper);
        FireflyWorker worker=spawn(helper,owner("Isolation"),8,1,8);
        var jar=ForgeRegistries.BLOCKS.getValue(DreamseekersMod.paster("ecology_glass_jar"));
        helper.assertTrue(jar!=null && jar!=Blocks.AIR,"original ecology jar exists");
        var player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.nameUUIDFromBytes("FireflyIsolationFakePlayer".getBytes(StandardCharsets.UTF_8)),"FireflyIsoTest"));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(jar,4));
        for(int i=0;i<3;i++) helper.assertTrue(worker.mobInteract(player,InteractionHand.MAIN_HAND)==InteractionResult.PASS,"trained worker rejects capture");
        helper.assertTrue(!worker.isRemoved() && player.getMainHandItem().getCount()==4,"capture does not discard unit or consume jar");
        net.pasterdream.procedures.FireflyPr0Procedure.execute(worker,player,player.getMainHandItem());
        helper.assertTrue(!worker.isRemoved() && player.getMainHandItem().getCount()==4,"raw native capture procedure is also worker-scoped and blocked");
        Entity wild=net.pasterdream.init.PasterdreamModEntities.FIREFLY.get().create(helper.getLevel());
        helper.assertTrue(wild instanceof FireflyEntity,"wild control is original registered firefly");
        wild.moveTo(worker.getX()+3,worker.getY(),worker.getZ(),0,0);FIXTURES.add(wild);
        helper.assertTrue(helper.getLevel().addFreshEntity(wild),"wild capture control enters real world");
        net.pasterdream.procedures.FireflyPr0Procedure.execute(wild,player,player.getMainHandItem());
        helper.assertTrue(wild.isRemoved() && player.getMainHandItem().getCount()==3,"native wild capture remains functional and consumes exactly one jar");

        helper.assertTrue(worker.getAttributeValue(Attributes.MAX_HEALTH)==28 && worker.getAttributeValue(Attributes.MOVEMENT_SPEED)==.50
                && worker.getAttributeValue(Attributes.FLYING_SPEED)==.50
                && worker.getAttributeValue(Attributes.ATTACK_DAMAGE)==2 && worker.getAttributeValue(Attributes.ARMOR)==1,"tuned Firefly baselines; air speed matches nominal ground speed");
        helper.assertTrue(worker.getCost().equals(StoneSpiritWorker.COST) || (worker.getCost().food==StoneSpiritWorker.COST.food
                && worker.getCost().wood==StoneSpiritWorker.COST.wood && worker.getCost().ore==StoneSpiritWorker.COST.ore
                && worker.getCost().emerald==StoneSpiritWorker.COST.emerald && worker.getCost().ticks==StoneSpiritWorker.COST.ticks
                && worker.getCost().population==StoneSpiritWorker.COST.population),"worker economy and training time preserved");
        worker.setScenarioRoleIndex(27);
        worker.setAnchor(helper.absolutePos(new BlockPos(8,1,8)));
        worker.getItems().add(new ItemStack(Items.WHEAT,1));
        worker.getItems().add(new ItemStack(Items.AMETHYST_SHARD,3));
        CompoundTag fireflySaved=new CompoundTag();
        helper.assertTrue(worker.save(fireflySaved),"real firefly entity NBT saved");
        Entity fireflyRestored=EntityType.loadEntityRecursive(fireflySaved,helper.getLevel(),e->e);
        helper.assertTrue(fireflyRestored instanceof FireflyWorker fresh && fresh.isFlyingUnit() && fresh.isNoGravity()
                && !fresh.noPhysics && fresh.getOwnerName().equals(worker.getOwnerName())
                && fresh.getScenarioRoleIndex()==27 && fresh.getAnchor().equals(worker.getAnchor())
                && fresh.getItems().size()==1 && fresh.getItems().get(0).is(Items.AMETHYST_SHARD)
                && fresh.getItems().get(0).getCount()==3,"firefly NBT restores flight, owner and non-valued cargo without valued-cargo duplication");
        helper.assertTrue(Resources.getTotalResourcesFromItems(worker.getItems()).food==4,
                "entity serialization does not consume live cargo before the normal RoN ledger save");
        helper.assertTrue(worker.getInflatedSelectionBox().getXsize()>worker.getBoundingBox().getXsize()
                && worker.getBbWidth()==.5F && worker.getBbHeight()==.5F,"selection aid never changes native physical dimensions");
        worker.getItems().clear();
        StoneSpiritWorker legacy=DreamseekersMod.STONE_SPIRIT_WORKER.get().create(helper.getLevel());
        helper.assertTrue(legacy!=null,"legacy registry preserved");
        legacy.setOwnerName(worker.getOwnerName());
        legacy.getItems().add(new ItemStack(Items.AMETHYST_SHARD,2));
        CompoundTag saved=new CompoundTag();
        helper.assertTrue(legacy.save(saved),"legacy worker NBT can still be saved");
        Entity restored=EntityType.loadEntityRecursive(saved,helper.getLevel(),e->e);
        helper.assertTrue(restored instanceof StoneSpiritWorker old && old.getOwnerName().equals(worker.getOwnerName())
                && old.getItems().size()==1 && Factions.getFaction(old)==DreamseekersMod.DREAMSEEKERS,"old worker reload remains old worker, preserves owner/cargo/faction, no silent conversion");
        var deathArea=worker.getBoundingBox().inflate(2);
        helper.assertTrue(worker.hurt(helper.getLevel().damageSources().genericKill(),Float.MAX_VALUE),"real worker death");
        helper.runAtTickTime(30,()->{
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,deathArea).isEmpty(),"worker native death yields no farmable item drops");
            player.getInventory().clearContent();
            System.out.println("DREAMSEEKERS_FIREFLY_NATIVE_ISOLATION_AND_LEGACY_PASS");
            helper.succeed();
        });
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 490)
    public static void lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper helper) {
        floor(helper);
        for (int x = 2; x <= 21; x++) for (int z = 8; z <= 12; z++) for (int y = 1; y <= 8; y++)
            if (x == 2 || x == 21 || z == 8 || z == 12) set(helper, x, y, z, Blocks.STONE);
        for (int z = 9; z <= 11; z++) for (int y = 1; y <= 6; y++) set(helper, 12, y, z, Blocks.STONE);
        FireflyWorker worker = spawn(helper, owner("LowCeiling"), 5, 1, 10);
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
            helper.runAtTickTime(tick, () -> System.out.println("DREAMSEEKERS_FIREFLY_LOW_CEILING_DIAGNOSTIC tick="
                    + now + " " + FireflyNavigationTestDiagnostics.describeMove(worker, target)));
        }
        helper.runAtTickTime(450, () -> {
            helper.assertTrue(Math.abs(worker.getX() - (target.getX() + .5)) < 2,
                    "same pending order recovers through a genuinely opened route: " + worker.position());
            System.out.println("DREAMSEEKERS_FIREFLY_LOW_CEILING_RETRY_PASS final=" + worker.position());
            helper.succeed();
        });
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 530)
    public static void queuedFogConstructionUsesCollisionFlightAndStopClearsIt(GameTestHelper helper) {
        floor(helper);
        String owner = owner("FogApproach");
        Resources account = ResourcesServerEvents.resourcesList.stream()
                .filter(r -> r.ownerName.equals(owner)).findFirst().orElseThrow();
        BlockPos origin = helper.absolutePos(new BlockPos(15, 1, 3));
        BuildingPlacement pending = BuildingUtils.getNewBuildingPlacement(DreamseekersMod.REVERIE_GARDEN,
                helper.getLevel(), origin, Rotation.NONE, owner, false);
        helper.assertTrue(pending != null, "real registered garden creates an unplaced preview");
        int z = pending.centrePos.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();
        helper.assertTrue(z >= 3 && z <= 20, "fog fixture's actual building centre fits the arena");
        for (int x = 2; x <= 23; x++) for (int y = 1; y <= 7; y++) {
            set(helper, x, y, z - 2, Blocks.STONE);
            set(helper, x, y, z + 2, Blocks.STONE);
        }
        for (int dz = -1; dz <= 1; dz++) for (int y = 1; y <= 2; y++)
            set(helper, 12, y, z + dz, Blocks.STONE);
        FireflyWorker worker = spawn(helper, owner, 5, 1, z);
        // A headless bot has no connected viewer: retain the genuine server visibility gate,
        // never inject a visibility mask or claim this exercises client fog rendering.
        helper.assertTrue(!FogOfWarServerEvents.isBlockVisibleFor(owner, pending.centrePos.getX(), pending.centrePos.getZ()),
                "pending site remains hidden under the real server visibility policy");
        account.wood = 0;
        helper.assertTrue(BuildingServerEvents.placeBuilding(pending, origin, Rotation.NONE, owner,
                new int[]{worker.getId()}, false, false, false, false) == null
                && worker.getExploreBuildLocationGoal().getFogQueuedBuildings().isEmpty(),
                "ordinary affordability guard rejects a fog order before queuing it");
        account.wood = 500;
        helper.assertTrue(BuildingServerEvents.placeBuilding(pending, origin, Rotation.NONE, owner,
                new int[]{worker.getId()}, false, false, false, false) == null
                && worker.getExploreBuildLocationGoal().getFogQueuedBuildings().contains(pending),
                "normal placement API queues exploration without force-place or ignore-fog flags");
        final double[] highest = {worker.getY()};
        final boolean[] crossed = {false};
        final Vec3[] stopped = {null};
        final double floorY = helper.absolutePos(BlockPos.ZERO).getY();
        for (int tick = 1; tick <= 370; tick++) helper.runAtTickTime(tick, () -> {
            helper.assertTrue(helper.getLevel().noCollision(worker) && !worker.noPhysics,
                    "fog-approach flight uses actual body collision");
            highest[0] = Math.max(highest[0], worker.getY());
            if (worker.getX() > helper.absolutePos(new BlockPos(13, 0, 0)).getX()) crossed[0] = true;
            helper.assertTrue(!BuildingServerEvents.getBuildings().contains(pending),
                    "hidden preview does not become a free or remotely placed building");
        });
        helper.runAtTickTime(380, () -> {
            helper.assertTrue(crossed[0] && highest[0] >= floorY + 2.95,
                    "queued fog-building approach genuinely flies over the barrier: " + worker.position());
            helper.assertTrue(worker.getExploreBuildLocationGoal().getFogQueuedBuildings().contains(pending),
                    "unrevealed site stays queued until the player cancels");
            order(worker, UnitAction.STOP, BlockPos.ZERO);
        });
        helper.runAtTickTime(410, () -> stopped[0] = worker.position());
        helper.runAtTickTime(500, () -> {
            helper.assertTrue(worker.getExploreBuildLocationGoal().getFogQueuedBuildings().isEmpty()
                    && worker.getExploreBuildLocationGoal().getFogQueuedBlocksToDraw().isEmpty()
                    && worker.getExploreBuildLocationGoal().getMoveTarget() == null
                    && worker.getBuildRepairGoal().getBuildingTarget() == null
                    && worker.getNavigation().isDone(), "STOP clears fog queue, approach and construction task");
            helper.assertTrue(worker.position().distanceToSqr(stopped[0]) < .20,
                    "cancelled fog order never restarts flight");
            helper.assertTrue(!BuildingServerEvents.getBuildings().contains(pending)
                    && account.food == 500 && account.wood == 500 && account.ore == 500,
                    "hidden cancelled order grants no building and charges no resources");
            System.out.println("DREAMSEEKERS_FIREFLY_FOG_APPROACH_STOP_PASS highest=" + highest[0]);
            helper.succeed();
        });
    }

    private static String owner(String suffix) {
        String owner=PREFIX+suffix;
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));
        return owner;
    }
    private static FireflyWorker spawn(GameTestHelper helper,String owner,int x,int y,int z) {
        FireflyWorker worker=DreamseekersMod.FIREFLY_WORKER.get().create(helper.getLevel());
        helper.assertTrue(worker!=null,"firefly fixture exists");
        worker.setOwnerName(owner);
        BlockPos p=helper.absolutePos(new BlockPos(x,y,z));
        worker.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        FIXTURES.add(worker);
        helper.assertTrue(helper.getLevel().addFreshEntity(worker),"worker enters world with AI enabled");
        return worker;
    }
    private static void order(FireflyWorker worker,UnitAction action,BlockPos target) {
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
