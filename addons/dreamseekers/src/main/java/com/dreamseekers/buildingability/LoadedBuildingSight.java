// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Collision-only observation. Missing chunks/BE shapes deny; reads never create a chunk ticket. */
public final class LoadedBuildingSight {
    private LoadedBuildingSight() { }
    public static boolean clear(ServerLevel level,Vec3 from,Vec3 to,Entity shapeContext) {
        if(level==null||!level.getServer().isSameThread()||from==null||to==null
                ||(shapeContext!=null&&shapeContext.level()!=level)||!Double.isFinite(from.lengthSqr())||!Double.isFinite(to.lengthSqr())
                ||from.distanceToSqr(to)>128d*128d)return false;
        LoadedBlocks blocks=new LoadedBlocks(level);
        try {
            var hit=blocks.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shapeContext));
            return blocks.complete&&hit.getType()==HitResult.Type.MISS;
        } catch(RuntimeException unavailableShape) { return false; }
    }
    private static final class LoadedBlocks implements BlockGetter {
        private final ServerLevel level;
        private boolean complete=true;
        LoadedBlocks(ServerLevel level){this.level=level;}
        private LevelChunk chunk(BlockPos pos) {
            LevelChunk result=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(result==null)complete=false;
            return result;
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            if(level.isOutsideBuildHeight(pos))return Blocks.VOID_AIR.defaultBlockState();
            LevelChunk chunk=chunk(pos);return chunk==null?Blocks.BARRIER.defaultBlockState():chunk.getBlockState(pos);
        }
        @Override public FluidState getFluidState(BlockPos pos) {
            if(level.isOutsideBuildHeight(pos))return Fluids.EMPTY.defaultFluidState();
            LevelChunk chunk=chunk(pos);return chunk==null?Fluids.EMPTY.defaultFluidState():chunk.getFluidState(pos);
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            if(level.isOutsideBuildHeight(pos))return null;
            LevelChunk chunk=chunk(pos);if(chunk==null)return null;
            BlockEntity existing=chunk.getBlockEntities().get(pos);
            if(existing!=null&&!existing.isRemoved())return existing;
            if(existing!=null||chunk.getBlockState(pos).hasBlockEntity())complete=false;
            return null;
        }
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
    }
}
