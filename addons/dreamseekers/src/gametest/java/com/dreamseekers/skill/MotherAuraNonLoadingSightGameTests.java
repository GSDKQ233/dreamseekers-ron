// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

/** Direct no-load ray contract. Unpublished registered bodies are geometry inputs, not aura
 * authority; existing live aura tests and the genuine two-JVM shutdown test cover that authority.
 */
@GameTestHolder("dreamseekers_strengthening")
@PrefixGameTestTemplate(false)
public final class MotherAuraNonLoadingSightGameTests {
    private static final ThreadLocal<Boolean> NO_CREATE=ThreadLocal.withInitial(()->false);
    /** Called only by the gametest-source-set mixin; no production hook or authority. */
    public static void rejectCreate(int x,int z,boolean create) {
        if(create&&NO_CREATE.get())throw new AssertionError("Mother aura LOS attempted create=true chunk request "+x+","+z);
    }
    private static boolean guardedRay(net.minecraft.server.level.ServerLevel level,
                                      net.minecraft.world.entity.LivingEntity source,
                                      net.minecraft.world.entity.LivingEntity target) {
        if(NO_CREATE.get())throw new AssertionError("Nested test-only no-create scope");
        NO_CREATE.set(true);
        try {return BeetleMotherCommandAura.hasLoadedLineOfSight(level,source,target);}
        finally {NO_CREATE.remove();}
    }
    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch="dreamseekers_strengthening_los",timeoutTicks=100)
    public static void loadedMotherRayMatchesNativeClearSolidAndFluidNoneGeometry(GameTestHelper h) {
        var level=h.getLevel();var source=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(level);
        var target=DreamseekersMod.BEETLE.get().create(level);
        h.assertTrue(source!=null&&target!=null,"actual registered geometry inputs");
        BlockPos from=h.absolutePos(new BlockPos(4,2,4)),to=h.absolutePos(new BlockPos(8,2,4));
        for(BlockPos pos:BlockPos.betweenClosed(from.offset(-1,-1,-1),to.offset(1,6,1)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        source.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5);target.moveTo(to.getX()+.5,to.getY(),to.getZ()+.5);
        try {
            h.assertTrue(source.hasLineOfSight(target)&&guardedRay(level,source,target),"loaded clear eye ray agrees with native true");
            BlockPos wall=from.offset(2,0,0);
            for(BlockPos pos:BlockPos.betweenClosed(wall.offset(0,-1,-1),wall.offset(0,6,1)))level.setBlock(pos,Blocks.WATER.defaultBlockState(),2);
            h.assertTrue(source.hasLineOfSight(target)&&guardedRay(level,source,target),"native Fluid.NONE water remains transparent, not a new fluid veto");
            for(BlockPos pos:BlockPos.betweenClosed(wall.offset(0,-1,-1),wall.offset(0,6,1)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!source.hasLineOfSight(target)&&!guardedRay(level,source,target),"loaded solid collider remains blocked exactly as native");
            for(BlockPos pos:BlockPos.betweenClosed(wall.offset(0,-1,-1),wall.offset(0,6,1)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(guardedRay(level,source,target),"removing the real wall immediately restores loaded LOS");
        } finally {source.discard();target.discard();}
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch="dreamseekers_strengthening_los",timeoutTicks=60)
    public static void missingRayChunksDenyWithoutLoadingOrPublishingBodies(GameTestHelper h) {
        var level=h.getLevel();var source=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(level);
        var target=DreamseekersMod.BEETLE.get().create(level);
        h.assertTrue(source!=null&&target!=null,"actual registered but unpublished geometry inputs");
        int x=14_400_000,z=14_400_000; // separate bounded cold columns, never requested by a setup load
        source.moveTo(x+15.5,100,z+8.5);target.moveTo(x+16.5,100,z+8.5);
        var chunks=level.getChunkSource();int first=x>>4,second=first+1,row=z>>4;
        h.assertTrue(chunks.getChunkNow(first,row)==null&&chunks.getChunkNow(second,row)==null,"both original ray chunks actually unavailable before probe");
        int loaded=chunks.getLoadedChunksCount();
        try {
            for(int i=0;i<32;i++) {
                h.assertTrue(!guardedRay(level,source,target),"missing data is fail-closed, never transparent air");
                h.assertTrue(!guardedRay(level,target,source),"reverse ray cannot cause a hidden source-chunk load");
            }
            h.assertTrue(chunks.getChunkNow(first,row)==null&&chunks.getChunkNow(second,row)==null
                    &&chunks.getLoadedChunksCount()==loaded,"repeated guard reads neither load original cold columns nor increase loaded chunk count");
            // Explicit setup loads ONLY the start chunk. The actual tested ray now traverses
            // available geometry before reaching an absent adjacent chunk, not merely a cold start.
            var warm=level.getChunk(first,row);
            int y=level.getMaxBuildHeight()-8;
            source.moveTo(x+15.5,y,z+8.5);target.moveTo(x+16.5,y,z+8.5);
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(x+14,y,z+7),new BlockPos(x+15,y+6,z+9)))
                warm.setBlockState(pos,Blocks.AIR.defaultBlockState(),false);
            h.assertTrue(chunks.getChunkNow(first,row)==warm&&chunks.getChunkNow(second,row)==null,
                    "positive loaded start and still-cold endpoint are real setup prerequisites");
            int partiallyLoaded=chunks.getLoadedChunksCount();
            for(int i=0;i<32;i++)h.assertTrue(!guardedRay(level,source,target),"ray denies at missing data after an available clear start");
            h.assertTrue(chunks.getChunkNow(first,row)==warm&&chunks.getChunkNow(second,row)==null
                    &&chunks.getLoadedChunksCount()==partiallyLoaded,"loaded-to-cold boundary never creates or loads the unavailable endpoint");
            h.assertTrue(source.tickCount==0&&target.tickCount==0&&!source.isAddedToWorld()&&!target.isAddedToWorld()
                    &&level.getEntity(source.getUUID())==null&&level.getEntity(target.getUUID())==null,"probe never publishes replacement bodies or creates aura authority");
        } finally {source.discard();target.discard();}
        h.succeed();
    }
}
