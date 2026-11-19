// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.ShadowGhostUnit;
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
public final class ShadowGhostProductionGameTests {
    private static final String PREFIX = "ShadowGhostProdGT_";
    private static final String BATCH = "dreamseekers_shadow_ghost_production";
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

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=750)
    public static void exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources owner=f.account("queue",DreamseekersMod.DREAMSEEKERS,5000);
        var anchor=(ProductionPlacement)f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,owner.ownerName,38,4);
        var court=(ProductionPlacement)f.install(DreamseekersMod.BEETLE_SANCTUARY,owner.ownerName,4,42);
        var recipe=DreamseekersMod.SHADOW_GHOST_PRODUCTION;
        h.assertTrue(DreamseekersMod.DREAM_HALL.productions.get().equals(java.util.List.of(DreamseekersMod.BONE_WING_PRODUCTION,DreamseekersMod.WIND_KNIGHT_PRODUCTION,DreamseekersMod.STONE_SPIRIT_THROWER_PRODUCTION,DreamseekersMod.BEETLE_MOTHER_PRODUCTION,recipe,DreamseekersMod.SHADOW_SQUEAL_GHOST_PRODUCTION,DreamseekersMod.TERRORBEAK_PRODUCTION,DreamseekersMod.FRIENDLY_GHOST_PRODUCTION,DreamseekersMod.SHADOW_HAND_PRODUCTION,DreamseekersMod.ASH_BONE_WING_PRODUCTION,DreamseekersMod.SHADOW_GOLEM_PRODUCTION,DreamseekersMod.WEEPING_SQUEAL_GHOST_PRODUCTION,DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION,DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION)),"API49 third-page left/right appended; Hall exact Q Bone, W Wind, E Thrower, R Mother, T Ghost whitelist");
        h.assertTrue(DreamseekersMod.BEETLE_SANCTUARY.productions.get().size()==6&&DreamseekersMod.SCAFFOLD_BUILDINGS.size()==6&&DreamseekersMod.FUNCTIONAL_BUILDINGS.size()==8,"court six unit slots untouched; laboratory is a separate paid research producer and eight extension buildings remain registered");
        h.assertTrue(!anchor.startProductionItem(recipe)&&!court.startProductionItem(recipe)&&!hall.startProductionItem(DreamseekersMod.JELLYFISH_PRODUCTION),"cross-producer recipe forgery rejected");
        h.assertTrue(hall.request(actor(f.level,owner.ownerName),new com.dreamseekers.function.DreamFunctionRequest.Production(DreamseekersMod.id("shadow_ghost_unit"))).status()==com.dreamseekers.function.DreamFunctionResult.Status.UNAVAILABLE,"generic Production service remains disabled despite exact paid whitelist");
        try {
            var field=com.solegendary.reignofnether.building.production.ProductionItemList.class.getDeclaredField("productions");field.setAccessible(true);
            var bindings=(java.util.Map<?,?>)field.get(DreamseekersMod.DREAM_HALL.productions);
            h.assertTrue(bindings.values().stream().map(k->((com.solegendary.reignofnether.keybinds.Keybinding)k).buttonLabel).toList().equals(java.util.List.of("Q","W","E","R","T","Y","Q","W","E","R","T","Y","Q","W")),"page1/page2 QWERTY unchanged; page3 Q/W left/right are isolated by Hall page adapter");
        } catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
        h.assertTrue(hall.startProductionItem(recipe),"real owned completed Hall starts real paid queue");
        f.balance(owner,5000-recipe.defaultCost.food,5000,5000-recipe.defaultCost.ore,"one exact provisional cost");
        h.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==recipe.defaultCost.population,"queue reserves population");
        h.runAtTickTime(620,()->{
            h.assertTrue(hall.productionQueue.isEmpty()&&ownedShadowGhost(owner.ownerName)==1,"full unaccelerated train timer produced exactly one native shadow ghost");
            h.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==recipe.defaultCost.population,"paid reservation converts to one unit");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==1,"score only once after actual spawn");
            f.balance(owner,5000-recipe.defaultCost.food,5000,5000-recipe.defaultCost.ore,"completion no second charge");
            System.out.println("DREAMSEEKERS_SHADOW_GHOST_FULL_PAID_HALL_QUEUE_PASS");h.succeed();
        });
    }
    @GameTest(template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=280)
    public static void blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper helper) {
        Fixture f=new Fixture(helper);
        Resources owner=f.account("blockedexit",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,4,4);
        var hall=(ProductionPlacement)f.install(DreamseekersMod.DREAM_HALL,owner.ownerName,38,4);
        f.install(DreamseekersMod.CONTRACT_ECOLOGY,owner.ownerName,4,42);
        var shadow_ghost=DreamseekersMod.SHADOW_GHOST_PRODUCTION;
        BlockPos point=hall.getUnitSpawnPoint(f.level,false,net.minecraft.core.Vec3i.ZERO);
        helper.assertTrue(point.equals(hall.getDefaultOutdoorSpawnPoint()),"shared candidate extractor preserves old default spawn point");
        for(BlockPos side:java.util.List.of(point.east(),point.west(),point.north(),point.south()))
            f.level.setBlock(side.above(),Blocks.STONE.defaultBlockState(),2); // Basin keeps the later fluid fixture local.
        f.level.setBlock(point.above(),Blocks.STONE.defaultBlockState(),2);
        helper.assertTrue(hall.startProductionItem(shadow_ghost),"blocked exit can reserve a paid queue, but cannot produce into a wall");
        hall.productionQueue.get(0).ticksLeft=20; // Isolate completion-window gating; full real timer is a separate test.
        float[] paused={-1};
        helper.runAtTickTime(80,()->{
            helper.assertTrue(hall.productionQueue.size()==1 && ownedShadowGhost(owner.ownerName)==0,"solid blocked exit retains paid queue without unsafe entity");
            paused[0]=hall.productionQueue.get(0).ticksLeft;
            helper.assertTrue(paused[0]<=10 && paused[0]>=0,"preflight checks only final completion window");
            helper.assertTrue(PlayerServerEvents.getRTSPlayer(owner.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==0,"blocked completion awards no phantom production score");
            f.level.setBlock(point.above(),Blocks.WATER.defaultBlockState(),2);
        });
        helper.runAtTickTime(120,()->{
            helper.assertTrue(hall.productionQueue.size()==1 && hall.productionQueue.get(0).ticksLeft==paused[0]
                    && ownedShadowGhost(owner.ownerName)==0,"flooded exit remains paused without consuming or restarting queue");
            f.level.setBlock(point.above(),Blocks.AIR.defaultBlockState(),2);
            for(BlockPos side:java.util.List.of(point.east(),point.west(),point.north(),point.south()))
                f.level.setBlock(side.above(),Blocks.AIR.defaultBlockState(),2);
        });
        helper.runAtTickTime(200,()->{
            helper.assertTrue(hall.productionQueue.isEmpty() && ownedShadowGhost(owner.ownerName)==1,"opening exit resumes original queue and spawns once");
            helper.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e -> e instanceof ShadowGhostUnit unit && owner.ownerName.equals(unit.getOwnerName()))
                    .allMatch(f.level::noCollision),"actual spawned shadow_ghost has non-clipping native collision body");
            f.balance(owner,5000-shadow_ghost.defaultCost.food,5000-shadow_ghost.defaultCost.wood,5000-shadow_ghost.defaultCost.ore,"blocked/flooded retries never charge twice");
            helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==shadow_ghost.defaultCost.population,"paused queue replaced by exactly one unit population");
            System.out.println("DREAMSEEKERS_SHADOW_GHOST_PRODUCTION_PASS blocked_flooded_exit_pause_resume");
            helper.succeed();
        });
    }
    @GameTest(template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=100)
    public static void populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Resources owner=f.account("cap",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,4,4);
        var hall=(ProductionPlacement)f.install(DreamseekersMod.DREAM_HALL,owner.ownerName,38,4);
        f.install(DreamseekersMod.CONTRACT_ECOLOGY,owner.ownerName,4,42);
        var shadow_ghost=DreamseekersMod.SHADOW_GHOST_PRODUCTION;
        int accepted=0;
        while (hall.startProductionItem(shadow_ghost) && ++accepted<30) { }
        helper.assertTrue(accepted>0 && accepted<30,"real population supply rejects excess queued shadow_ghosts");
        helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==accepted*shadow_ghost.defaultCost.population,"all accepted queues counted once");
        f.balance(owner,5000-accepted*shadow_ghost.defaultCost.food,5000-accepted*shadow_ghost.defaultCost.wood,5000-accepted*shadow_ghost.defaultCost.ore,"failed excess queue never charges");
        while(!hall.productionQueue.isEmpty()) helper.assertTrue(hall.cancelProductionItem(shadow_ghost,true),"queued cancellation succeeds");
        helper.assertTrue(!hall.cancelProductionItem(shadow_ghost,true) && !hall.cancelProductionItem(shadow_ghost,false),"repeat empty cancellation is a no-op");
        f.balance(owner,5000,5000,5000,"all real cancellations refund exactly once");
        helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==0,"all queued population released");
        var wildType=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("shadow_ghost"));
        helper.assertTrue(wildType!=null,"original shadow_ghost registry remains available");
        var wild=wildType.create(f.level);
        helper.assertTrue(wild instanceof net.pasterdream.entity.ShadowGhostEntity && !(wild instanceof Unit),"wild native shadow_ghost is not a free RTS unit");
        wild.moveTo(hall.getDefaultOutdoorSpawnPoint().getX()+.5,hall.getDefaultOutdoorSpawnPoint().getY()+1,hall.getDefaultOutdoorSpawnPoint().getZ()+.5);
        helper.assertTrue(f.level.addFreshEntity(wild),"native wild control spawns independently");
        helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==0 && ownedShadowGhost(owner.ownerName)==0,"native wild control does not enter paid unit ledger");
        f.balance(owner,5000,5000,5000,"native wild control cannot spend or grant RTS resources");
        wild.discard();
        owner.food=0;
        helper.assertTrue(!hall.startProductionItem(shadow_ghost),"insufficient food rejected");
        f.balance(owner,0,5000,5000,"failed payment cannot become negative or spawn");
        helper.assertTrue(ownedShadowGhost(owner.ownerName)==0,"failed/replayed cancellations spawn no shadow_ghost");
        System.out.println("DREAMSEEKERS_SHADOW_GHOST_PRODUCTION_PASS population_cost_cancel");
        helper.succeed();
    }
    @GameTest(template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=100)
    public static void actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper helper) throws Exception {
        Fixture f=new Fixture(helper);
        Resources owner=f.account("packet",DreamseekersMod.DREAMSEEKERS,5000);
        Resources attacker=f.account("attacker",DreamseekersMod.DREAMSEEKERS,5000);
        Resources unrelated=f.account("unrelatedsandbox",Factions.NONE,0);
        f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,4,4);
        var hall=(ProductionPlacement)f.install(DreamseekersMod.DREAM_HALL,owner.ownerName,38,4);
        f.install(DreamseekersMod.CONTRACT_ECOLOGY,owner.ownerName,4,42);
        ServerPlayer ownerActor=actor(f.level,owner.ownerName), attackerActor=actor(f.level,attacker.ownerName);
        NetworkEvent.Context ownerContext=context(ownerActor), attackerContext=context(attackerActor);
        helper.assertTrue(ownerContext.getSender()==ownerActor && attackerContext.getSender()==attackerActor,"actual Forge contexts carry intended authenticated actors");
        String id=ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(DreamseekersMod.SHADOW_GHOST_PRODUCTION).toString();
        send(BuildingAction.START_PRODUCTION,id,hall,attackerContext);
        helper.assertTrue(hall.productionQueue.isEmpty(),"unrelated sandbox does not let foreign sender spend budget");
        f.balance(owner,5000,5000,5000,"foreign production rejected without debit");
        ServerLevel nether=f.level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether!=null,"isolated server provides second dimension for real context test");
        NetworkEvent.Context wrongDimension=context(actor(nether,owner.ownerName));
        send(BuildingAction.START_PRODUCTION,id,hall,wrongDimension);
        helper.assertTrue(hall.productionQueue.isEmpty(),"same owner in another dimension rejected");
        send(BuildingAction.START_PRODUCTION,"bad registry @",hall,ownerContext);
        helper.assertTrue(hall.productionQueue.isEmpty(),"malformed registry packet is a safe no-op");
        send(BuildingAction.START_PRODUCTION,id,hall,ownerContext);
        helper.assertTrue(hall.productionQueue.size()==1,"real authorized handler starts one paid queue");
        send(BuildingAction.CANCEL_PRODUCTION,id,hall,attackerContext);
        helper.assertTrue(hall.productionQueue.size()==1,"foreign sender cannot cancel another owner's queue");
        AlliancesServerEvents.addAlliance(owner.ownerName,attacker.ownerName);
        try {
            send(BuildingAction.CANCEL_PRODUCTION,id,hall,attackerContext);
            helper.assertTrue(hall.productionQueue.size()==1,"alliance alone grants no production control");
            AlliancesServerEvents.playersWithAlliedControl.add(owner.ownerName);
            send(BuildingAction.CANCEL_PRODUCTION,id,hall,attackerContext);
            helper.assertTrue(hall.productionQueue.isEmpty(),"explicit owner-granted allied control is honored");
            send(BuildingAction.CANCEL_PRODUCTION,id,hall,ownerContext);
            f.balance(owner,5000,5000,5000,"legitimate allied cancel plus replay refunds once");
        } finally {
            AlliancesServerEvents.playersWithAlliedControl.remove(owner.ownerName);
            AlliancesServerEvents.removeAlliance(owner.ownerName,attacker.ownerName);
        }
        helper.assertTrue(PlayerServerEvents.getRTSPlayer(unrelated.ownerName)!=null,"unrelated sandbox fixture remained active throughout exploit checks");
        System.out.println("DREAMSEEKERS_SHADOW_GHOST_PRODUCTION_PASS authenticated_handler_ownership_dimension_alliance");
        helper.succeed();
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=340)
    public static void buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources owner=f.account("combatowner",DreamseekersMod.DREAMSEEKERS,5000);
        Resources enemy=f.account("combatenemy",DreamseekersMod.DREAMSEEKERS,5000);
        var target=f.install(DreamseekersMod.BEETLE_SANCTUARY,enemy.ownerName,38,4);
        ShadowGhostUnit mother=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(f.level);mother.setOwnerName(owner.ownerName);
        for(int dx=-10;dx<=0;dx++)for(int dz=-2;dz<=2;dz++) {
            f.level.setBlock(target.originPos.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);
            for(int dy=1;dy<5;dy++)f.level.setBlock(target.originPos.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
        }
        BlockPos start=target.originPos.offset(-8,1,0);mother.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);
        h.assertTrue(f.level.addFreshEntity(mother),"real building attacker registered");
        var goal=(com.dreamseekers.unit.TeamSafeBuildingAttackGoal)mother.getAttackBuildingGoal();
        int blocks=target.getBlocksPlaced();
        mother.setAttackBuildingTarget(target.originPos,true);h.assertTrue(goal.getBuildingTarget()==target&&goal.forced,"same-faction enemy building is attackable");
        AlliancesServerEvents.addAlliance(owner.ownerName,enemy.ownerName);goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null&&target.getBlocksPlaced()==blocks,"alliance change cancels building damage");
        mother.setAttackBuildingTarget(target.originPos,true);h.assertTrue(goal.getBuildingTarget()==null&&!goal.forced,"denied forced friendly attack leaves no sticky forced flag");
        AlliancesServerEvents.removeAlliance(owner.ownerName,enemy.ownerName);
        goal.setBuildingTarget(target.originPos);BuildingServerEvents.getBuildings().remove(target);goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null,"detached placement reference cannot be damaged");BuildingServerEvents.getBuildings().add(target);
        goal.setBuildingTarget(target.originPos);target.isDestroyedServerside=true;goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null,"destroyed placement is invalid");target.isDestroyedServerside=false;
        goal.setBuildingTarget(target.originPos);target.ownerName=owner.ownerName;goal.tick();
        h.assertTrue(goal.getBuildingTarget()==null,"capture to own owner cancels pending attack");target.ownerName=enemy.ownerName;
        ServerLevel nether=f.level.getServer().getLevel(Level.NETHER);h.assertTrue(nether!=null,"real second world exists");
        ShadowGhostUnit other=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(nether);other.setOwnerName(owner.ownerName);other.moveTo(start.getX()+.5,90,start.getZ()+.5);
        h.assertTrue(nether.addFreshEntity(other),"cross-world attacker uses real Nether entity");
        var otherGoal=(com.dreamseekers.unit.TeamSafeBuildingAttackGoal)other.getAttackBuildingGoal();otherGoal.setBuildingTarget(target.originPos);otherGoal.tick();
        h.assertTrue(otherGoal.getBuildingTarget()==null&&target.getBlocksPlaced()==blocks,"same coordinates in another world never select or damage overworld structure");other.discard();
        net.minecraft.world.phys.Vec3[] held={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother),"wait real flying ticks")).thenExecute(()->{
            com.solegendary.reignofnether.unit.interfaces.Unit.fullResetBehaviours(mother);mother.setHoldPosition(true);goal.setBuildingTarget(target.originPos);held[0]=mother.position();
        }).thenIdle(70).thenExecute(()->{
            h.assertTrue(mother.getHoldPosition()&&mother.position().distanceToSqr(held[0])<.2&&target.getBlocksPlaced()==blocks,"HOLD cannot start a distant building chase or remote damage: held="+held[0]+" now="+mother.position()+" blocks="+blocks+"/"+target.getBlocksPlaced()+" hold="+mother.getHoldPosition());
            com.solegendary.reignofnether.unit.UnitServerEvents.addActionItem(owner.ownerName,
                com.solegendary.reignofnether.unit.UnitAction.ATTACK_BUILDING,-1,new int[]{mother.getId()},target.originPos,BlockPos.ZERO);
        }).thenIdle(160).thenExecute(()->{
            h.assertTrue(!mother.getHoldPosition()&&target.getBlocksPlaced()<blocks,"actual ATTACK_BUILDING releases hold, approaches and damages enemy structure");
            goal.stopAttacking();System.out.println("DREAMSEEKERS_SHADOW_GHOST_BUILDING_TEAM_DIMENSION_LIFECYCLE_HOLD_PASS");
        }).thenSucceed();
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void buildingImpactRechecksWallStunFreezeAndAlliance(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);Resources a=f.account("impactOwner",DreamseekersMod.DREAMSEEKERS,5000),b=f.account("impactEnemy",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var target=f.install(DreamseekersMod.DREAM_HALL,b.ownerName,38,4);
        var ghost=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(f.level);ghost.setOwnerName(a.ownerName);ghost.setHoldPosition(true);
        ghost.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(100); // Deterministic final-impact positive control, not production balance.
        ghost.moveTo(target.minCorner.getX()-1.5,target.originPos.getY()+1,target.centrePos.getZ()+.5);
        h.assertTrue(f.level.addFreshEntity(ghost),"genuine paid building attacker");
        int wallX=target.minCorner.getX()-1;
        for(int z=target.minCorner.getZ()-3;z<=target.maxCorner.getZ()+3;z++)for(int y=target.originPos.getY()+1;y<=target.maxCorner.getY()+4;y++)
            f.level.setBlock(new BlockPos(wallX,y,z),Blocks.STONE.defaultBlockState(),2);
        h.startSequence().thenWaitUntil(()->h.assertTrue(ghost.tickCount>2&&f.level.getEntity(ghost.getId())==ghost&&f.level.getEntity(ghost.getUUID())==ghost,"published impact actor")).thenExecute(()->{
            ghost.setAttackBuildingTarget(target.originPos,true);
            var goal=(com.dreamseekers.shadow.ShadowGhostBuildingAttackGoal)ghost.getAttackBuildingGoal();
            h.assertTrue(goal.getBuildingTarget()==target&&goal.isAttacking(),"actual valid enemy and legacy melee reach established before negative checks");
            int before=target.getBlocksPlaced();readyImpact(goal);goal.tick();h.assertTrue(target.getBlocksPlaced()==before,"terrain wall prevents through-wall building damage");
            for(int z=target.minCorner.getZ()-3;z<=target.maxCorner.getZ()+3;z++)for(int y=target.originPos.getY()+1;y<=target.maxCorner.getY()+4;y++)f.level.setBlock(new BlockPos(wallX,y,z),Blocks.AIR.defaultBlockState(),2);
            for(var effect:java.util.List.of(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get(),com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())){
                ghost.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,20));readyImpact(goal);goal.tick();h.assertTrue(target.getBlocksPlaced()==before,"same callback status prevents damage before four-tick core reset");ghost.removeEffect(effect);
            }
            readyImpact(goal);goal.tick();h.assertTrue(target.getBlocksPlaced()<before,"exposed real target surface allows ordinary building impact");
            int after=target.getBlocksPlaced();AlliancesServerEvents.addAlliance(a.ownerName,b.ownerName);readyImpact(goal);goal.tick();
            h.assertTrue(target.getBlocksPlaced()==after&&goal.getBuildingTarget()==null,"live alliance immediately cancels building damage");
            AlliancesServerEvents.removeAlliance(a.ownerName,b.ownerName);h.succeed();
        });
    }
    private static void readyImpact(com.dreamseekers.shadow.ShadowGhostBuildingAttackGoal goal){
        try{var field=com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal.class.getDeclaredField("ticksToNextBlockBreak");field.setAccessible(true);field.setInt(goal,0);}catch(Exception e){throw new RuntimeException(e);}
    }
    private static long ownedShadowGhost(String owner) {
        return UnitServerEvents.getAllUnits().stream().filter(e -> e instanceof ShadowGhostUnit unit && owner.equals(unit.getOwnerName())).count();
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
        var observed = action == BuildingAction.CANCEL_PRODUCTION && !building.productionQueue.isEmpty()
                ? building.productionQueue.get(0).queueEntryId : null;
        new BuildingProductionServerboundPacket(action,id,building.originPos,observed).handle(() -> context);
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
