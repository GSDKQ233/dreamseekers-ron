package com.solegendary.reignofnether.unit;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;
/** Two JVMs and normal disk loading; manifest contains expectations only. */
@GameTestHolder("api62_chunk_restart") @PrefixGameTestTemplate(false)
public final class ChunkRestartGameTests {
    private static final File MANIFEST=new File("api62-chunk-restart-expectations.nbt");
    @GameTest(templateNamespace="api62_chunk_restart",template="ore_mine_test_arena",batch="api62_chunk_restart",timeoutTicks=600)
    public static void normalRestartReconcilesOnlyLoadedKnownUnit(GameTestHelper h)throws Exception {
        var level=h.getLevel();
        switch(System.getProperty("api62.chunk.restart.phase","")) {
            case "prepare" -> {
                h.assertTrue(!MANIFEST.exists(),"fresh isolated prepare world");
                var pos=new BlockPos(3072,90,3072);var current=new ChunkPos(pos);var stale=new ChunkPos(current.x+3,current.z);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                var unit=EntityRegistrar.ZOMBIE_UNIT.get().create(level);unit.setOwnerName("Api62Restart");unit.setNoAi(true);unit.setPersistenceRequired();unit.setInvulnerable(true);unit.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
                h.assertTrue(level.addFreshEntity(unit),"real permanent unit published before normal save");
                UUID unknown=UUID.randomUUID();
                h.runAfterDelay(5,()->{
                    ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,unit.getUUID(),stale.x,stale.z,true,true);
                    ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,unknown,stale.x,stale.z,true,true);
                    h.assertTrue(ChunkLifecycleGameTests.persistent(level,ReignOfNether.MOD_ID,unit.getUUID(),true).size()==2,"legacy known unit has actual current and stale persisted tickets");
                    var expected=new CompoundTag();expected.putUUID("unit",unit.getUUID());expected.putUUID("unknown",unknown);expected.putLong("current",current.toLong());expected.putLong("stale",stale.toLong());expected.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
                    try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
                    System.out.println("API62_CHUNK_RESTART_PREPARED known="+unit.getUUID()+" unknown="+unknown);h.succeed();
                });
            }
            case "verify" -> {
                var expected=NbtIo.readCompressed(MANIFEST);UUID id=expected.getUUID("unit"),unknown=expected.getUUID("unknown");long stale=expected.getLong("stale");
                h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM, no injected saved state");
                int[] polls={0};
                h.startSequence().thenWaitUntil(()->{
                    var unit=level.getEntity(id);
                    if (++polls[0] % 50 == 1) {
                        boolean captured=false;
                        try {var f=UnitServerEvents.class.getDeclaredField("restoredUnitTickets");f.setAccessible(true);
                            var all=(java.util.Map<?,?>)f.get(null);var owners=(java.util.Map<?,?>)all.get(level);captured=owners!=null&&owners.containsKey(id);
                        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
                        var current=new ChunkPos(expected.getLong("current"));
                        System.out.println("API62_CHUNK_RESTART_AWAIT polls="+polls[0]+" startupSnapshot="+captured+" chunkLoaded="+level.getChunkSource().hasChunk(current.x,current.z)+" body="+(unit!=null)+" ticking="+(unit!=null?unit.tickCount:-1));
                    }
                    h.assertTrue(unit!=null&&unit.isAlive()&&unit.tickCount>0,"normal forced-chunk startup must publish and tick the original permanent unit UUID");
                }).thenExecute(()->{
                    var unit=level.getEntity(id);
                    h.assertTrue(ChunkLifecycleGameTests.persistent(level,ReignOfNether.MOD_ID,id,true).equals(Set.of(new ChunkPos(unit.blockPosition()).toLong())),"actual startup callback reconciles known unit stale location");
                    h.assertTrue(ChunkLifecycleGameTests.persistent(level,ReignOfNether.MOD_ID,unknown,true).contains(stale),"unresolved UUID ticket survives real restart");
                    unit.discard();ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,unknown,ChunkPos.getX(stale),ChunkPos.getZ(stale),false,true);
                    System.out.println("API62_CHUNK_PROCESS_RESTART_PASS polls="+polls[0]);
                }).thenSucceed();
            }
            default -> throw new IllegalArgumentException("api62.chunk.restart.phase must be prepare or verify");
        }
    }
}
