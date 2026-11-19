package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.blocks.WraithSnowBlockEntity;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** Native runtime ticket evidence, not a graphical or full chunk-eviction test. */
@GameTestHolder("api62_chunk_lifecycle") @PrefixGameTestTemplate(false)
public final class ChunkLifecycleGameTests {
    private static Object field(Object target, String name) {
        try { Class<?> type=target.getClass(); while(type!=null) {
            try {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
            catch(NoSuchFieldException e){type=type.getSuperclass();}
        } throw new NoSuchFieldException(name); } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    @SuppressWarnings("unchecked") private static long tickets(ServerLevel level, String type, Object key) {
        Map<Long,?> all=(Map<Long,?>)field(level.getChunkSource().chunkMap.getDistanceManager(),"tickets");
        long count=0;
        for(var set:all.values()) for(Object raw:(Iterable<?>)set) {
            Ticket<?> ticket=(Ticket<?>)raw;
            if(type.equals(ticket.getType().toString()) && (key==null || key.equals(field(ticket,"key"))))count++;
        }
        return count;
    }
    private static WraithSnowBlockEntity snow(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos,BlockRegistrar.WRAITH_SNOW_LAYER.get().defaultBlockState());
        return (WraithSnowBlockEntity)level.getBlockEntity(pos);
    }
    static Set<Long> persistent(ServerLevel level,String modId,UUID id, boolean ticking) {
        var data=level.getDataStorage().get(ForcedChunksSavedData::load,"chunks");
        Set<Long> out=new HashSet<>();if(data==null)return out;
        var mods=data.save(new CompoundTag()).getList("ForgeForced",Tag.TAG_COMPOUND);
        for(int i=0;i<mods.size();i++){var mod=mods.getCompound(i);if(!modId.equals(mod.getString("Mod")))continue;
            var chunks=mod.getList("ModForced",Tag.TAG_COMPOUND);
            for(int j=0;j<chunks.size();j++)for(Tag uuid:chunks.getCompound(j).getList(ticking?"TickingEntities":"Entities",Tag.TAG_INT_ARRAY))
                if(id.equals(NbtUtils.loadUUID(uuid)))out.add(chunks.getCompound(j).getLong("Chunk"));
        }return out;
    }
    @GameTest(templateNamespace="api62_chunk_lifecycle",template="ore_mine_test_arena",batch="api62_chunks",timeoutTicks=100)
    public static void overlappingSnowRemovalPreservesOtherOwners(GameTestHelper h) {
        var level=h.getLevel();var a=h.absolutePos(new BlockPos(3,2,3));
        // Deliberately place both in exactly the same chunk, irrespective of template origin.
        var b=a.offset((a.getX()&15)==15?-1:1,0,0);var chunk=new ChunkPos(a);
        var first=snow(level,a);var second=snow(level,b);
        var unit=EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        unit.setOwnerName("Api62SnowControl");unit.setNoAi(true);unit.moveTo(a.getX()+.5,a.getY()+2,a.getZ()+.5);
        h.assertTrue(level.addFreshEntity(unit),"native unit control published");
        level.getChunkSource().addRegionTicket(TicketType.FORCED,chunk,1,chunk);
        h.runAfterDelay(3,()->{
            try {
                var firstKey=field(first,"ticketOwner");var secondKey=field(second,"ticketOwner");
                h.assertTrue(!firstKey.equals(secondKey),"each snow lifetime has independent owner");
                h.assertTrue(tickets(level,"reignofnether:wraith_snow",firstKey)==1&&tickets(level,"reignofnether:wraith_snow",secondKey)==1,"both native snow leases exist");
                long neighborhood=tickets(level,"reignofnether:unit_neighborhood",null);
                level.setBlockAndUpdate(a,Blocks.AIR.defaultBlockState());
                h.assertTrue(tickets(level,"reignofnether:wraith_snow",firstKey)==0&&tickets(level,"reignofnether:wraith_snow",secondKey)==1,"removing one block preserves peer lease");
                h.assertTrue(tickets(level,"forced",chunk)>=1,"vanilla forced control untouched");
                h.assertTrue(tickets(level,"reignofnether:unit_neighborhood",null)==neighborhood&&!persistent(level,ReignOfNether.MOD_ID,unit.getUUID(),true).isEmpty(),"unit runtime and persistent controls untouched");
                second.onLoad();second.onLoad();
                h.assertTrue(tickets(level,"reignofnether:wraith_snow",secondKey)==1,"repeated load cannot duplicate own lease");
                level.setBlockAndUpdate(b,Blocks.AIR.defaultBlockState());
                second.setRemoved();
                h.assertTrue(tickets(level,"reignofnether:wraith_snow",secondKey)==0,"repeat removal remains idempotent");
            } finally {level.setBlockAndUpdate(a,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(b,Blocks.AIR.defaultBlockState());unit.discard();level.getChunkSource().removeRegionTicket(TicketType.FORCED,chunk,1,chunk);}
            h.succeed();
        });
    }
    @GameTest(templateNamespace="api62_chunk_lifecycle",template="ore_mine_test_arena",batch="api62_chunks",timeoutTicks=100)
    public static void replacedSnowCannotReleaseNewLifetime(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,5));var old=snow(level,pos);
        h.runAfterDelay(2,()->{
            var oldKey=field(old,"ticketOwner");
            h.assertTrue(tickets(level,"reignofnether:wraith_snow",oldKey)==1,"original acquired ticket");
            CompoundTag saved=old.saveWithFullMetadata();
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            var replacement=snow(level,pos);replacement.load(saved);
            h.runAfterDelay(2,()->{
                try {var newKey=field(replacement,"ticketOwner");old.setRemoved();old.onLoad();
                    h.assertTrue(tickets(level,"reignofnether:wraith_snow",oldKey)==0,"stale body cannot reacquire or remove another lifetime");
                    h.assertTrue(!oldKey.equals(newKey)&&tickets(level,"reignofnether:wraith_snow",newKey)==1,"NBT reload has a new runtime lease");
                } finally {level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}
                h.succeed();
            });
        });
    }
    @GameTest(templateNamespace="api62_chunk_lifecycle",template="ore_mine_test_arena",batch="api62_chunks",timeoutTicks=100)
    public static void loadedUnitReconcilesOnlyConfirmedLegacyOwner(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,3,5));var current=new ChunkPos(pos);var stale=new ChunkPos(current.x+3,current.z);
        var unit=EntityRegistrar.ZOMBIE_UNIT.get().create(level);unit.setOwnerName("Api62Legacy");unit.setNoAi(true);unit.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        UUID id=unit.getUUID(),unknown=UUID.randomUUID();
        var nonUnit=net.minecraft.world.entity.EntityType.PIG.create(level);nonUnit.setNoAi(true);nonUnit.moveTo(pos.getX()+2,pos.getY(),pos.getZ());
        h.assertTrue(level.addFreshEntity(nonUnit),"non-Unit control is natively published");
        ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,nonUnit.getUUID(),stale.x,stale.z,true,true);
        ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,pos,stale.x,stale.z,true,true);
        ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,id,stale.x,stale.z,true,true);
        ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,unknown,stale.x,stale.z,true,true);
        ForgeChunkManager.forceChunk(level,"forge",id,stale.x,stale.z,true,true);
        ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,id,stale.x,stale.z,true,false);
        // Invoke Forge's real startup validation/reinstatement API with native persisted ticket data.
        ForgeChunkManager.reinstatePersistentChunks(level,level.getDataStorage().get(ForcedChunksSavedData::load,"chunks"));
        h.assertTrue(persistent(level,ReignOfNether.MOD_ID,id,true).contains(stale.toLong()),"validation does not guess missing UUIDs are dead");
        h.assertTrue(level.addFreshEntity(unit),"exact known UUID becomes natively published");
        h.runAfterDelay(3,()->{
            try {
                h.assertTrue(persistent(level,ReignOfNether.MOD_ID,id,true).equals(Set.of(new ChunkPos(unit.blockPosition()).toLong())),"confirmed live unit keeps only its current center");
                h.assertTrue(persistent(level,ReignOfNether.MOD_ID,unknown,true).contains(stale.toLong()),"unknown or unloaded UUID remains untouched");
                h.assertTrue(persistent(level,ReignOfNether.MOD_ID,nonUnit.getUUID(),true).contains(stale.toLong()),"loaded non-Unit UUID remains untouched");
                h.assertTrue(level.getDataStorage().get(ForcedChunksSavedData::load,"chunks").getBlockForcedChunks().getTickingChunks().values().stream().anyMatch(chunks->chunks.contains(stale.toLong())),"block ticket remains untouched");
                h.assertTrue(persistent(level,"forge",id,true).contains(stale.toLong()),"other mod same UUID remains untouched");
                h.assertTrue(persistent(level,ReignOfNether.MOD_ID,id,false).contains(stale.toLong()),"non-ticking ticket outside legacy unit pattern remains untouched");
            } finally {
                unit.discard();nonUnit.discard();
                ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,nonUnit.getUUID(),stale.x,stale.z,false,true);
                ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,pos,stale.x,stale.z,false,true);
                ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,unknown,stale.x,stale.z,false,true);
                ForgeChunkManager.forceChunk(level,"forge",id,stale.x,stale.z,false,true);ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,id,stale.x,stale.z,false,false);
                ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,id,stale.x,stale.z,false,true);
            }h.succeed();
        });
    }
}
