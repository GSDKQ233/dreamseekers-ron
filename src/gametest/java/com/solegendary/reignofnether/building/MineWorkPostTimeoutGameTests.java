package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.shared.MineWorkPosts;
import com.solegendary.reignofnether.building.buildings.shared.PassiveOreBuilding;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.goals.GarrisonGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Dedicated real-world blocked-route deadline test. No NoAI, time changes, worker teleport,
 * reflected lease fields, research reset, or replacement command between admission and expiry. */
@GameTestHolder("reignofnether_mine_workpost_timeout")
@PrefixGameTestTemplate(false)
public final class MineWorkPostTimeoutGameTests {
    private static Runner active;
    @AfterBatch(batch="mine_workpost_timeout")
    public static void cleanup(ServerLevel ignored) { if (active != null) { active.cleanup(); active = null; } }
    @GameTest(templateNamespace="reignofnether_mine_workpost_timeout", template="ore_mine_test_arena",
            batch="mine_workpost_timeout", timeoutTicks=1400)
    public static void realBlockedRouteExpiresAtServerDeadline(GameTestHelper helper) {
        helper.assertTrue(active == null, "single isolated timeout fixture");
        active = new Runner(helper); active.prepare(); helper.onEachTick(active::tick);
    }
    private static final class Runner {
        static final String OWNER = "MineDeadline_Owner";
        final GameTestHelper h; final ServerLevel level; final Set<Long> forced = new LinkedHashSet<>();
        BuildingPlacement mine; Resources account; VillagerUnit worker; GarrisonGoal originalGoal;
        BlockPos origin, cageFeet; long orderedAt = -1, baselineCredit; int fixtureTicks, commandEntityTick;
        boolean saw1199, saw1200, sawBackoff, completed; int checks;
        Runner(GameTestHelper h) { this.h = h; level = h.getLevel(); }
        void check(boolean condition, String why) { checks++; h.assertTrue(condition, why); }
        long totalCredit() { return (long) account.ore * 160 + mine.getDataStorage().getData(PassiveOreBuilding.PRODUCTION_STATE).getQuarterTicks(); }
        void prepare() {
            check(PlayerServerEvents.getRTSPlayer(OWNER) == null, "fresh isolated registered owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER, Factions.VILLAGERS));
            account = new Resources(OWNER, 1000, 1000, 0); ResourcesServerEvents.resourcesList.add(account);
            origin = h.absolutePos(new BlockPos(30, 1, 30)); cageFeet = origin.offset(4, 1, -9);
            for (int x = (origin.getX()-20)>>4; x <= (origin.getX()+20)>>4; x++)
                for (int z = (origin.getZ()-20)>>4; z <= (origin.getZ()+20)>>4; z++) {
                    long key = ChunkPos.asLong(x,z);
                    if (!level.getForcedChunks().contains(key)) { level.setChunkForced(x,z,true); forced.add(key); }
                }
            for (int x=-20;x<=20;x++) for (int z=-20;z<=20;z++) {
                level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for (int y=1;y<=17;y++) level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for (BuildingBlock block : BuildingUtils.getAbsoluteBlockData(Buildings.VILLAGER_ORE_MINE.getRelativeBlockData(level),level,origin,Rotation.NONE))
                level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            mine = BuildingUtils.getNewBuildingPlacement(Buildings.VILLAGER_ORE_MINE,level,origin,Rotation.NONE,OWNER,false);
            BuildingServerEvents.getBuildings().add(mine);
            // Fully sealed stone enclosure, separate from and not overlapping any valid mine work post.
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) for (int y=0;y<=5;y++)
                if (Math.abs(x)==2 || Math.abs(z)==2 || y==5)
                    level.setBlock(cageFeet.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
            worker = EntityRegistrar.VILLAGER_UNIT.get().create(level);
            check(worker != null, "registered village worker created");
            worker.setOwnerName(OWNER); worker.setNoAi(false);
            // Initial spawn placement only. No position mutation after world insertion.
            worker.moveTo(cageFeet.getX()+0.5,cageFeet.getY(),cageFeet.getZ()+0.5);
            check(level.addFreshEntity(worker), "live AI worker inserted into sealed approach enclosure");
        }
        void tick() {
            if (completed) return;
            try { tickChecked(); } catch (RuntimeException | Error failure) { cleanup(); throw failure; }
        }
        void tickChecked() {
            fixtureTicks++;
            if (orderedAt < 0) {
                if (fixtureTicks < 20) return;
                boolean ready = mine.isBuilt && worker.tickCount > 0 && worker.onGround()
                        && level.getEntity(worker.getId()) == worker && level.isPositionEntityTicking(worker.blockPosition());
                check(ready || fixtureTicks < 100, "native entity tracking and completed mine must be ready before deadline test");
                if (!ready) return;
                for (int i=0;i<MineWorkPosts.BASE_CAPACITY;i++) check(MineWorkPosts.isPostClear(mine,i), "destination post remains valid; route, not target clearance, is blocked");
                new UnitActionItem(OWNER,UnitAction.GARRISON,-1,new int[]{worker.getId()},origin,BlockPos.ZERO).action(level);
                originalGoal = worker.getGarrisonGoal(); orderedAt = level.getGameTime();
                commandEntityTick = worker.tickCount; baselineCredit = totalCredit();
                check(MineWorkPosts.hasAssignment(worker) && originalGoal.getBuildingTarget() == mine,
                        "real GARRISON synchronously admits an explicit reservation despite blocked approach");
                check(MineWorkPosts.snapshot(mine).size()==1 && MineWorkPosts.countActive(mine)==0,
                        "admitted route reservation is visible and earns no bonus");
                System.out.println("MINE_TIMEOUT_ADMITTED time="+orderedAt+" worker="+worker.getUUID()+" post="+originalGoal.getMinePost()+" rtsPathfinding="+UnitServerEvents.rtsPathfinding);
                return;
            }
            long age = level.getGameTime() - orderedAt;
            check(age >= 1 && age <= MineWorkPosts.MAX_APPROACH_TICKS+6, "native server deadline advances without clock edits");
            check(worker.isAlive() && !worker.isRemoved() && !worker.isNoAi() && worker.tickCount > commandEntityTick,
                    "live AI and native entity ticks continue during blocked navigation");
            check(worker.getGarrisonGoal() == originalGoal, "no goal replacement can masquerade as timeout");
            check(mine.ownerName.equals(OWNER) && mine.getCaptureRevision()==0 && mine.isBuilt
                    && BuildingServerEvents.getBuildings().contains(mine), "no capture/removal/fixture reset can masquerade as timeout");
            check(worker.getX()>cageFeet.getX()-1.7 && worker.getX()<cageFeet.getX()+2.7
                    && worker.getZ()>cageFeet.getZ()-1.7 && worker.getZ()<cageFeet.getZ()+2.7
                    && worker.getY()>=cageFeet.getY()-0.1 && worker.getY()<cageFeet.getY()+5,
                    "worker never teleports out of its physically sealed approach enclosure");
            check(MineWorkPosts.countActive(mine)==0, "blocked traveller never produces an arrived-worker bonus");
            check(totalCredit()-baselineCredit==age*4, "real passive mine earns exactly base quarter-credit across every blocked/expired tick: age="+age+" credit="+(totalCredit()-baselineCredit));
            sawBackoff |= originalGoal.isInBackoff();
            if (age < MineWorkPosts.MAX_APPROACH_TICKS) {
                check(MineWorkPosts.hasAssignment(worker) && MineWorkPosts.snapshot(mine).size()==1,
                        "reservation must remain valid until actual 1200-tick deadline, age="+age);
                check(originalGoal.getMoveTarget()!=null && originalGoal.getBuildingTarget()==mine,
                        "actual navigation retains same explicit order before deadline");
                if (age==MineWorkPosts.MAX_APPROACH_TICKS-1) saw1199=true;
            } else {
                check(!MineWorkPosts.hasAssignment(worker) && MineWorkPosts.snapshot(mine).isEmpty(),
                        "server validity and authoritative occupancy release exactly at deadline, age="+age);
                if (age==MineWorkPosts.MAX_APPROACH_TICKS) saw1200=true;
                if (age>=MineWorkPosts.MAX_APPROACH_TICKS+5) {
                    check(originalGoal.getMinePost()==-1 && originalGoal.getBuildingTarget()==null && originalGoal.getMoveTarget()==null,
                            "normal AI cleanup retires raw lease and cancels pending navigation without external reset");
                    check(saw1199 && saw1200, "observed both adjacent actual server ticks around deadline");
                    check(sawBackoff, "blocked active pathfinder really retried and entered backoff; not a NoAI stagnation fixture");
                    completed=true;
                    System.out.println("MINE_TIMEOUT_PASS checks="+checks+" nativeElapsed="+age+" entityTicks="+(worker.tickCount-commandEntityTick)+" exact1199=true exact1200=true backoff="+sawBackoff+" baseCredit="+(totalCredit()-baselineCredit));
                    cleanup();h.succeed();
                }
            }
        }
        void cleanup() {
            if (worker != null) { Unit.fullResetBehaviours(worker); worker.discard(); }
            if (mine != null) BuildingServerEvents.getBuildings().remove(mine);
            ResourcesServerEvents.removeAccount(OWNER); PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));
            for (long key:forced) { ChunkPos chunk=new ChunkPos(key); level.setChunkForced(chunk.x,chunk.z,false); }
            forced.clear();
        }
    }
}
