// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.BoneWingUnit;
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
public final class BoneWingProductionGameTests {
    private static final String PREFIX = "BoneWingProdGT_";
    private static final String BATCH = "dreamseekers_bone_wing_production";
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

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=760)
    public static void exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources owner=f.account("queue",DreamseekersMod.DREAMSEEKERS,5000);
        var anchor=(ProductionPlacement)f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,owner.ownerName,38,4);
        var court=(ProductionPlacement)f.install(DreamseekersMod.BEETLE_SANCTUARY,owner.ownerName,4,42);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;
        h.assertTrue(DreamseekersMod.DREAM_HALL.productions.get().equals(java.util.List.of(recipe,DreamseekersMod.WIND_KNIGHT_PRODUCTION,DreamseekersMod.STONE_SPIRIT_THROWER_PRODUCTION,DreamseekersMod.BEETLE_MOTHER_PRODUCTION,DreamseekersMod.SHADOW_GHOST_PRODUCTION,DreamseekersMod.SHADOW_SQUEAL_GHOST_PRODUCTION,DreamseekersMod.TERRORBEAK_PRODUCTION,DreamseekersMod.FRIENDLY_GHOST_PRODUCTION,DreamseekersMod.SHADOW_HAND_PRODUCTION,DreamseekersMod.ASH_BONE_WING_PRODUCTION,DreamseekersMod.SHADOW_GOLEM_PRODUCTION,DreamseekersMod.WEEPING_SQUEAL_GHOST_PRODUCTION,DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION,DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION)),"API49 third-page left/right appended; Hall retains BoneWing first and the explicit WindKnight second and public thrower third");
        h.assertTrue(DreamseekersMod.BEETLE_SANCTUARY.productions.get().size()==6&&DreamseekersMod.SCAFFOLD_BUILDINGS.size()==6&&DreamseekersMod.FUNCTIONAL_BUILDINGS.size()==8,"court six unit slots untouched; laboratory is a separate paid research producer and eight extension buildings remain registered");
        h.assertTrue(!anchor.startProductionItem(recipe)&&!court.startProductionItem(recipe)&&!hall.startProductionItem(DreamseekersMod.JELLYFISH_PRODUCTION),"cross-producer recipe forgery rejected");
        h.assertTrue(hall.request(actor(f.level,owner.ownerName),new com.dreamseekers.function.DreamFunctionRequest.Production(DreamseekersMod.id("bone_wing_unit"))).status()==com.dreamseekers.function.DreamFunctionResult.Status.UNAVAILABLE,"generic Production service remains disabled despite exact paid whitelist");
        h.assertTrue(hall.startProductionItem(recipe),"real owned completed Hall starts real paid queue");
        f.balance(owner,5000-recipe.defaultCost.food,5000,5000-recipe.defaultCost.ore,"one exact provisional cost");
        h.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==recipe.defaultCost.population,"queue reserves population");
        h.runAtTickTime(630,()->{
            h.assertTrue(hall.productionQueue.isEmpty()&&ownedBoneWing(owner.ownerName)==1,"full unaccelerated train timer produced exactly one native light BoneWing");
            h.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==recipe.defaultCost.population,"paid reservation converts to one unit");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==1,"score only once after actual spawn");
            f.balance(owner,5000-recipe.defaultCost.food,5000,5000-recipe.defaultCost.ore,"completion no second charge");
            System.out.println("DREAMSEEKERS_BONE_WING_FULL_PAID_HALL_QUEUE_PASS");h.succeed();
        });
    }

    @GameTest(template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=280)
    public static void blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper helper) {
        Fixture f=new Fixture(helper);
        Resources owner=f.account("blockedexit",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,4,4);
        var hall=(ProductionPlacement)f.install(DreamseekersMod.DREAM_HALL,owner.ownerName,38,4);
        f.install(DreamseekersMod.CONTRACT_ECOLOGY,owner.ownerName,4,42);
        var bone_wing=DreamseekersMod.BONE_WING_PRODUCTION;
        BlockPos point=hall.getUnitSpawnPoint(f.level,false,net.minecraft.core.Vec3i.ZERO);
        helper.assertTrue(point.equals(hall.getDefaultOutdoorSpawnPoint()),"shared candidate extractor preserves old default spawn point");
        for(BlockPos side:java.util.List.of(point.east(),point.west(),point.north(),point.south()))
            f.level.setBlock(side.above(),Blocks.STONE.defaultBlockState(),2); // Basin keeps the later fluid fixture local.
        f.level.setBlock(point.above(),Blocks.STONE.defaultBlockState(),2);
        helper.assertTrue(hall.startProductionItem(bone_wing),"blocked exit can reserve a paid queue, but cannot produce into a wall");
        hall.productionQueue.get(0).ticksLeft=20; // Isolate completion-window gating; full real timer is a separate test.
        float[] paused={-1};
        helper.runAtTickTime(80,()->{
            helper.assertTrue(hall.productionQueue.size()==1 && ownedBoneWing(owner.ownerName)==0,"solid blocked exit retains paid queue without unsafe entity");
            paused[0]=hall.productionQueue.get(0).ticksLeft;
            helper.assertTrue(paused[0]<=10 && paused[0]>=0,"preflight checks only final completion window");
            helper.assertTrue(PlayerServerEvents.getRTSPlayer(owner.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==0,"blocked completion awards no phantom production score");
            f.level.setBlock(point.above(),Blocks.WATER.defaultBlockState(),2);
        });
        helper.runAtTickTime(120,()->{
            helper.assertTrue(hall.productionQueue.size()==1 && hall.productionQueue.get(0).ticksLeft==paused[0]
                    && ownedBoneWing(owner.ownerName)==0,"flooded exit remains paused without consuming or restarting queue");
            f.level.setBlock(point.above(),Blocks.AIR.defaultBlockState(),2);
            for(BlockPos side:java.util.List.of(point.east(),point.west(),point.north(),point.south()))
                f.level.setBlock(side.above(),Blocks.AIR.defaultBlockState(),2);
        });
        helper.runAtTickTime(200,()->{
            helper.assertTrue(hall.productionQueue.isEmpty() && ownedBoneWing(owner.ownerName)==1,"opening exit resumes original queue and spawns once");
            helper.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e -> e instanceof BoneWingUnit unit && owner.ownerName.equals(unit.getOwnerName()))
                    .allMatch(f.level::noCollision),"actual spawned bone_wing has non-clipping native collision body");
            f.balance(owner,5000-bone_wing.defaultCost.food,5000-bone_wing.defaultCost.wood,5000-bone_wing.defaultCost.ore,"blocked/flooded retries never charge twice");
            helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==bone_wing.defaultCost.population,"paused queue replaced by exactly one unit population");
            System.out.println("DREAMSEEKERS_BONE_WING_PRODUCTION_PASS blocked_flooded_exit_pause_resume");
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
        var bone_wing=DreamseekersMod.BONE_WING_PRODUCTION;
        int accepted=0;
        while (hall.startProductionItem(bone_wing) && ++accepted<30) { }
        helper.assertTrue(accepted>0 && accepted<30,"real population supply rejects excess queued bone_wings");
        helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==accepted*bone_wing.defaultCost.population,"all accepted queues counted once");
        f.balance(owner,5000-accepted*bone_wing.defaultCost.food,5000-accepted*bone_wing.defaultCost.wood,5000-accepted*bone_wing.defaultCost.ore,"failed excess queue never charges");
        while(!hall.productionQueue.isEmpty()) helper.assertTrue(hall.cancelProductionItem(bone_wing,true),"queued cancellation succeeds");
        helper.assertTrue(!hall.cancelProductionItem(bone_wing,true) && !hall.cancelProductionItem(bone_wing,false),"repeat empty cancellation is a no-op");
        f.balance(owner,5000,5000,5000,"all real cancellations refund exactly once");
        helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==0,"all queued population released");
        var wildType=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("bone_wing"));
        helper.assertTrue(wildType!=null,"original bone_wing registry remains available");
        var wild=wildType.create(f.level);
        helper.assertTrue(wild instanceof net.pasterdream.entity.BoneWingEntity && !(wild instanceof Unit),"wild native bone_wing is not a free RTS unit");
        wild.moveTo(hall.getDefaultOutdoorSpawnPoint().getX()+.5,hall.getDefaultOutdoorSpawnPoint().getY()+1,hall.getDefaultOutdoorSpawnPoint().getZ()+.5);
        helper.assertTrue(f.level.addFreshEntity(wild),"native wild control spawns independently");
        helper.assertTrue(UnitServerEvents.getCurrentPopulation(owner.ownerName)==0 && ownedBoneWing(owner.ownerName)==0,"native wild control does not enter paid unit ledger");
        f.balance(owner,5000,5000,5000,"native wild control cannot spend or grant RTS resources");
        wild.discard();
        owner.food=0;
        helper.assertTrue(!hall.startProductionItem(bone_wing),"insufficient food rejected");
        f.balance(owner,0,5000,5000,"failed payment cannot become negative or spawn");
        helper.assertTrue(ownedBoneWing(owner.ownerName)==0,"failed/replayed cancellations spawn no bone_wing");
        System.out.println("DREAMSEEKERS_BONE_WING_PRODUCTION_PASS population_cost_cancel");
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
        String id=ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(DreamseekersMod.BONE_WING_PRODUCTION).toString();
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
        System.out.println("DREAMSEEKERS_BONE_WING_PRODUCTION_PASS authenticated_handler_ownership_dimension_alliance");
        helper.succeed();
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources a=f.account("receiptA",DreamseekersMod.DREAMSEEKERS,5000),b=f.account("receiptB",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,4);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;var cost=recipe.defaultCost;
        int food=cost.food,ore=cost.ore,emerald=cost.emerald;
        a.emerald=100;b.emerald=100;
        try {
            cost.emerald=7;
            h.assertTrue(hall.startProductionItem(recipe)&&hall.startProductionItem(recipe),"two real paid receipts include copied emerald cost");
            var old=hall.productionQueue.get(0);
            h.assertTrue(a.emerald==86,"real core start deducted actual emerald twice");
            cost.food+=77;cost.ore+=31;cost.emerald=33;
            h.assertTrue(hall.cancelProductionItem(recipe,false),"nonstarted back cancellation uses original ticks/cost");
            f.balance(a,5000-food,5000,5000-ore,"back refund exactly one original receipt");h.assertTrue(a.emerald==93,"back emerald exact");
            hall.ownerName=b.ownerName;
            h.assertTrue(hall.cancelProductionItem(recipe,true),"owner changes before any tick cannot redirect refund");
            f.balance(a,5000,5000,5000,"original payer refunded remaining receipt");f.balance(b,5000,5000,5000,"new owner receives zero old payment");
            h.assertTrue(a.emerald==100&&b.emerald==100,"emerald refund belongs only to original payer");
            old.complete(hall);old.complete(hall);
            h.assertTrue(hall.productionQueue.isEmpty()&&!hall.cancelProductionItem(recipe,true)&&ownedBoneWing(a.ownerName)==0&&ownedBoneWing(b.ownerName)==0,"retired active references and repeat cancels cannot mint units/refunds");
        } finally {cost.food=food;cost.ore=ore;cost.emerald=emerald;}
        h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources a=f.account("destroyA",DreamseekersMod.DREAMSEEKERS,5000),b=f.account("destroyB",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,4);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;
        h.assertTrue(hall.startProductionItem(recipe)&&hall.startProductionItem(recipe),"two real paid destruction receipts");
        hall.ownerName=b.ownerName;BuildingServerEvents.getBuildings().remove(hall);
        hall.destroy(f.level);hall.destroy(f.level);hall.onRemoved(BuildingPlacement.RemovalReason.SANDBOX);
        f.balance(a,5000,5000,5000,"destroy after live-list removal refunds original payer once, no base drain loop");f.balance(b,5000,5000,5000,"destroy new owner zero windfall");
        var detached=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,42);
        h.assertTrue(detached.startProductionItem(recipe),"real paid item before lifecycle detach");
        int food=a.food,ore=a.ore;var state=detached.stateSnapshot();var old=detached.productionQueue.get(0);
        BuildingServerEvents.getBuildings().remove(detached);detached.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
        h.assertTrue(detached.productionQueue.isEmpty()&&!detached.startProductionItem(recipe)&&!detached.cancelProductionItem(recipe,true),"detached instance cannot use stale start/cancel or reserve population");
        detached.tick(f.level);detached.onBuilt();detached.destroy(f.level);old.complete(detached);
        f.balance(a,food,5000,ore,"onRemoved and all stale callbacks never write accounting or refund");
        h.assertTrue(detached.stateSnapshot().equals(state)&&ownedBoneWing(a.ownerName)==0,"detach leaves shared saved metadata untouched and cannot mint a unit");h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources a=f.account("epochA",DreamseekersMod.DREAMSEEKERS,5000),b=f.account("epochB",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,4);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;
        h.assertTrue(hall.startProductionItem(recipe),"initial paid generation");var old=hall.productionQueue.get(0);
        hall.ownerName=b.ownerName;hall.getDataStorage();hall.ownerName=a.ownerName;hall.tick(f.level);
        System.out.println("DREAM_HALL_LIFECYCLE_DIAGNOSTIC ABA queue="+hall.productionQueue.size()
                +" snapshot="+hall.stateSnapshot()+" currentOwner="+hall.ownerName+" capture="+hall.getCaptureRevision()
                +" originalCompleted="+old.completed+" payerReceiptIds="+a.paidReceipts.keySet()
                +" payerFood="+a.food+" payerOre="+a.ore+" otherFood="+b.food+" otherOre="+b.ore);
        h.assertTrue(hall.productionQueue.isEmpty()&&hall.stateSnapshot().ownerRevision()==2,"observed ABA invalidates old paid generation despite same final owner");
        f.balance(a,5000,5000,5000,"old generation paid receipt returned exactly once");
        h.assertTrue(hall.startProductionItem(recipe),"new generation can pay normally");var cleared=hall.productionQueue.get(0);
        hall.productionQueue.clear();hall.tick(f.level);old.complete(hall);cleared.complete(hall);
        h.assertTrue(!hall.cancelProductionItem(recipe,true)&&ownedBoneWing(a.ownerName)==0,"core external queue clear retires receipt, old references cannot complete/refund");
        f.balance(a,5000-recipe.defaultCost.food,5000,5000-recipe.defaultCost.ore,"external core clear itself does not synchronously refund");
        h.runAtTickTime(40,()->{
            f.balance(a,5000,5000,5000,"detached durable receipt eventually refunds original payer once");
            h.assertTrue(a.paidReceipts.isEmpty(),"background ledger settlement removes the detached claim");
            old.complete(hall);cleared.complete(hall);
            h.assertTrue(!hall.cancelProductionItem(recipe,true)&&ownedBoneWing(a.ownerName)==0,"settled old references still cannot spawn or refund");
        });
        h.runAtTickTime(80,()->{
            f.balance(a,5000,5000,5000,"later ledger passes cannot duplicate the detached refund");
            f.balance(b,5000,5000,5000,"other owner receives no detached refund");
            h.assertTrue(a.paidReceipts.isEmpty()&&ownedBoneWing(a.ownerName)==0,"settled receipt remains absent and no stale unit appears");h.succeed();
        });
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources a=f.account("future",DreamseekersMod.DREAMSEEKERS,5000),b=f.account("futureOther",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,4);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;
        h.assertTrue(hall.startProductionItem(recipe),"genuine paid receipt before unsupported schema");var paid=hall.productionQueue.get(0);paid.ticksLeft=0;
        net.minecraft.nbt.CompoundTag supported=hall.getDataStorage().getData(com.dreamseekers.function.DreamFunctionalPlacement.STATE).write();
        net.minecraft.nbt.CompoundTag future=new net.minecraft.nbt.CompoundTag();future.putInt("schemaVersion",99);future.putString("future","keep unchanged");
        hall.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(future));
        paid.complete(hall); // Cannot bypass the authorized item.tick handshake by calling a public old reference.
        h.runAtTickTime(60,()->{
            System.out.println("DREAM_HALL_LIFECYCLE_DIAGNOSTIC FUTURE queue="+hall.productionQueue.size()
                    +" sameHead="+(!hall.productionQueue.isEmpty()&&hall.productionQueue.get(0)==paid)
                    +" snapshot="+hall.stateSnapshot()+" currentOwner="+hall.ownerName+" capture="+hall.getCaptureRevision()
                    +" originalCompleted="+paid.completed+" ticksLeft="+paid.ticksLeft+" payerReceiptIds="+a.paidReceipts.keySet()
                    +" payerFood="+a.food+" payerOre="+a.ore+" units="+ownedBoneWing(a.ownerName)
                    +" producedScore="+PlayerServerEvents.getRTSPlayer(a.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED));
            h.assertTrue(hall.productionQueue.size()==1&&hall.productionQueue.get(0)==paid&&!paid.completed&&paid.ticksLeft==0,"future schema at zero ticks pauses original paid entry");
            h.assertTrue(ownedBoneWing(a.ownerName)==0&&PlayerServerEvents.getRTSPlayer(a.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==0,"no unit/score/phantom completion");
            h.assertTrue(hall.getDataStorage().getData(com.dreamseekers.function.DreamFunctionalPlacement.STATE).write().equals(future),"unknown storage preserved exactly");
            h.assertTrue(!hall.startProductionItem(recipe)&&hall.cancelProductionItem(recipe,true),"unsupported state prevents new payment but allows genuine old receipt cancellation");
            f.balance(a,5000,5000,5000,"exact refund despite unsupported lifecycle state");
            h.assertTrue(a.paidReceipts.isEmpty(),"opaque cancellation removes its genuine claim");
            hall.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(supported));
            h.assertTrue(hall.startProductionItem(recipe),"restored supported metadata permits a fresh payment");
            var opaqueABA=hall.productionQueue.get(0);
            hall.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(future));
            hall.ownerName=b.ownerName;hall.getDataStorage();hall.ownerName=a.ownerName;hall.getDataStorage();
            hall.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(supported));
            hall.tick(f.level);opaqueABA.complete(hall);
            h.assertTrue(hall.productionQueue.isEmpty()&&a.paidReceipts.isEmpty()&&!opaqueABA.completed,
                    "observed opaque owner ABA cannot be erased by restoring the old supported raw state");
            f.balance(a,5000,5000,5000,"opaque ABA refunds original payer exactly once");
            h.assertTrue(hall.startProductionItem(recipe),"new runtime generation remains usable after ABA settlement");
            var requestObserved=hall.productionQueue.get(0);
            hall.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(future));
            hall.ownerName=b.ownerName;
            h.assertTrue(hall.request(actor(f.level,b.ownerName),new com.dreamseekers.function.DreamFunctionRequest.Production(DreamseekersMod.id("bone_wing_unit"))).status()
                    ==com.dreamseekers.function.DreamFunctionResult.Status.UNSUPPORTED_SCHEMA,
                    "authenticated new owner's request observes but cannot execute opaque functionality");
            hall.ownerName=a.ownerName;
            hall.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(supported));
            hall.tick(f.level);requestObserved.complete(hall);
            h.assertTrue(hall.productionQueue.isEmpty()&&a.paidReceipts.isEmpty()&&!requestObserved.completed,
                    "request-observed opaque ABA cannot revive a binding through old raw restoration");
            f.balance(a,5000,5000,5000,"request-observed ABA settles only the original payer");
            h.assertTrue(hall.startProductionItem(recipe),"fresh payment after request-observed generation change");
            var capturedOpaque=hall.productionQueue.get(0);
            hall.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(future));
            hall.setOwnerNameFromServerEditor(b.ownerName);hall.setOwnerNameFromServerEditor(a.ownerName);
            hall.tick(f.level);capturedOpaque.complete(hall);
            h.assertTrue(hall.productionQueue.isEmpty()&&a.paidReceipts.isEmpty()&&!capturedOpaque.completed,
                    "supported editor capture revision also invalidates an opaque paid binding");
            h.assertTrue(hall.getDataStorage().getData(com.dreamseekers.function.DreamFunctionalPlacement.STATE).write().equals(future),
                    "capture settlement does not rewrite opaque raw metadata");
        });
        h.runAtTickTime(100,()->{
            f.balance(a,5000,5000,5000,"opaque cancellation and invalidated claims never duplicate refunds later");
            f.balance(b,5000,5000,5000,"opaque owner transitions never credit the other account");
            h.assertTrue(a.paidReceipts.isEmpty()&&hall.productionQueue.isEmpty()&&ownedBoneWing(a.ownerName)==0
                    &&PlayerServerEvents.getRTSPlayer(a.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==0,
                    "later ticks preserve no stale completion, unit, score, or ledger claim");h.succeed();
        });
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=220)
    public static void actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h) {
        joinCancellation(h,net.minecraftforge.eventbus.api.EventPriority.HIGHEST);
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=220)
    public static void lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h) {
        joinCancellation(h,net.minecraftforge.eventbus.api.EventPriority.LOWEST);
    }

    private static void joinCancellation(GameTestHelper h,net.minecraftforge.eventbus.api.EventPriority priority) {
        Fixture f=new Fixture(h);Resources a=f.account("joinCancel"+priority.name(),DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,4);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;BlockPos exit=hall.getUnitSpawnPoint(f.level,false,net.minecraft.core.Vec3i.ZERO);
        boolean[] block={true};int[] rejected={0};java.util.List<net.minecraft.world.entity.Entity> refused=new java.util.ArrayList<>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> guard=event->{
            if(block[0]&&event.getLevel()==f.level&&event.getEntity() instanceof BoneWingUnit
                    &&event.getEntity().position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(exit))<16) {
                rejected[0]++;refused.add(event.getEntity());event.setCanceled(true);
            }
        };
        SPAWN_GUARDS.add(guard);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(priority,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,guard);
        h.assertTrue(hall.startProductionItem(recipe),"real paid queue before controlled Forge spawn rejection");var paid=hall.productionQueue.get(0);paid.ticksLeft=1;
        h.runAtTickTime(40,()->{
            System.out.println("BONE_HALL_SPAWN_REJECT_TRACE priority="+priority+" rejected="+rejected[0]+" queue="+hall.productionQueue.size()+" score="+PlayerServerEvents.getRTSPlayer(a.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED));
            h.assertTrue(rejected[0]>0,"real EntityType.spawn reached cancellable Forge join event");
            h.assertTrue(hall.productionQueue.size()==1&&hall.productionQueue.get(0)==paid&&!paid.completed&&ownedBoneWing(a.ownerName)==0,"rejected actual spawn retains same paid item without phantom unit");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(a.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==0,"rejected actual spawn gives no score or completion");
            h.assertTrue(refused.stream().noneMatch(UnitServerEvents.getAllUnits()::contains),"cancelled entities leave no RoN population roster entries");
            try {
                var field=UnitServerEvents.class.getDeclaredField("forcedUnitChunks");field.setAccessible(true);
                java.util.Map<?,?> tickets=(java.util.Map<?,?>)field.get(null);
                h.assertTrue(refused.stream().noneMatch(e->tickets.containsKey(e.getId())),"cancelled entities leave no RoN force-ticket bookkeeping");
            } catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            block[0]=false;
        });
        h.runAtTickTime(100,()->{
            h.assertTrue(hall.productionQueue.isEmpty()&&ownedBoneWing(a.ownerName)==1,"same original paid item succeeds once after Forge spawn permission returns");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(a.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==1,"only actual accepted spawn earns completion score");
            f.balance(a,5000-recipe.defaultCost.food,5000,5000-recipe.defaultCost.ore,"spawn retries never charge again");h.succeed();
        });
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources a=f.account("savedA",DreamseekersMod.DREAMSEEKERS,5000),b=f.account("savedB",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,4);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;h.assertTrue(hall.startProductionItem(recipe),"genuine paid item before save boundary");
        hall.ownerName=b.ownerName;int food=recipe.defaultCost.food;
        try {
            recipe.defaultCost.food=food+37;
            ResourcesServerEvents.saveResources(f.level);
            var saved=com.solegendary.reignofnether.resources.ResourcesSaveData.getInstance(f.level).resources;
            Resources sa=saved.stream().filter(r->r.ownerName.equals(a.ownerName)).findFirst().orElseThrow();
            Resources sb=saved.stream().filter(r->r.ownerName.equals(b.ownerName)).findFirst().orElseThrow();
            System.out.println("BONE_HALL_SAVED_REFUND_TRACE original="+sa.food+" current="+sb.food);
            h.assertTrue(sa.food==5000&&sa.ore==5000&&sb.food==5000&&sb.ore==5000,"real save snapshot refunds immutable payment to original payer before any normal cancellation");
            f.balance(a,5000-food,5000,5000-recipe.defaultCost.ore,"snapshot does not mutate live payer");
            h.assertTrue(hall.productionQueue.size()==1,"snapshot neither completes nor drains paid queue");
        } finally {recipe.defaultCost.food=food;}
        h.assertTrue(hall.cancelProductionItem(recipe,true),"normal cancellation still settles once after snapshot");f.balance(a,5000,5000,5000,"real refund once");f.balance(b,5000,5000,5000,"transferee no windfall");
        ResourcesServerEvents.saveResources(f.level);h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h) {
        Fixture f=new Fixture(h);Resources a=f.account("reentry",DreamseekersMod.DREAMSEEKERS,5000);
        f.install(DreamseekersMod.DREAM_ANCHOR,a.ownerName,4,4);
        var hall=(com.dreamseekers.production.DreamHallPlacement)f.install(DreamseekersMod.DREAM_HALL,a.ownerName,38,4);
        var recipe=DreamseekersMod.BONE_WING_PRODUCTION;BlockPos exit=hall.getUnitSpawnPoint(f.level,false,net.minecraft.core.Vec3i.ZERO);
        h.assertTrue(hall.startProductionItem(recipe),"one genuine paid entry before callback reentry");var paid=hall.productionQueue.get(0);paid.ticksLeft=1;
        int[] joins={0};boolean[] attempted={false},extraStart={false};
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> guard=event->{
            if(event.getLevel()==f.level&&event.getEntity() instanceof BoneWingUnit&&event.getEntity().position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(exit))<16) {
                joins[0]++;
                if(!attempted[0]) {attempted[0]=true;paid.complete(hall);extraStart[0]=hall.startProductionItem(recipe);}
            }
        };
        SPAWN_GUARDS.add(guard);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,guard);
        h.runAtTickTime(50,()->{
            System.out.println("BONE_HALL_REENTRY_TRACE joins="+joins[0]+" extraStart="+extraStart[0]+" queue="+hall.productionQueue.size());
            h.assertTrue(attempted[0]&&joins[0]==1&&!extraStart[0],"synchronous Join callback cannot recurse completion or mutate cached production head through new start");
            h.assertTrue(hall.productionQueue.isEmpty()&&ownedBoneWing(a.ownerName)==1&&PlayerServerEvents.getRTSPlayer(a.ownerName).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==1,"one original payment gives exactly one completion");
            f.balance(a,5000-recipe.defaultCost.food,5000,5000-recipe.defaultCost.ore,"no callback second payment or phantom refund");h.succeed();
        });
    }

    private static long ownedBoneWing(String owner) {
        return UnitServerEvents.getAllUnits().stream().filter(e -> e instanceof BoneWingUnit unit && owner.equals(unit.getOwnerName())).count();
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
}
