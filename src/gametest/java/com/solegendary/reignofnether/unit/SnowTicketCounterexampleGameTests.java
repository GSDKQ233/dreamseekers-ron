package com.solegendary.reignofnether.unit;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.Map;
/** Compatible with API61: observes actual runtime ownership loss, no new-field assumption. */
@GameTestHolder("api62_chunk_counterexample") @PrefixGameTestTemplate(false)
public final class SnowTicketCounterexampleGameTests {
    private static Object field(Object target,String name)throws Exception {
        Class<?> c=target.getClass();while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);
    }
    @SuppressWarnings("unchecked") private static long snowTickets(ServerLevel level,ChunkPos chunk){
        try {Map<Long,?> all=(Map<Long,?>)field(level.getChunkSource().chunkMap.getDistanceManager(),"tickets");
            Object entries=all.get(chunk.toLong());if(entries==null)return 0;long count=0;
            for(Object raw:(Iterable<?>)entries){Ticket<?> t=(Ticket<?>)raw;
                if(t.getTicketLevel()==32 && ("reignofnether:wraith_snow".equals(t.getType().toString()) || (t.getType()==TicketType.FORCED && chunk.equals(field(t,"key")))))count++;
            }return count;
        }catch(Exception e){throw new RuntimeException(e);}
    }
    @GameTest(templateNamespace="api62_chunk_counterexample",template="ore_mine_test_arena",batch="api62_snow_counterexample",timeoutTicks=100)
    public static void oneSnowRemovalMustNotRetireRemainingSnowLease(GameTestHelper h){
        var level=h.getLevel();var a=h.absolutePos(new BlockPos(3,2,3));var b=a.offset((a.getX()&15)==15?-1:1,0,0);var chunk=new ChunkPos(a);
        for(var pos:new BlockPos[]{a,b}){level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,BlockRegistrar.WRAITH_SNOW_LAYER.get().defaultBlockState());}
        h.runAfterDelay(3,()->{
            try {
                h.assertTrue(snowTickets(level,chunk)>0,"real snow runtime lease exists before removal");
                level.setBlockAndUpdate(a,Blocks.AIR.defaultBlockState());
                h.assertTrue(level.getBlockState(b).is(BlockRegistrar.WRAITH_SNOW_LAYER.get()),"peer snow physically remains");
                h.assertTrue(snowTickets(level,chunk)>0,"remaining snow must retain its chunk lease when peer is removed");
            }finally{level.setBlockAndUpdate(a,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(b,Blocks.AIR.defaultBlockState());}
            h.succeed();
        });
    }
}
