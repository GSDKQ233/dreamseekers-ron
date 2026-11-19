package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GarrisonGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.ArrayList;
import com.solegendary.reignofnether.building.workposts.MineWorkPostClient;
import com.solegendary.reignofnether.building.workposts.MineWorkPostStatusPacket;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.ResearchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Explicit transient work orders, NOT military garrison occupancy.
 * Leases live on the worker's existing goal; normal commands/reset release them.
 * Restart deliberately requires reassignment rather than assuming nearby workers work.
 */
public final class MineWorkPosts {
    public static final int BASE_CAPACITY = 2;
    public static final int MAX_CAPACITY = 6;
    public static final ResourceLocation EXPANDED_WORKFORCE = ResourceLocation.fromNamespaceAndPath(
            "reignofnether", "research_expanded_workforce");
    public static final int MAX_APPROACH_TICKS = 1200; // 60 simulated seconds; blocked paths release their reservation.
    private static final List<BlockPos> POSTS = List.of(
            new BlockPos(4, 1, 1), new BlockPos(8, 1, 3),
            new BlockPos(3, 1, 3), new BlockPos(4, 1, 5),
            new BlockPos(6, 1, 5), new BlockPos(6, 1, 1));
    private MineWorkPosts() {}
    public static boolean isMine(BuildingPlacement mine) {
        return mine != null && mine.getBuilding() == Buildings.VILLAGER_ORE_MINE;
    }
    /** Owner-level research controls reservations and existing leases, never neighbouring workers.
     * Client research contains only the local player's state; another owner's research is unknown. */
    public static int capacity(BuildingPlacement mine) {
        if (!isMine(mine) || mine.ownerName == null || mine.ownerName.isBlank()) return 0;
        if (mine.getLevel().isClientSide()) {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getName().getString().equals(mine.ownerName)
                    && ResearchClient.hasResearch(EXPANDED_WORKFORCE) ? MAX_CAPACITY : BASE_CAPACITY;
        }
        return ResearchServerEvents.playerHasResearch(mine.ownerName, EXPANDED_WORKFORCE)
                ? MAX_CAPACITY : BASE_CAPACITY;
    }
    /** Matches structure placement transform, including +1 Y offset. */
    public static BlockPos getPost(BuildingPlacement mine, int index) {
        if (index < 0 || index >= MAX_CAPACITY) throw new IllegalArgumentException("Invalid mine work post");
        return POSTS.get(index).rotate(mine.rotation).offset(mine.originPos).above();
    }
    public static boolean isPostClear(BuildingPlacement mine, int index) {
        if (index < 0 || index >= MAX_CAPACITY) return false;
        BlockPos feet = getPost(mine, index);
        Level level = mine.getLevel();
        return level.hasChunkAt(feet)
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }
    public static boolean isValidWorker(Mob worker, BuildingPlacement mine) {
        if (!(worker instanceof VillagerUnit villager) || !worker.isAlive() || worker.isRemoved()
                || !isMine(mine) || worker.level() != mine.getLevel() || !mine.isBuilt
                || mine.shouldBeDestroyed() || mine.ownerName == null || mine.ownerName.isBlank()
                || !mine.ownerName.equals(villager.getOwnerName()) || mine.getCaptureRevision() < 0
                || worker.isPassenger() || villager.getBuildRepairGoal().getBuildingTarget() != null)
            return false;
        return worker.level().isClientSide()
                ? BuildingClientEvents.getBuildings().contains(mine)
                : BuildingServerEvents.getBuildings().contains(mine);
    }
    private static List<LivingEntity> units(Level level) {
        return level.isClientSide() ? UnitClientEvents.getAllUnits() : UnitServerEvents.getAllUnits();
    }
    /** Reservations require explicit goal identity; travellers reserve but do not produce. */
    public static int findFreePost(Mob worker, BuildingPlacement mine) {
        if (!isValidWorker(worker, mine)) return -1;
        boolean[] occupied = new boolean[MAX_CAPACITY];
        for (LivingEntity entity : units(worker.level())) {
            if (entity == worker || entity.level() != worker.level() || !(entity instanceof VillagerUnit villager)) continue;
            GarrisonGoal goal = villager.getGarrisonGoal();
            if (goal != null && goal.getBuildingTarget() == mine && goal.hasMineAssignment())
                occupied[goal.getMinePost()] = true;
        }
        for (int i = 0; i < capacity(mine); i++) if (!occupied[i] && isPostClear(mine, i)) return i;
        return -1;
    }
    public static boolean atPost(Mob worker, BuildingPlacement mine, int index) {
        BlockPos feet = getPost(mine, index);
        double dx = worker.getX() - (feet.getX() + 0.5);
        double dz = worker.getZ() - (feet.getZ() + 0.5);
        return worker.onGround() && dx * dx + dz * dz <= 0.65 * 0.65
                && Math.abs(worker.getY() - feet.getY()) <= 0.4;
    }
    public static boolean hasAssignment(Unit worker) {
        if (worker instanceof LivingEntity entity && entity.level().isClientSide())
            return MineWorkPostClient.hasAssignment(worker);
        return worker instanceof VillagerUnit && worker.getGarrisonGoal() != null
                && worker.getGarrisonGoal().hasMineAssignment();
    }
    /** Server only; client orders are presentation hints, never income authority. */
    public static int countActive(BuildingPlacement mine) {
        if (!isMine(mine) || mine.getLevel().isClientSide()) return 0;
        boolean[] counted = new boolean[MAX_CAPACITY];
        for (LivingEntity entity : units(mine.getLevel())) {
            if (!(entity instanceof VillagerUnit villager) || entity.level() != mine.getLevel()) continue;
            GarrisonGoal goal = villager.getGarrisonGoal();
            if (goal != null && goal.getBuildingTarget() == mine && goal.isMineWorkerActive()) counted[goal.getMinePost()] = true;
        }
        int count = 0;
        for (boolean present : counted) if (present) count++;
        return count;
    }
    /** Live server snapshot uses the same goal validity/arrival predicates as income, not nearby bodies. */
    public static List<MineWorkPostStatusPacket.Worker> snapshot(BuildingPlacement mine) {
        if (!isMine(mine) || mine.level.isClientSide()) return List.of();
        List<MineWorkPostStatusPacket.Worker> result = new ArrayList<>();
        boolean[] counted = new boolean[MAX_CAPACITY];
        for (LivingEntity entity : units(mine.level)) {
            if (!(entity instanceof VillagerUnit worker) || entity.level() != mine.level) continue;
            GarrisonGoal goal = worker.getGarrisonGoal();
            if (goal == null || goal.getBuildingTarget() != mine || !goal.hasMineAssignment()) continue;
            int post = goal.getMinePost();
            if (!counted[post]) {
                counted[post] = true;
                result.add(new MineWorkPostStatusPacket.Worker(worker.getId(), worker.getUUID(), post, goal.isMineWorkerActive()));
            }
        }
        return List.copyOf(result);
    }
    /** Caller handles packet authority; no teleport, economic side effect or auto-reassignment. */
    public static void releaseAll(BuildingPlacement mine) {
        for (LivingEntity entity : units(mine.getLevel())) {
            if (!(entity instanceof VillagerUnit villager) || entity.level() != mine.getLevel()) continue;
            GarrisonGoal goal = villager.getGarrisonGoal();
            if (goal != null && goal.getBuildingTarget() == mine && goal.getMinePost() >= 0)
                goal.stopGarrisoning();
        }
    }
}
