package com.solegendary.reignofnether.building;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Baseline-compatible behavioural tests: only API64 public entry points are referenced. */
@GameTestHolder("api65_world") @PrefixGameTestTemplate(false)
public final class Api65WorldRegressionGameTests {
    private static final String BATCH="api65_world_baseline";
    private static final class ObservedPlacement extends BuildingPlacement {
        int breaks, damageCalls;
        ObservedPlacement(ServerLevel level,BlockPos pos,String owner) {
            super(Buildings.BARRACKS,level,pos,Rotation.NONE,owner,
                    new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);
            isBuilt=true;
        }
        @Override public void onBlockBreak(ServerLevel level,BlockPos pos,boolean breakBlocks){breaks++;}
        @Override public void destroyRandomBlocks(double amount){if(amount>0)damageCalls++;}
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void nativeAvailabilityRejectsEditorWrongFactionAndMissingPrerequisite(GameTestHelper h) {
        String owner="WorldGate"+Math.abs(h.absolutePos(BlockPos.ZERO).asLong());
        var player=RTSPlayer.getNewBot(owner,Factions.VILLAGERS);PlayerServerEvents.rtsPlayers.add(player);
        try {
            h.assertTrue(Buildings.TOWN_CENTRE.getBuildAvailabilityError(owner)==null,"own native capitol positive control");
            h.assertTrue(Buildings.CAPTURABLE_BEACON.getBuildAvailabilityError(owner)!=null,"ordinary owner cannot request editor-only free invulnerable beacon");
            h.assertTrue(Buildings.MAUSOLEUM.getBuildAvailabilityError(owner)!=null,"ordinary owner cannot build wrong faction capitol");
            h.assertTrue(Buildings.BARRACKS.getBuildAvailabilityError(owner)!=null,"ordinary owner requires completed native prerequisite");
        } finally {PlayerServerEvents.rtsPlayers.remove(player);}
        h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void sameCoordinateForeignBreakDoesNotDamageBuilding(GameTestHelper h) {
        ServerLevel own=h.getLevel(),other=own.getServer().getLevel(Level.NETHER);
        h.assertTrue(other!=null&&other!=own,"real second dimension available");
        BlockPos arena=h.absolutePos(new BlockPos(3,2,3)),pos=new BlockPos(arena.getX(),64,arena.getZ());
        h.assertTrue(!own.isOutsideBuildHeight(pos)&&!other.isOutsideBuildHeight(pos),"event position is inside both dimensions build heights");
        var ownBefore=own.getBlockState(pos);var otherBefore=other.getBlockState(pos);
        ObservedPlacement building=new ObservedPlacement(own,pos,"WorldBreak");BuildingServerEvents.getBuildings().add(building);
        try {
            own.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());other.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            h.assertTrue(own.getBlockState(pos).is(Blocks.STONE)&&other.getBlockState(pos).is(Blocks.STONE),"both same-coordinate event targets exist in their actual worlds");
            var actor=FakePlayerFactory.get(other,new GameProfile(UUID.randomUUID(),"WorldBreakActor"));
            BuildingServerEvents.onPlayerBlockBreak(new BlockEvent.BreakEvent(other,pos,Blocks.STONE.defaultBlockState(),actor));
            h.assertTrue(building.breaks==0,"foreign BreakEvent must not reach same-coordinate building callback");
            var local=FakePlayerFactory.get(own,new GameProfile(UUID.randomUUID(),"WorldBreakLocal"));
            BuildingServerEvents.onPlayerBlockBreak(new BlockEvent.BreakEvent(own,pos,Blocks.STONE.defaultBlockState(),local));
            h.assertTrue(building.breaks==1,"same-dimension positive control still reaches building");
        } finally {BuildingServerEvents.getBuildings().remove(building);own.setBlockAndUpdate(pos,ownBefore);other.setBlockAndUpdate(pos,otherBefore);}
        h.succeed();
    }
    private static ExplosionEvent.Detonate explosion(ServerLevel level,BlockPos pos) {
        var tnt=new PrimedTnt(level,pos.getX(),pos.getY(),pos.getZ(),null);
        var explosion=new Explosion(level,tnt,pos.getX(),pos.getY(),pos.getZ(),4f,new ArrayList<>(List.of(pos)));
        return new ExplosionEvent.Detonate(level,explosion,new ArrayList<>());
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void sameCoordinateForeignTntDoesNotDamageBuilding(GameTestHelper h) {
        ServerLevel own=h.getLevel(),other=own.getServer().getLevel(Level.NETHER);
        h.assertTrue(other!=null&&other!=own,"real second dimension available");
        BlockPos arena=h.absolutePos(new BlockPos(3,2,3)),pos=new BlockPos(arena.getX(),64,arena.getZ());
        h.assertTrue(!own.isOutsideBuildHeight(pos)&&!other.isOutsideBuildHeight(pos),"event position is inside both dimensions build heights");
        var ownBefore=own.getBlockState(pos);var otherBefore=other.getBlockState(pos);
        ObservedPlacement building=new ObservedPlacement(own,pos,"WorldExplosion");BuildingServerEvents.getBuildings().add(building);
        try {
            own.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());other.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            h.assertTrue(own.getBlockState(pos).is(Blocks.STONE)&&other.getBlockState(pos).is(Blocks.STONE),"both same-coordinate event targets exist in their actual worlds");
            BuildingServerEvents.onExplosion(explosion(other,pos));
            h.assertTrue(building.damageCalls==0,"foreign TNT event must not dispatch building damage");
            BuildingServerEvents.onExplosion(explosion(own,pos));
            h.assertTrue(building.damageCalls==1,"same-dimension TNT still dispatches real damage path");
        } finally {BuildingServerEvents.getBuildings().remove(building);own.setBlockAndUpdate(pos,ownBefore);other.setBlockAndUpdate(pos,otherBefore);}
        h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void nativeCommandCountSurvivesRegisteredCodecTwice(GameTestHelper h) {
        var command=new BuildingCommand(7,20,"say once","ON_CAPTURE");command.triggerCount=3;
        DataStorage state=new DataStorage();state.setData(BuildingServerEvents.BUILDING_COMMANDS,new ArrayList<>(List.of(command)));
        for(int i=0;i<2;i++)state=DataStorage.read(state.write(),h.getLevel().getServer());
        var restored=state.getData(BuildingServerEvents.BUILDING_COMMANDS).get(0);
        h.assertTrue(restored.triggerCount==3,"native registered codec retains consumed one-shot count after two writes");
        h.assertTrue(restored.tickCooldown==7&&restored.tickCooldownMax==20,"cooldown positive control unchanged");
        h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void duplicateCustomRegistrationDoesNotDeleteExistingPlacement(GameTestHelper h) {
        BlockPos first=h.absolutePos(new BlockPos(2,2,2)),second=first.offset(4,0,0);
        String name="WorldDuplicate"+Math.abs(first.asLong());
        var id=ResourceLocation.fromNamespaceAndPath("api65_world","registration_"+Math.abs(first.asLong()));
        h.getLevel().setBlockAndUpdate(first,Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(second,Blocks.STONE.defaultBlockState());
        h.getLevel().getStructureManager().getOrCreate(id).fillFromWorld(h.getLevel(),first,new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
        var before=new HashSet<>(CustomBuildingServerEvents.customBuildings);
        ObservedPlacement incumbent=new ObservedPlacement(h.getLevel(),second,"WorldDuplicateOwner");
        try {
            h.assertTrue(CustomBuildingServerEvents.createAndRegisterNewCustomBuilding(id,name,h.getLevel(),first,new Vec3i(1,1,1)),"first real registration succeeds");
            BuildingServerEvents.getBuildings().add(incumbent);
            boolean accepted=CustomBuildingServerEvents.createAndRegisterNewCustomBuilding(id,name,h.getLevel(),second,new Vec3i(1,1,1));
            h.assertTrue(!accepted,"same name at different origin is rejected");
            h.assertTrue(BuildingServerEvents.getBuildings().contains(incumbent),"failed registration cannot delete pre-existing live placement");
        } finally {
            BuildingServerEvents.getBuildings().removeIf(b->b==incumbent||b.originPos.equals(first));
            CustomBuildingServerEvents.customBuildings.removeIf(b->!before.contains(b));
            CustomBuildingServerEvents.saveCustomBuildings(h.getLevel());BuildingServerEvents.saveBuildings(h.getLevel());
        }
        h.succeed();
    }
}
