// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.gametest.SporeRandomProbe;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.SporeEntityEntity;
import java.io.File;
import java.util.*;

/** Normal shutdown storage and genuinely separate-JVM invalidation, not an implemented persistent summon. */
@GameTestHolder("dreamseekers_spore_restart") @PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SporeRestartGameTests {
    private static final File MANIFEST=new File("spore-restart-expectations.nbt");
    private static final BlockPos ORIGIN=new BlockPos(16384,90,16384);
    private static JungleSporeCarrier loadedCarrier;
    private static SporeEntityEntity loadedWild;
    private static int carrierJoins,wildJoins;
    private static CompoundTag expectation;
    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event){
        if(!"verify".equals(System.getProperty("dreamseekers.spore.test.phase"))||event.getLevel().isClientSide()||!MANIFEST.isFile())return;
        try{
            if(expectation==null)expectation=NbtIo.readCompressed(MANIFEST);
            Entity e=event.getEntity();
            if(e.getUUID().equals(expectation.getUUID("carrier"))){
                if(!(e instanceof JungleSporeCarrier c))throw new IllegalStateException("carrier saved type changed");
                loadedCarrier=c;carrierJoins++;
                System.out.println("DREAMSEEKERS_SPORE_RESTART_JOIN_CARRIER uuid="+e.getUUID()+" tick="+e.tickCount+" health="+c.getHealth());
            }
            if(e.getUUID().equals(expectation.getUUID("wild"))){
                if(e.getClass()!=SporeEntityEntity.class)throw new IllegalStateException("wild auto-converted");
                loadedWild=(SporeEntityEntity)e;wildJoins++;SporeRandomProbe.force(e,1);
                System.out.println("DREAMSEEKERS_SPORE_RESTART_JOIN_WILD uuid="+e.getUUID());
            }
        }catch(Exception e){throw new RuntimeException(e);}
    }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_restart",timeoutTicks=220)
    public static void genuineSavedDisabledCarrierExpiresWithoutRewardAndWildRemainsNative(GameTestHelper h) throws Exception {
        String phase=System.getProperty("dreamseekers.spore.test.phase","");
        if(phase.equals("prepare"))prepare(h);else if(phase.equals("verify"))verify(h);else throw new IllegalArgumentException(phase);
    }
    private static void prepare(GameTestHelper h){
        h.assertTrue(!MANIFEST.exists(),"fresh isolated restart manifest");ServerLevel l=h.getLevel();List<Long> forced=new ArrayList<>();
        for(int x=1024;x<=1025;x++)for(int z=1024;z<=1025;z++){
            long key=ChunkPos.asLong(x,z);h.assertTrue(!l.getForcedChunks().contains(key),"do not adopt another test ticket");l.setChunkForced(x,z,true);forced.add(key);
        }
        for(int x=0;x<20;x++)for(int z=0;z<20;z++)for(int y=0;y<=8;y++)if(y==0||y==8||x==0||z==0||x==19||z==19)l.setBlock(ORIGIN.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        l.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN,ORIGIN.offset(20,l.getMaxBuildHeight()-ORIGIN.getY(),20))).forEach(Entity::discard);
        for(int x=1;x<19;x++)for(int z=1;z<19;z++)for(int y=1;y<8;y++)l.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        var marker=EntityType.COW.create(l);marker.setNoAi(true);marker.moveTo(ORIGIN.getX()+6.5,91,ORIGIN.getZ()+6.5);h.assertTrue(l.addFreshEntity(marker),"real readiness marker");
        h.startSequence().thenWaitUntil(()->h.assertTrue(marker.tickCount>2&&l.getEntity(marker.getId())==marker,"persist region genuinely ticking")).thenExecute(()->{
            marker.discard();
            // Insert once only after the last ordinary server tick, before normal entity storage save.
            java.util.function.Consumer<ServerStoppingEvent> listener=event->{
                JungleSporeCarrier carrier=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(l);
                SporeEntityEntity wild=(SporeEntityEntity)Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("spore_entity"))).create(l);
                wild.setNoAi(true);wild.setPersistenceRequired();SporeRandomProbe.force(wild,1);
                carrier.moveTo(ORIGIN.getX()+6.5,91.4,ORIGIN.getZ()+6.5);wild.moveTo(ORIGIN.getX()+10.5,91.4,ORIGIN.getZ()+6.5);
                if(!l.addFreshEntity(carrier)||!l.addFreshEntity(wild)||!carrier.isAddedToWorld()||!wild.isAddedToWorld()||carrier.tickCount!=0||carrier.wasAuthorityTerminated())throw new IllegalStateException("normal shutdown insertion failed");
                CompoundTag e=new CompoundTag();e.putUUID("carrier",carrier.getUUID());e.putUUID("wild",wild.getUUID());e.putLongArray("forcedChunks",forced);
                e.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());e.putBoolean("insertedAtStopping",true);e.putInt("carrierTickAtStopping",carrier.tickCount);
                try{NbtIo.writeCompressed(e,MANIFEST);}catch(Exception ex){throw new RuntimeException(ex);}
                System.out.println("DREAMSEEKERS_SPORE_RESTART_PREPARED_NORMAL_STOP carrier="+carrier.getUUID()+" wild="+wild.getUUID()+" carrierTick="+carrier.tickCount+" added="+carrier.isAddedToWorld());
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,ServerStoppingEvent.class,listener);
            System.out.println("DREAMSEEKERS_SPORE_RESTART_PREPARE_ARMED_NORMAL_STOP");h.succeed();
        });
    }
    private static void verify(GameTestHelper h)throws Exception{
        h.assertTrue(MANIFEST.isFile(),"normal first JVM produced manifest");CompoundTag e=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(e.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"actual distinct JVM");
        h.assertTrue(e.getBoolean("insertedAtStopping")&&e.getInt("carrierTickAtStopping")==0,"first JVM saved actual entity before first activation-denying tick");
        h.startSequence().thenWaitUntil(()->h.assertTrue(loadedCarrier!=null&&loadedWild!=null&&loadedWild.tickCount>3,"both original UUIDs loaded from normal storage and wild really ticks")).thenExecute(()->{
            h.assertTrue(carrierJoins==1&&wildJoins==1,"no duplicate reload/respawn");
            h.assertTrue(loadedCarrier.getUUID().equals(e.getUUID("carrier"))&&loadedCarrier.wasAuthorityTerminated()&&loadedCarrier.isRemoved()&&h.getLevel().getEntity(e.getUUID("carrier"))==null,"saved unauthorized carrier rejected by its real server tick, never reactivated");
            h.assertTrue(loadedWild.getClass()==SporeEntityEntity.class&&loadedWild.isAlive()&&h.getLevel().getEntity(e.getUUID("wild"))==loadedWild,"original wild UUID remains native, alive and unconverted");
            AABB area=new AABB(ORIGIN,ORIGIN.offset(20,8,20));
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,area).isEmpty()&&h.getLevel().getEntitiesOfClass(ExperienceOrb.class,area).isEmpty()&&!UnitServerEvents.getAllUnits().contains(loadedCarrier),"restart provides no free drop/XP/RTS population");
            loadedWild.discard();SporeRandomProbe.clear();for(long key:e.getLongArray("forcedChunks")){ChunkPos c=new ChunkPos(key);h.getLevel().setChunkForced(c.x,c.z,false);}
            System.out.println("DREAMSEEKERS_SPORE_RESTART_VERIFIED_NORMAL_STORAGE carrier="+e.getUUID("carrier")+" wild="+e.getUUID("wild")+" joins="+carrierJoins+" terminated="+loadedCarrier.wasAuthorityTerminated());h.succeed();
        });
    }
}
