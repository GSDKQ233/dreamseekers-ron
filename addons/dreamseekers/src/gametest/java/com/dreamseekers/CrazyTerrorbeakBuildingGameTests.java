// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.CrazyTerrorbeakUnit;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/** Real paid queues and authenticated Forge handler; no GUI/socket/multiplayer claim. */
@net.minecraftforge.gametest.GameTestHolder(DreamseekersMod.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class CrazyTerrorbeakBuildingGameTests {
    private static final String PREFIX = "CrazyTerrorbeakProdGT_";
    private static final String BATCH = "dreamseekers_crazy_terrorbeak_production";
    private static final java.util.List<java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent>> SPAWN_GUARDS=new java.util.ArrayList<>();
    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        for(var guard:SPAWN_GUARDS)net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(guard);SPAWN_GUARDS.clear();
        for (var entity : new ArrayList<>(UnitServerEvents.getAllUnits()))
            if (entity instanceof Unit unit && unit.getOwnerName().startsWith(PREFIX)) entity.discard();
        BuildingServerEvents.getBuildings().removeIf(b -> b.ownerName.startsWith(PREFIX));
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
        AlliancesServerEvents.playersWithAlliedControl.removeIf(name -> name.startsWith(PREFIX));
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=340)
    public static void buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources owner=f.account("combatowner",DreamseekersMod.DREAMSEEKERS,5000);
        Resources enemy=f.account("combatenemy",DreamseekersMod.DREAMSEEKERS,5000);
        var target=f.install(DreamseekersMod.BEETLE_SANCTUARY,enemy.ownerName,38,4);
        CrazyTerrorbeakUnit terror=DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get().create(f.level);terror.setOwnerName(owner.ownerName);
        for(int dx=-10;dx<=0;dx++)for(int dz=-2;dz<=2;dz++) {
            f.level.setBlock(target.originPos.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);
            for(int dy=1;dy<5;dy++)f.level.setBlock(target.originPos.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
        }
        BlockPos start=target.originPos.offset(-8,1,0);terror.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);
        h.assertTrue(f.level.addFreshEntity(terror),"real building attacker registered");
        var goal=(com.dreamseekers.unit.TeamSafeBuildingAttackGoal)terror.getAttackBuildingGoal();
        int blocks=target.getBlocksPlaced();
        terror.setAttackBuildingTarget(target.originPos,true);h.assertTrue(goal.getBuildingTarget()==target&&goal.forced,"same-faction enemy building is attackable");
        AlliancesServerEvents.addAlliance(owner.ownerName,enemy.ownerName);goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null&&target.getBlocksPlaced()==blocks,"alliance change cancels building damage");
        terror.setAttackBuildingTarget(target.originPos,true);h.assertTrue(goal.getBuildingTarget()==null&&!goal.forced,"denied forced friendly attack leaves no sticky forced flag");
        AlliancesServerEvents.removeAlliance(owner.ownerName,enemy.ownerName);
        goal.setBuildingTarget(target.originPos);BuildingServerEvents.getBuildings().remove(target);goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null,"detached placement reference cannot be damaged");BuildingServerEvents.getBuildings().add(target);
        goal.setBuildingTarget(target.originPos);target.isDestroyedServerside=true;goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null,"destroyed placement is invalid");target.isDestroyedServerside=false;
        goal.setBuildingTarget(target.originPos);target.ownerName=owner.ownerName;goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null,"capture to own owner cancels pending attack");target.ownerName=enemy.ownerName;
        ServerLevel nether=f.level.getServer().getLevel(Level.NETHER);h.assertTrue(nether!=null,"real second world exists");
        CrazyTerrorbeakUnit other=DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get().create(nether);other.setOwnerName(owner.ownerName);other.moveTo(start.getX()+.5,90,start.getZ()+.5);
        h.assertTrue(nether.addFreshEntity(other),"cross-world attacker uses real Nether entity");
        var otherGoal=(com.dreamseekers.unit.TeamSafeBuildingAttackGoal)other.getAttackBuildingGoal();otherGoal.setBuildingTarget(target.originPos);otherGoal.tick();
        h.assertTrue(otherGoal.getBuildingTarget()==null&&target.getBlocksPlaced()==blocks,"same coordinates in another world never select or damage overworld structure");other.discard();
        net.minecraft.world.phys.Vec3[] held={null};
        var timeline=new CrazyTerrorbeakFixture.Timeline(h);
        timeline.at(0,()->{com.solegendary.reignofnether.unit.interfaces.Unit.fullResetBehaviours(terror);terror.setHoldPosition(true);goal.setBuildingTarget(target.originPos);held[0]=terror.position();});
        timeline.at(70,()->{
            h.assertTrue(terror.getHoldPosition()&&terror.position().distanceToSqr(held[0])<.2&&target.getBlocksPlaced()==blocks,"HOLD does not chase or damage distant building");
            com.solegendary.reignofnether.unit.UnitServerEvents.addActionItem(owner.ownerName,com.solegendary.reignofnether.unit.UnitAction.ATTACK_BUILDING,-1,new int[]{terror.getId()},target.originPos,BlockPos.ZERO);
        });
        timeline.at(230,()->{h.assertTrue(!terror.getHoldPosition()&&target.getBlocksPlaced()<blocks,"real ATTACK_BUILDING releases HOLD, approaches and damages");goal.stopAttacking();});
        timeline.start(terror,()->published(h,terror));
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=130)
    public static void noRecipeButtonOrForgedFreeQueueForUpgradeFoundation(GameTestHelper h) throws Exception {
        Fixture f=new Fixture(h);Resources owner=f.account("unavailable",DreamseekersMod.DREAMSEEKERS,5000);
        var anchor=(ProductionPlacement)f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,4,4);
        var hall=(ProductionPlacement)f.install(DreamseekersMod.DREAM_HALL,owner.ownerName,38,4);
        var court=(ProductionPlacement)f.install(DreamseekersMod.BEETLE_SANCTUARY,owner.ownerName,4,42);
        h.assertTrue(DreamseekersMod.DREAM_HALL.productions.get().size()==14&&DreamseekersMod.BEETLE_SANCTUARY.productions.get().size()==6,"Hall fourteen/court six current whitelist; no normal upgrade-branch production");
        h.assertTrue(!ReignOfNetherRegistries.PRODUCTION_ITEM.containsKey(DreamseekersMod.id("crazy_terrorbeak_unit")),"no registered normal/free recipe");
        var forged=new com.dreamseekers.production.DreamUnitProduction(DreamseekersMod.CRAZY_TERRORBEAK_UNIT,CrazyTerrorbeakUnit.COST,DreamseekersMod.CRAZY_TERRORBEAK_ICON,()->DreamseekersMod.DREAM_HALL,null,null);
        h.assertTrue(!hall.startProductionItem(forged)&&!anchor.startProductionItem(forged)&&!court.startProductionItem(forged),"even forged zero-currency metadata cannot bypass producer whitelist");
        var actor=actor(f.level,owner.ownerName);var ctx=context(actor);
        send(BuildingAction.START_PRODUCTION,"dreamseekers:crazy_terrorbeak_unit",hall,ctx);
        h.startSequence().thenIdle(10).thenExecute(()->{
            h.assertTrue(hall.productionQueue.isEmpty()&&anchor.productionQueue.isEmpty()&&court.productionQueue.isEmpty()&&ownedCrazyTerrorbeak(owner.ownerName)==0,"real production packet unknown recipe cannot spawn or queue");
            f.balance(owner,5000,5000,5000,"unavailable upgrade has no debit/refund");
        }).thenSucceed();
    }

    private static long ownedCrazyTerrorbeak(String owner) {
        return UnitServerEvents.getAllUnits().stream().filter(e -> e instanceof CrazyTerrorbeakUnit unit && owner.equals(unit.getOwnerName())).count();
    }
    private static ServerPlayer actor(ServerLevel level,String name) {
        return FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name));
    }
    private static NetworkEvent.Context context(ServerPlayer actor) throws Exception {
        Connection connection=new Connection(PacketFlow.SERVERBOUND);
        connection.setListener(actor.connection);
        var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,-1);
    }
    private static void send(BuildingAction action,String id,ProductionPlacement building,NetworkEvent.Context context) {
        new BuildingProductionServerboundPacket(action,id,building.originPos).handle(() -> context);
    }
    private static final class Fixture {
        final GameTestHelper helper;
        final ServerLevel level;
        Fixture(GameTestHelper helper) { this.helper=helper;level=helper.getLevel(); }
        Resources account(String suffix,Faction faction,int value) {
            String name=PREFIX+suffix;
            helper.assertTrue(PlayerServerEvents.getRTSPlayer(name)==null,"fixture owner is unique");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,faction));
            Resources account=new Resources(name,value,value,value);
            ResourcesServerEvents.resourcesList.add(account);
            return account;
        }
        BuildingPlacement install(Building definition,String owner,int x,int z) {
            BlockPos origin=BuildingAssetGameTests.outdoorPos(helper,x,1,z);
            for(int dx=-3;dx<28;dx++) for(int dz=-3;dz<28;dz++) {
                level.setBlock(origin.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);
                for(int dy=1;dy<15;dy++)level.setBlock(origin.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
            }
            BuildingAssetGameTests.clearPreexistingFixtureDrops(helper,origin.offset(-3,0,-3),31,31);
            for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,origin,Rotation.NONE))
                level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            BuildingPlacement placement=BuildingUtils.getNewBuildingPlacement(definition,level,origin,Rotation.NONE,owner,false);
            BuildingServerEvents.getBuildings().add(placement);
            placement.onBuilt();
            return placement;
        }
        void balance(Resources account,int food,int wood,int ore,String message) {
            helper.assertTrue(account.food==food && account.wood==wood && account.ore==ore,message+" actual="+account.food+"/"+account.wood+"/"+account.ore);
        }
    }
    private static boolean published(GameTestHelper h,net.minecraft.world.entity.Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

}
