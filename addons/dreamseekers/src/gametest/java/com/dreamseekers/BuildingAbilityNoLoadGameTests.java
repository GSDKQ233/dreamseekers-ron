// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.LoadedBuildingSight;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Real production no-load ray, armed tripwire only during the tested read. Fixture loading is
 * explicit and outside the guard; bodies are geometry inputs, never manufactured authority. */
@GameTestHolder("dreamseekers_building_boundary") @PrefixGameTestTemplate(false)
public final class BuildingAbilityNoLoadGameTests {
    private static final ThreadLocal<Boolean> NO_CREATE=ThreadLocal.withInitial(()->false);
    public static void rejectCreate(int x,int z,boolean create) {
        if(create&&NO_CREATE.get())throw new AssertionError("Building ray attempted create=true request "+x+","+z);
    }
    private static boolean ray(net.minecraft.server.level.ServerLevel level,Vec3 from,Vec3 to,LivingEntity context) {
        if(NO_CREATE.get())throw new AssertionError("Nested no-create scope");NO_CREATE.set(true);
        try{return LoadedBuildingSight.clear(level,from,to,context);}finally{NO_CREATE.remove();}
    }
    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch="building_loaded_sight",timeoutTicks=80)
    public static void actualBuildingRayMatchesLoadedColliderFluidAndInvalidInputPolicy(GameTestHelper h) {
        var level=h.getLevel();var body=DreamseekersMod.BEETLE.get().create(level);
        BlockPos from=h.absolutePos(new BlockPos(4,2,4)),to=h.absolutePos(new BlockPos(8,2,4));
        for(BlockPos p:BlockPos.betweenClosed(from.offset(-1,-1,-1),to.offset(1,6,1)))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
        body.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5);Vec3 a=Vec3.atCenterOf(from),b=Vec3.atCenterOf(to);
        try {
            h.assertTrue(ray(level,a,b,body),"loaded clear production building ray is transparent");
            BlockPos wall=from.offset(2,0,0);
            for(BlockPos p:BlockPos.betweenClosed(wall.offset(0,-1,-1),wall.offset(0,6,1)))level.setBlock(p,Blocks.WATER.defaultBlockState(),2);
            h.assertTrue(ray(level,a,b,body),"Fluid.NONE never invents water obstruction");
            for(BlockPos p:BlockPos.betweenClosed(wall.offset(0,-1,-1),wall.offset(0,6,1)))level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!ray(level,a,b,body),"real solid collider blocks observation");
            for(BlockPos p:BlockPos.betweenClosed(wall.offset(0,-1,-1),wall.offset(0,6,1)))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(ray(level,a,b,body),"same-task removal restores clear loaded ray without cache lag");
            h.assertTrue(!ray(level,new Vec3(Double.NaN,a.y,a.z),b,body)&&!ray(level,a,new Vec3(Double.POSITIVE_INFINITY,b.y,b.z),body)
                    &&!ray(level,a,a.add(129,0,0),body),"nonfinite and out-of-bound ray inputs fail closed");
            var nether=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);var foreign=DreamseekersMod.BEETLE.get().create(nether);
            try{h.assertTrue(!ray(level,a,b,foreign),"foreign dimension shape context is rejected");}finally{foreign.discard();}
        }finally{body.discard();}
        h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch="building_loaded_sight",timeoutTicks=80)
    public static void actualBuildingRayNeverCreatesColdStartOrPartiallyLoadedDestination(GameTestHelper h) {
        var level=h.getLevel();var body=DreamseekersMod.BEETLE.get().create(level);var chunks=level.getChunkSource();
        int x=13_440_000,z=13_440_000,cx=x>>4,cz=z>>4,y=level.getMaxBuildHeight()-8;
        Vec3 a=new Vec3(x+15.5,y+.5,z+8.5),b=new Vec3(x+16.5,y+.5,z+8.5);body.moveTo(a.x,a.y,a.z);
        h.assertTrue(chunks.getChunkNow(cx,cz)==null&&chunks.getChunkNow(cx+1,cz)==null,"true cold columns before guarded reads");
        int coldCount=chunks.getLoadedChunksCount();var forced=new java.util.HashSet<>(level.getForcedChunks());
        try {
            for(int i=0;i<64;i++)h.assertTrue(!ray(level,a,b,body)&&!ray(level,b,a,body),"both-direction missing data denies without load");
            h.assertTrue(chunks.getChunkNow(cx,cz)==null&&chunks.getChunkNow(cx+1,cz)==null&&chunks.getLoadedChunksCount()==coldCount
                    &&level.getForcedChunks().equals(forced),"128 guarded cold rays preserve load count and forced tickets");
            // The test's one setup load is outside the guarded production helper.
            var warm=level.getChunk(cx,cz);
            for(BlockPos p:BlockPos.betweenClosed(new BlockPos(x+14,y,z+7),new BlockPos(x+15,y+5,z+9)))warm.setBlockState(p,Blocks.AIR.defaultBlockState(),false);
            h.assertTrue(chunks.getChunkNow(cx,cz)==warm&&chunks.getChunkNow(cx+1,cz)==null,"real warm start and absent destination boundary");
            int warmCount=chunks.getLoadedChunksCount();
            for(int i=0;i<64;i++)h.assertTrue(!ray(level,a,b,body),"partially available traversal fails at missing destination");
            h.assertTrue(chunks.getChunkNow(cx,cz)==warm&&chunks.getChunkNow(cx+1,cz)==null&&chunks.getLoadedChunksCount()==warmCount
                    &&level.getForcedChunks().equals(forced),"64 boundary rays preserve original availability without any create=true call");
            h.assertTrue(!body.isAddedToWorld()&&body.tickCount==0&&level.getEntity(body.getUUID())==null,"geometry input never became combat or mark authority");
        }finally{body.discard();}
        h.succeed();
    }
}
