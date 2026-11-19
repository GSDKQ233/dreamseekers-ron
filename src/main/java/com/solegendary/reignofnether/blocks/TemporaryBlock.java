package com.solegendary.reignofnether.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class TemporaryBlock {

    public static final Random RANDOM = new Random();

    public final ServerLevel ownerLevel;
    public final int lifespan;
    public final BlockPos bp;
    public final BlockState bs;
    public final BlockState oldBs;
    public final BlockState bsAbove;
    public int tickAge;
    public boolean isPlaced = false;

    /** Legacy API binds at submission, never to whichever world happens to tick next. */
    public TemporaryBlock(BlockPos bp, BlockState bs, BlockState oldBs, int lifespan, BlockState bsAbove) {
        this(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null ? null :
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld(), bp, bs, oldBs, lifespan, bsAbove);
    }

    public TemporaryBlock(ServerLevel ownerLevel, BlockPos bp, BlockState bs, BlockState oldBs, int lifespan, BlockState bsAbove) {
        this.ownerLevel = ownerLevel;
        this.bp = bp.immutable();
        this.bs = bs;
        this.oldBs = oldBs;
        this.bsAbove = bsAbove;
        this.lifespan = lifespan + RANDOM.nextInt(-10,10);
        this.tickAge = RANDOM.nextInt(-5,-1);
    }

    // returns true if the block lifespan is done
    public boolean tick(ServerLevel level) {
        if (ownerLevel == null) return true;
        if (level != ownerLevel || level.isClientSide())
            return false;

        tickAge += 1;

        if (!isPlaced && tickAge > 0) {
            if (oldBs.isAir()) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, bp, Block.getId(bs));
                level.levelEvent(bs.getSoundType().getPlaceSound().hashCode(), bp, Block.getId(bs));
            }
            level.setBlockAndUpdate(bp, bs);
            if (bsAbove != null) {
                level.setBlockAndUpdate(bp.above(), bsAbove);
            }
            isPlaced = true;
        }
        else if (isPlaced && tickAge >= lifespan) {
            // An overwritten temporary block is retired too: never resurrect stale oldBs later.
            if (level.getBlockState(bp).getBlock() != bs.getBlock()) return true;
            if (oldBs.isAir()) {
                level.destroyBlock(bp, false);
            } else {
                level.setBlockAndUpdate(bp, oldBs);
            }
            if (bsAbove != null && level.getBlockState(bp.above()).getBlock() == bsAbove.getBlock()) {
                level.destroyBlock(bp.above(), false);
            }
            return true;
        }
        return false;
    }
}
