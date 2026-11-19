package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ability.EnchantAbility;
import com.solegendary.reignofnether.blocks.*;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.building.buildings.placements.*;
import com.solegendary.reignofnether.building.buildings.villagers.Library;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.time.TimeServerEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Headless state/geometry projection tests, not a GUI or true two-integrated-world acceptance. */
@GameTestHolder("api65_world") @PrefixGameTestTemplate(false)
public final class Api65WorldStateGameTests {
    private static final String BATCH="api65_world_state";
    private static ArrayList<BuildingBlock> geometry(String structure,ServerLevel level,BlockPos pos){return BuildingUtils.getAbsoluteBlockData(BuildingBlockData.getBuildingBlocksFromNbt(structure,level),level,pos,Rotation.NONE);}
    private static TickEvent.LevelTickEvent tick(ServerLevel level){return new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,level,()->true);}
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void temporaryBlockQueueUsesSubmittingDimension(GameTestHelper h){
        ServerLevel own=h.getLevel(),other=own.getServer().getLevel(Level.NETHER);
        BlockPos arena=h.absolutePos(new BlockPos(3,2,3));BlockPos pos=new BlockPos(arena.getX(),64,arena.getZ());
        h.assertTrue(!own.isOutsideBuildHeight(pos)&&!other.isOutsideBuildHeight(pos),"test coordinate is valid in both dimension height ranges");
        var ownState=own.getBlockState(pos);var otherState=other.getBlockState(pos);var oldList=new ArrayList<>(BlockServerEvents.tempBlocks);
        try{
            BlockServerEvents.tempBlocks.clear();own.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());other.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            h.assertTrue(own.getBlockState(pos).is(Blocks.STONE)&&other.getBlockState(pos).is(Blocks.STONE),"both sentinels exist before submitting delayed work");
            BlockServerEvents.addTempBlock(other,pos,Blocks.ICE.defaultBlockState(),Blocks.STONE.defaultBlockState(),100,true);
            h.assertTrue(BlockServerEvents.tempBlocks.size()==1,"one real delayed block queued");BlockServerEvents.tempBlocks.get(0).tickAge=0;
            BlockServerEvents.onWorldTick(tick(own));
            h.assertTrue(own.getBlockState(pos).is(Blocks.STONE)&&other.getBlockState(pos).is(Blocks.STONE),"unrelated dimension tick cannot consume or write queued block");
            BlockServerEvents.onWorldTick(tick(other));
            h.assertTrue(other.getBlockState(pos).is(Blocks.ICE)&&own.getBlockState(pos).is(Blocks.STONE),"submitting dimension consumes its own delayed block");
        }finally{BlockServerEvents.tempBlocks.clear();BlockServerEvents.tempBlocks.addAll(oldList);own.setBlockAndUpdate(pos,ownState);other.setBlockAndUpdate(pos,otherState);}h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void overwrittenTemporaryBlockRetiresWithoutRestoringOldState(GameTestHelper h){
        BlockPos pos=h.absolutePos(new BlockPos(3,2,3));var old=h.getLevel().getBlockState(pos);
        try{
            var temporary=new TemporaryBlock(pos,Blocks.ICE.defaultBlockState(),Blocks.DIRT.defaultBlockState(),20,null);temporary.tickAge=100;temporary.isPlaced=true;h.getLevel().setBlockAndUpdate(pos,Blocks.GOLD_BLOCK.defaultBlockState());
            h.assertTrue(temporary.tick(h.getLevel()),"expired overwritten entry retires instead of leaking forever");
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.GOLD_BLOCK),"expiry does not resurrect old dirt over replacement gold");
        }finally{h.getLevel().setBlockAndUpdate(pos,old);}h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void stoppedSessionDropsItsPendingWorldWork(GameTestHelper h)throws Exception{
        BlockPos pos=h.absolutePos(new BlockPos(3,2,3));var old=h.getLevel().getBlockState(pos);var calls=new AtomicInteger();
        var priorTemp=new ArrayList<>(BlockServerEvents.tempBlocks);var priorLegacy=new HashMap<>(BlockServerEvents.blocksToPlace);
        var start=TimeServerEvents.class.getDeclaredField("serverStartTime");start.setAccessible(true);long previousStart=start.getLong(null);
        var left=TimeServerEvents.class.getDeclaredField("bloodMoonTicksLeft");left.setAccessible(true);int previousLeft=left.getInt(null);
        try{
            BlockServerEvents.tempBlocks.clear();BlockServerEvents.blocksToPlace.clear();h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            BlockServerEvents.addTempBlock(h.getLevel(),pos,Blocks.ICE.defaultBlockState(),Blocks.STONE.defaultBlockState(),100,true);
            BlockServerEvents.queueBlock(h.getLevel(),pos,Blocks.GOLD_BLOCK.defaultBlockState());
            TaskSchedulerServerEvents.schedule(0,calls::incrementAndGet);start.setLong(null,987654321L);left.setInt(null,80);
            var stop=new ServerStoppingEvent(h.getLevel().getServer());BlockServerEvents.stopping(stop);TaskSchedulerServerEvents.stopping(stop);TimeServerEvents.stopping(stop);
            BlockServerEvents.onWorldTick(tick(h.getLevel()));TaskSchedulerServerEvents.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,h.getLevel().getServer()));
            h.assertTrue(calls.get()==0&&BlockServerEvents.tempBlocks.isEmpty()&&h.getLevel().getBlockState(pos).is(Blocks.STONE),"stopped-session delayed tasks and block mutations cannot run");
            h.assertTrue(start.getLong(null)==0&&!TimeServerEvents.isBloodMoonActive(),"blood-moon and relative time origin reset at stop");
        }finally{BlockServerEvents.tempBlocks.clear();BlockServerEvents.tempBlocks.addAll(priorTemp);BlockServerEvents.blocksToPlace.clear();BlockServerEvents.blocksToPlace.putAll(priorLegacy);h.getLevel().setBlockAndUpdate(pos,old);start.setLong(null,previousStart);left.setInt(null,previousLeft);}h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void delayedCallbackCanEnqueueNextTickWithoutIteratorMutation(GameTestHelper h){
        var count=new AtomicInteger();TaskSchedulerServerEvents.schedule(0,()->{count.incrementAndGet();TaskSchedulerServerEvents.schedule(0,count::incrementAndGet);});
        var event=new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,h.getLevel().getServer());TaskSchedulerServerEvents.onServerTick(event);
        h.assertTrue(count.get()==1,"callback-enqueued task is deferred, not executed recursively");TaskSchedulerServerEvents.onServerTick(event);h.assertTrue(count.get()==2,"new task executes on next owning-server tick");h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void librarySnapshotRestoresUpgradeAndAbsoluteAutocastIdempotently(GameTestHelper h){
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));String owner="WorldSnapshot";
        var source=new ProductionPlacement(Buildings.LIBRARY,h.getLevel(),pos,Rotation.NONE,owner,geometry(Library.upgradedStructureName,h.getLevel(),pos),false);source.isBuilt=true;
        var target=new ProductionPlacement(Buildings.LIBRARY,h.getLevel(),pos,Rotation.NONE,owner,geometry(Library.structureName,h.getLevel(),pos),false);
        var ability=(EnchantAbility)source.getAbilities().stream().filter(a->a instanceof EnchantAbility).findFirst().orElseThrow();source.getDataStorage().setData(Library.AUTO_CAST_ENCHANT,ability);
        var tag=NativeBuildingSync.snapshot(source);var worldBefore=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();source.getBlocks().forEach(b->worldBefore.put(b.getBlockPos(),h.getLevel().getBlockState(b.getBlockPos())));
        h.assertTrue(source.getUpgradeLevel()==1&&target.getUpgradeLevel()==0,"real native geometry level positive control");
        h.assertTrue(NativeBuildingSync.apply(target,tag)&&NativeBuildingSync.apply(target,tag),"repeated same absolute snapshot accepted");
        h.assertTrue(target.getUpgradeLevel()==1&&target.getDataStorage().getData(Library.AUTO_CAST_ENCHANT)==ability,"upgrade and autocast stay on after duplicate snapshot, not toggle");
        source.getDataStorage().setData(Library.AUTO_CAST_ENCHANT,null);NativeBuildingSync.apply(target,NativeBuildingSync.snapshot(source));
        h.assertTrue(target.getDataStorage().getData(Library.AUTO_CAST_ENCHANT)==null,"explicit null autocast clears prior state");
        h.assertTrue(worldBefore.entrySet().stream().allMatch(e->h.getLevel().getBlockState(e.getKey()).equals(e.getValue())),"presentation restoration writes no world blocks");h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void beaconSnapshotCodecPreservesAuraAndTierWithoutActions(GameTestHelper h){
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));String owner="WorldBeaconSnapshot";
        var source=new BeaconPlacement(Buildings.BEACON,h.getLevel(),pos,Rotation.NONE,owner,geometry(Beacon.structureNameT3,h.getLevel(),pos),false);source.isBuilt=true;
        var target=new BeaconPlacement(Buildings.BEACON,h.getLevel(),pos,Rotation.NONE,owner,geometry(Beacon.structureName,h.getLevel(),pos),false);
        var old=h.getLevel().getBlockState(source.beaconPos);h.getLevel().setBlockAndUpdate(source.beaconPos,Blocks.BEACON.defaultBlockState());
        try{
            source.restoreSyncedAura(MobEffects.REGENERATION,true);
            var packet=new BuildingClientboundPacket(BuildingAction.SYNC_BLOCKS_AND_OWNER,BuildingClientboundPacket.EMPTY,"",pos,Rotation.NONE,owner,0,0,0,false,0,true,PortalPlacement.PortalType.BASIC,BlockPos.ZERO);packet.nativeState=NativeBuildingSync.snapshot(source);
            FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(buffer);var decoded=new BuildingClientboundPacket(buffer);
                h.assertTrue(buffer.readableBytes()==0&&decoded.nativeState.equals(packet.nativeState),"actual registered packet codec retains entire native snapshot");
                h.assertTrue(NativeBuildingSync.apply(target,decoded.nativeState)&&NativeBuildingSync.apply(target,decoded.nativeState),"repeated native state restores without ability invocation");
            }finally{buffer.release();}
            h.assertTrue(target.getUpgradeLevel()==3&&target.isBeaconActive()&&target.getAuraEffect()==MobEffects.REGENERATION,"late snapshot restores tier3 active regeneration");
            source.restoreSyncedAura(null,false);NativeBuildingSync.apply(target,NativeBuildingSync.snapshot(source));h.assertTrue(!target.isBeaconActive()&&target.getAuraEffect()==null,"deactivation absolute state also converges");
        }finally{h.getLevel().setBlockAndUpdate(source.beaconPos,old);}h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void nativeAvailabilityPreservesFactionPrerequisiteCheatAndEditorPolicy(GameTestHelper h){
        var factions=List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,com.solegendary.reignofnether.faction.Factions.MONSTERS,com.solegendary.reignofnether.faction.Factions.PIGLINS);
        var capitals=List.of(Buildings.TOWN_CENTRE,Buildings.MAUSOLEUM,Buildings.CENTRAL_PORTAL);
        var next=List.of(Buildings.BARRACKS,Buildings.GRAVEYARD,Buildings.PORTAL_BASIC);
        BlockPos pos=h.absolutePos(new BlockPos(3,2,3));var other=h.getLevel().getServer().getLevel(Level.NETHER);
        var all=new ArrayList<BuildingPlacement>();var players=new ArrayList<com.solegendary.reignofnether.player.RTSPlayer>();
        var beaconRule=h.getLevel().getGameRules().getRule(com.solegendary.reignofnether.registrars.GameRuleRegistrar.ALLOW_BEACONS);boolean wasBeacons=beaconRule.get();
        String owner="WorldPolicies"+Math.abs(pos.asLong());
        try{
            for(int index=0;index<3;index++){
                String name=owner+index;var player=com.solegendary.reignofnether.player.RTSPlayer.getNewBot(name,factions.get(index));players.add(player);com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers.add(player);
                h.assertTrue(capitals.get(index).getBuildAvailabilityError(name)==null,"own first capitol remains available for faction "+index);
                h.assertTrue(next.get(index).getBuildAvailabilityError(name)!=null,"missing prerequisite rejected for faction "+index);
                var foreign=new BuildingPlacement(capitals.get(index),other,pos,Rotation.NONE,name,new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);foreign.isBuilt=true;all.add(foreign);BuildingServerEvents.getBuildings().add(foreign);
                h.assertTrue(next.get(index).getBuildAvailabilityError(name)!=null,"foreign-dimension prerequisite never authorizes native build");
                var local=new BuildingPlacement(capitals.get(index),h.getLevel(),pos.offset(index,0,0),Rotation.NONE,name,new ArrayList<>(List.of(new BuildingBlock(pos.offset(index,0,0),Blocks.STONE.defaultBlockState()))),false);all.add(local);BuildingServerEvents.getBuildings().add(local);
                h.assertTrue(next.get(index).getBuildAvailabilityError(name)!=null,"unfinished prerequisite rejected");local.isBuilt=true;
                h.assertTrue(next.get(index).getBuildAvailabilityError(name)==null,"completed same-Level prerequisite positive control");
            }
            String villager=owner+0;com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(villager,"modifythephasevariance");beaconRule.set(true,h.getLevel().getServer());
            h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(villager)==null,"legitimate prerequisite cheat remains supported");
            h.assertTrue(Buildings.MAUSOLEUM.getBuildAvailabilityError(villager)!=null&&NativeBuildingAvailability.packetError(Buildings.CAPTURABLE_BEACON,villager)!=null,"prerequisite cheat is not faction/editor authority");
            beaconRule.set(false,h.getLevel().getServer());h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(villager)!=null,"disabled beacon rule still applies with prerequisite cheat");
            var editor=com.solegendary.reignofnether.player.RTSPlayer.getNewBot(owner+"Editor",com.solegendary.reignofnether.faction.Factions.NONE);players.add(editor);com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers.add(editor);
            h.assertTrue(NativeBuildingAvailability.packetError(Buildings.CAPTURABLE_BEACON,villager)==null&&NativeBuildingAvailability.packetError(Buildings.PORTAL_TRANSPORT,villager)==null,"existing shared native sandbox editor contract remains available");
        }finally{BuildingServerEvents.getBuildings().removeAll(all);com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers.removeAll(players);com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(owner+0,"modifythephasevariance");beaconRule.set(wasBeacons,h.getLevel().getServer());}h.succeed();
    }

}
