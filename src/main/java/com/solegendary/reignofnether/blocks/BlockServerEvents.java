package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.ResourceSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class BlockServerEvents {

    private static final Random RANDOM = new Random();

    public static ArrayList<TemporaryBlock> tempBlocks = new ArrayList<>();

    // if you want to place a block to replace a broken block, we can't do it in the same tick
    @Deprecated public static Map<BlockPos, BlockState> blocksToPlace = new HashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, BlockState>> pendingBlocks = new IdentityHashMap<>();

    public static void queueBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (level != null && level.getServer().isSameThread())
            pendingBlocks.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), state);
    }

    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
        tempBlocks.clear(); blocksToPlace.clear(); pendingBlocks.clear();
    }
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        tempBlocks.removeIf(block -> block.ownerLevel == null || block.ownerLevel.getServer() == event.getServer());
        pendingBlocks.keySet().removeIf(level -> level.getServer() == event.getServer());
        blocksToPlace.clear();
    }

    public static void addTempBlock(ServerLevel level, BlockPos bp, BlockState bs, BlockState oldBs, int lifespan) {
        addTempBlock(level, bp, bs, oldBs, lifespan, false, null);
    }

    public static void addTempBlock(ServerLevel level, BlockPos bp, BlockState bs, BlockState oldBs, int lifespan, boolean allowInsideBuildings) {
        addTempBlock(level, bp, bs, oldBs, lifespan, allowInsideBuildings, null);
    }

    public static void addTempBlock(ServerLevel level, BlockPos bp, BlockState bs, BlockState oldBs, int lifespan, boolean allowInsideBuildings, BlockState bsAbove) {
        if (!allowInsideBuildings && BuildingUtils.isPosInsideAnyBuilding(level, bp))
            return;

        if (level.getBlockState(bp) != bs)
            tempBlocks.add(new TemporaryBlock(level, bp, bs, oldBs, lifespan, bsAbove));
        else {
            for (TemporaryBlock block : tempBlocks)
                if (block.ownerLevel == level && bp.equals(block.bp))
                    block.tickAge = 0;
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide()) {
            return;
        }
        if (!(evt.level instanceof ServerLevel level)) return;
        tempBlocks.removeIf(tb -> tb.ownerLevel == null || tb.ownerLevel.getServer() != level.getServer()
                || tb.ownerLevel == level && tb.tick(level));
        Map<BlockPos, BlockState> pending = pendingBlocks.remove(level);
        if (pending != null) pending.forEach(level::setBlockAndUpdate);
        // Compatibility writes to the old side-less field remain Overworld-only, and are cleared on stop.
        if (level.dimension() == Level.OVERWORLD) {
            blocksToPlace.forEach(level::setBlockAndUpdate);
            blocksToPlace.clear();
        }
    }

    @SubscribeEvent
    public static void onPlayerBlockBreak(BlockEvent.BreakEvent evt) {
        if (evt.getLevel().isClientSide() || evt.getLevel().getServer() == null)
            return;

        boolean isResource = ResourceSources.getFromBlockState(evt.getState()) != null;
        boolean isBuilding = (evt.getLevel() instanceof Level level && BuildingUtils.isPosInsideAnyBuilding(level, evt.getPos()));

        if (!evt.getLevel().getServer().getGameRules().getRule(GameRuleRegistrar.DO_PLAYER_GRIEFING).get() &&
            !isResource && !isBuilding) {
            evt.setCanceled(true);
        }
    }

    private static HashMap<BlockPos, Integer> getPosesAndWeights(ServerLevel level, BlockPos pos, int centrePosLayers) {
        int baseWeight = 4;
        List<BlockPos> adjPoses = List.of(
                pos.north(), pos.south(), pos.east(), pos.west(),
                pos.north().north(), pos.south().south(), pos.east().east(), pos.west().west(),
                pos.north().east(), pos.south().east(), pos.north().west(), pos.south().west()
        );
        HashMap<BlockPos, Integer> posesAndLayers = new HashMap<>();
        for (BlockPos adjPos : adjPoses) {

            List<BlockPos> verticalPosesShort = List.of(adjPos, adjPos.below(), adjPos.above());
            List<BlockPos> verticalPosesLong = List.of(adjPos, adjPos.below(), adjPos.above(), adjPos.below().below(), adjPos.above().above());
            int horizDistance = adjPos.atY(0).distManhattan(pos.atY(0));
            for (BlockPos verticalPos : horizDistance <= 1 ? verticalPosesShort : verticalPosesLong) {
                if (BlockUtils.canPlaceSnow(level, verticalPos)) {
                    int layers = BlockUtils.getWraithSnowLayers(level.getBlockState(verticalPos));
                    int layerDiffWeight = (centrePosLayers - layers) * 3;
                    int distanceWeight = - horizDistance * 6;
                    int weight = baseWeight + layerDiffWeight + distanceWeight;
                    if (layers == 0 && layerDiffWeight > 0) {
                        weight += baseWeight * 2;
                    }
                    posesAndLayers.put(verticalPos, Math.max(0, weight));
                    break;
                }
            }
        }
        // extra chance for centre pos:
        if (centrePosLayers < 8 && BlockUtils.canPlaceSnow(level, pos))
            posesAndLayers.put(pos, baseWeight * 2);
        return posesAndLayers;
    }

    public static HashMap<BlockPos, Integer> getSnowPositions(
            Level level, BlockPos center, int maxDistance
    ) {
        HashMap<BlockPos, Integer> positions = new HashMap<>();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        for (int dx = -maxDistance; dx <= maxDistance; dx++) {
            for (int dz = -maxDistance; dz <= maxDistance; dz++) {
                int horizontalDistance = Math.abs(dx) + Math.abs(dz);
                if (horizontalDistance > maxDistance)
                    continue;

                BlockPos basePos = new BlockPos(cx + dx, cy, cz + dz);
                if (BlockUtils.canPlaceSnow(level, basePos)) {
                    positions.put(basePos, horizontalDistance);
                    continue;
                }
                // Vertical search range grows with horizontal distance
                for (int dy = 1; dy <= horizontalDistance; dy++) {
                    BlockPos below = basePos.below(dy);
                    if (BlockUtils.canPlaceSnow(level, below)) {
                        positions.put(below, horizontalDistance);
                        break;
                    }
                    BlockPos above = basePos.above(dy);
                    if (BlockUtils.canPlaceSnow(level, above)) {
                        positions.put(above, horizontalDistance);
                        break;
                    }
                }
            }
        }
        return positions;
    }

    private static BlockPos pickWeighted(Map<BlockPos, Integer> weights) {
        int totalWeight = 0;

        for (int w : weights.values()) {
            if (w > 0) {
                totalWeight += w;
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = RANDOM.nextInt(totalWeight);

        for (Map.Entry<BlockPos, Integer> entry : weights.entrySet()) {
            int w = entry.getValue();
            if (w <= 0) continue;

            roll -= w;
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean placeWraithSnow(ServerLevel level, BlockPos pos, int ownerId) {
        return placeWraithSnow(level, pos, ownerId, true);
    }

    public static boolean placeWraithSnow(ServerLevel level, BlockPos pos, int ownerId, boolean breakWithSound) {
        if (!BlockUtils.canPlaceSnow(level, pos))
            return false;

        // Resolve the caller's live identity before placement callbacks can recycle its ID.
        var owner = level.getEntity(ownerId);
        BlockState bsExisting = level.getBlockState(pos);

        int layers = BlockUtils.getWraithSnowLayers(bsExisting);

        BlockPos targetPos = pos;

        if (layers > 1) {
            HashMap<BlockPos, Integer> posesAndWeights = getPosesAndWeights(level, pos, layers);
            targetPos = pickWeighted(posesAndWeights);
        }
        BlockState snowBs = BlockRegistrar.WRAITH_SNOW_LAYER.get().defaultBlockState();

        if (targetPos != null && BlockUtils.canPlaceSnow(level, targetPos)) {
            BlockState targetBs = level.getBlockState(targetPos);
            int targetLayers = BlockUtils.getWraithSnowLayers(targetBs);
            if (targetLayers >= 8) return false;
            BlockState placedState = targetLayers <= 0 ? snowBs
                    : targetBs.setValue(BlockStateProperties.LAYERS, targetLayers + 1);
            if (targetLayers <= 0 && level.getBlockState(targetPos.below()).getBlock() == Blocks.DIRT_PATH) {
                level.setBlockAndUpdate(targetPos.below(), Blocks.DIRT.defaultBlockState());
            }
            if (!BlockUtils.canPlaceSnow(level, targetPos) || level.getBlockState(targetPos) != targetBs
                    || !level.setBlockAndUpdate(targetPos, placedState)
                    || level.getBlockState(targetPos) != placedState) return false;
            BlockEntity be = level.getBlockEntity(targetPos);
            if (!(be instanceof WraithSnowBlockEntity snowBe) || be.isRemoved()) return false;
            snowBe.setOwner(owner);
            snowBe.randomiseLifeTicks();
            if (breakWithSound) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, targetPos, Block.getId(snowBs));
                level.levelEvent(targetBs.getSoundType().getPlaceSound().hashCode(), targetPos, Block.getId(snowBs));
            }
            return true;
        }
        return false;
    }
}
