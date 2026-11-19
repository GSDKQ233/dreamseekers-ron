package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Real two-JVM fallen-record persistence followed by the native paid revival production path. */
@GameTestHolder("reignofnether_kill_restart")
@PrefixGameTestTemplate(false)
public final class KillValueRestartGameTests {
    static final String PREFIX="KillValueRestart_",KILLER=PREFIX+"Killer",VICTIM=PREFIX+"Victim";
    static final BlockPos ORIGIN=new BlockPos(8192,90,8192);
    static final File MANIFEST=new File("kill-value-restart-expectations.nbt");
    static final class Observer {
        int committed;final ServerLevel level;Observer(ServerLevel level){this.level=level;}
        @SubscribeEvent public void credit(ConfirmedRtsKillEvent event){
            if(!event.snapshot().killerOwner().equals(KILLER)||!event.snapshot().victimOwner().equals(VICTIM))return;
            if(event.snapshot().food()!=100||ResourceCosts.ROYAL_GUARD.food!=0)throw new AssertionError("configured death snapshot must survive immediate default-price restoration");
            if(event.tryClaim(a->ResourceTransaction.creditIncome(level.getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))committed++;
        }
    }
    static Resources account(String owner){return ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();}
    static Resources create(String owner){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.VILLAGERS));Resources r=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(r);return r;}
    static CompoundTag snapshot(Resources r){CompoundTag n=new CompoundTag();n.putInt("food",r.food);n.putInt("wood",r.wood);n.putInt("ore",r.ore);n.putInt("emerald",r.emerald);r.writeExtended(n);return n;}
    static BuildingPlacement install(ServerLevel level,Building type,BlockPos origin){
        for(int x=-1;x<30;x++)for(int z=-1;z<30;z++){level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<20;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(BuildingBlock b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        BuildingPlacement p=BuildingUtils.getNewBuildingPlacement(type,level,origin,Rotation.NONE,VICTIM,false);BuildingServerEvents.getBuildings().add(p);return p;
    }
    static <T extends Mob> T spawn(ServerLevel level,EntityType<T> type,String owner,BlockPos p){T e=type.create(level);((Unit)e).setOwnerName(owner);e.setNoAi(true);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);level.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);if(!level.addFreshEntity(e))throw new AssertionError("real restart entity publication");return e;}
    @GameTest(templateNamespace="reignofnether_kill_restart",template="ore_mine_test_arena",batch="kill_value_restart",timeoutTicks=240)
    public static void nativeFallenSaveThenPaidReviveRetainsOneEconomicValue(GameTestHelper h) throws Exception {
        Observer o=new Observer(h.getLevel());MinecraftForge.EVENT_BUS.register(o);
        switch(System.getProperty("resource.kill.restart.phase","")){
            case "prepare"->prepare(h,o);case "verify"->verify(h,o);default->throw new IllegalArgumentException("killValueRestartPhase must be prepare or verify");
        }
    }
    static void prepare(GameTestHelper h,Observer o){
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(KILLER)==null&&PlayerServerEvents.getRTSPlayer(VICTIM)==null,"new isolated restart fixtures");
        CompoundTag expected=new CompoundTag();expected.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());List<Long> chunks=new ArrayList<>();
        for(int x=512;x<=516;x++)for(int z=512;z<=514;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!h.getLevel().getForcedChunks().contains(key),"do not adopt unrelated forced chunks");h.getLevel().setChunkForced(x,z,true);chunks.add(key);}expected.putLongArray("forcedChunks",chunks);
        Resources a=create(KILLER);create(VICTIM);BuildingPlacement shrine=install(h.getLevel(),Buildings.SHRINE_OF_PROSPERITY,ORIGIN);install(h.getLevel(),Buildings.TOWN_CENTRE,ORIGIN.offset(40,0,0));
        var attacker=spawn(h.getLevel(),EntityRegistrar.VILLAGER_UNIT.get(),KILLER,ORIGIN.offset(5,1,40));var hero=spawn(h.getLevel(),EntityRegistrar.ROYAL_GUARD_UNIT.get(),VICTIM,ORIGIN.offset(8,1,40));expected.putUUID("attacker",attacker.getUUID());expected.putUUID("hero",hero.getUUID());
        h.runAtTickTime(35,()->{h.assertTrue(shrine.isBuilt,"real revival anchor complete");h.assertTrue(!hero.getPersistentData().contains(RtsKillValueClaims.KEY),"initial hero has no manufactured economic claim");KillBridgeCoverageGameTests.configuredHeroDeath(h,hero,attacker);h.assertTrue(hero.isDeadOrDying(),"first native hero death occurs");});
        h.runAtTickTime(70,()->{
            h.assertTrue(o.committed==1&&a.ore==10001&&RtsKillValueClaims.isClaimed(hero),"first life credited once through real confirmed event");
            HeroUnitSave fallen=HeroUnit.getFallenHero(false,VICTIM,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId());h.assertTrue(fallen!=null&&fallen.economicValue!=null,"native fallen record stores permanent economic metadata");expected.put("provenance",fallen.economicValue.copy());expected.putUUID("origin",RtsKillValueClaims.valueId(hero));expected.put("killerAccount",snapshot(a));expected.put("victimAccount",snapshot(account(VICTIM)));
            PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(h.getLevel());ResourcesServerEvents.saveResources(h.getLevel());UnitServerEvents.saveFallenHeroUnits(h.getLevel());
            try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}MinecraftForge.EVENT_BUS.unregister(o);System.out.println("RTS_KILL_VALUE_RESTART_PREPARED origin="+expected.getUUID("origin"));h.succeed();
        });
    }
    static void verify(GameTestHelper h,Observer o)throws Exception{
        h.assertTrue(MANIFEST.isFile(),"previous JVM expectations exist");CompoundTag expected=NbtIo.readCompressed(MANIFEST);h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"verify is truly a different JVM");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(h.getLevel().getForcedChunks().contains(key),"normal world load restored forced chunks");ChunkPos c=new ChunkPos(key);h.getLevel().getChunk(c.x,c.z);}
        h.assertTrue(PlayerServerEvents.getRTSPlayer(KILLER)!=null&&PlayerServerEvents.getRTSPlayer(VICTIM)!=null,"ordinary startup restored both RTS identities");Resources a=account(KILLER);h.assertTrue(snapshot(a).equals(expected.getCompound("killerAccount"))&&snapshot(account(VICTIM)).equals(expected.getCompound("victimAccount")),"normal load preserves payout, epoch and revision without replay");
        HeroUnitSave fallen=HeroUnit.getFallenHero(false,VICTIM,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId());h.assertTrue(fallen!=null&&fallen.uuid.equals(expected.getUUID("hero").toString())&&fallen.economicValue.equals(expected.get("provenance")),"ordinary HeroUnitSaveData loader restored exact permanently claimed lineage");
        ProductionPlacement shrine=(ProductionPlacement)BuildingServerEvents.getBuildings().stream().filter(p->p.ownerName.equals(VICTIM)&&p.originPos.equals(ORIGIN)).findFirst().orElseThrow();
        final LivingEntity[] revived={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getEntity(expected.getUUID("attacker")) instanceof Mob,"wait ordinary saved shooter entity load, never inject replacement"))
          .thenExecute(()->{
            h.assertTrue(shrine.isBuilt&&shrine.getBlocksPlaced()>0,"restored native revival structure exists in world");
            var prod=ProductionItems.ROYAL_GUARD_REVIVE;
            Entity oldBody=h.getLevel().getEntity(expected.getUUID("hero"));
            System.out.println("RTS_KILL_RESTART_OLD_BODY "+(oldBody==null?"absent":oldBody.getUUID()+":"+((LivingEntity)oldBody).getHealth()+":"+oldBody.isRemoved()+":ticks="+oldBody.tickCount));
            h.assertTrue(oldBody==null || (oldBody instanceof LivingEntity && !((LivingEntity)oldBody).isAlive()),"ordinary reload never restores the fallen hero as a living duplicate; corpse may naturally expire");
            System.out.println("RTS_KILL_RESTART_REVIVE_DIAGNOSTIC error="+prod.getProduceErrorMsg(shrine)+" queued="+prod.itemIsBeingProduced(false,VICTIM)+" affordable="+prod.canAfford(shrine)+" currentPop="+UnitServerEvents.getCurrentPopulation(VICTIM)+" popSupply="+BuildingServerEvents.getTotalPopulationSupply(VICTIM)+" heroes="+HeroUnit.getHeroes(false,VICTIM).stream().map(hero->((LivingEntity)hero).getUUID()+":"+((LivingEntity)hero).getHealth()+":"+((LivingEntity)hero).isRemoved()).toList()+" building="+shrine.getBuilding().getClass().getName()+" queue="+shrine.productionQueue.size());
            h.assertTrue(shrine.startProductionItem(ProductionItems.ROYAL_GUARD_REVIVE),"normal restored fallen hero enters real paid production queue");shrine.productionQueue.get(0).ticksLeft=2;
          }).thenIdle(12).thenExecute(()->{
            var heroes=HeroUnit.getHeroes(false,VICTIM,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId()).stream().filter(hero -> ((LivingEntity)hero).isAlive()).toList();h.assertTrue(heroes.size()==1,"native queue spawned exactly one hero after restart");revived[0]=(LivingEntity)heroes.get(0);((Mob)revived[0]).setNoAi(true);
            h.assertTrue(!revived[0].getUUID().equals(expected.getUUID("hero"))&&RtsKillValueClaims.valueId(revived[0]).equals(expected.getUUID("origin"))&&RtsKillValueClaims.isClaimed(revived[0]),"native new-entity revival retained persisted economic claim");
            h.assertTrue("hud.hero.reignofnether.error.duplicate".equals(ProductionItems.ROYAL_GUARD_REVIVE.getProduceErrorMsg(shrine)),"actual living revived hero still blocks duplicate revival");
            Mob attacker=(Mob)h.getLevel().getEntity(expected.getUUID("attacker"));attacker.setNoAi(true);KillBridgeCoverageGameTests.configuredHeroDeath(h,revived[0],attacker);h.assertTrue(revived[0].isDeadOrDying(),"restored-and-revived hero really dies again");
          }).thenIdle(5).thenExecute(()->{
            h.assertTrue(snapshot(a).equals(expected.getCompound("killerAccount"))&&o.committed==0,"new JVM second life cannot repay or mutate killer account");
            MinecraftForge.EVENT_BUS.unregister(o);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Unit u&&u.getOwnerName().startsWith(PREFIX)).toList().forEach(Entity::discard);BuildingServerEvents.getBuildings().removeIf(p->p.ownerName.startsWith(PREFIX));HeroServerEvents.fallenHeroes.removeIf(s->s.ownerName.startsWith(PREFIX));for(long key:expected.getLongArray("forcedChunks")){ChunkPos c=new ChunkPos(key);h.getLevel().setChunkForced(c.x,c.z,false);}System.out.println("RTS_KILL_VALUE_PROCESS_RESTART_PASS native_fallen_save paid_revive permanent_claim");
          }).thenSucceed();
    }
}
