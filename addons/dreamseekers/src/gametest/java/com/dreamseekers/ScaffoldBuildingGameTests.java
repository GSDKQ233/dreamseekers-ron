// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamScaffoldBuilding;
import com.dreamseekers.unit.FireflyWorker;
import com.dreamseekers.function.*;
import com.solegendary.reignofnether.building.data.DataStorage;
import static com.dreamseekers.function.DreamFunctionService.Lifecycle.*;
import static com.dreamseekers.function.DreamFunctionResult.Status.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Real Forge tests. Source presence is not evidence of a passing execution. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScaffoldBuildingGameTests {
    private static final String PREFIX = "DreamScaffoldGT_";
    private static final String BATCH = "dreamseekers_scaffold_buildings";
    private static final String TEMPLATE = "dreamseekers_test_arena";
    private static final List<String> IDS = List.of("dream_depot", "shadow_mine", "shadow_furnace", "dream_hall",
            "resonance_laboratory", "contract_ecology", "resonance_dais", "shadow_watchtower");

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (var entity : new ArrayList<>(UnitServerEvents.getAllUnits()))
            if (entity instanceof Unit unit && unit.getOwnerName().startsWith(PREFIX)) entity.discard();
        // Use the real disposal hook, not a removeIf which leaves addon attachments alive.
        for (BuildingPlacement placement : new ArrayList<>(BuildingServerEvents.getBuildings())) {
            if (!placement.ownerName.startsWith(PREFIX)) continue;
            BuildingServerEvents.getBuildings().remove(placement);
            placement.destroy(level);
        }
        for (ServerPlayer player : new ArrayList<>(level.players()))
            if (player.getGameProfile().getName().startsWith(PREFIX)) level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        for (ServerPlayer player : new ArrayList<>(level.getServer().getPlayerList().getPlayers()))
            if (player.getGameProfile().getName().startsWith(PREFIX)) setFakeServerMembership(player,false);
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        PlayerServerEvents.players.removeIf(p -> p.getGameProfile().getName().startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void allEightRegisteredFactionFactoriesPaidRotationsAndCancel(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Resources account = f.account("rotation", DreamseekersMod.DREAMSEEKERS, 10000);
        Resources wrong = f.account("wrong", Factions.VILLAGERS, 10000);
        Resources poor = f.account("poor", DreamseekersMod.DREAMSEEKERS, 0);
        BlockPos site = f.pos(60, 1, 60);
        f.flat(35, 35, 50, 50);
        List<Building> buildings = buildings(helper);
        for (Building building : buildings) {
            int[] before = balance(account);
            helper.assertTrue(place(building, site, Rotation.NONE, account.ownerName) == null,
                    building.structureName + ": no placement without completed own anchor");
            assertBalance(helper, account, before, "prerequisite rejection does not debit");
        }
        f.flat(2, 2, 22, 22);
        BuildingPlacement anchor = f.install(DreamseekersMod.DREAM_ANCHOR, account.ownerName, f.pos(4, 1, 4), Rotation.NONE);
        f.install(DreamseekersMod.DREAM_ANCHOR, poor.ownerName, f.pos(4, 1, 25), Rotation.NONE);
        helper.assertTrue(ReignOfNetherRegistries.BUILDING.keySet().stream().filter(k -> k.getNamespace().equals(DreamseekersMod.MOD_ID)).count()==11,
                "all eleven distinct namespaced building definitions are registered on the server");
        for(Building original:List.of(DreamseekersMod.DREAM_ANCHOR,DreamseekersMod.REVERIE_GARDEN,DreamseekersMod.BEETLE_SANCTUARY))
            helper.assertTrue(original.getFaction()==DreamseekersMod.DREAMSEEKERS,"existing building faction mapping preserved");
        for (Building building : buildings) {
            // Factions adds GUI buttons only on Dist.CLIENT. Server tests assert the
            // actual faction link; source assertions cover registration call sites.
            helper.assertTrue(building.getFaction()==DreamseekersMod.DREAMSEEKERS,"registered faction mapping: "+building.structureName);
            helper.assertTrue((building instanceof DreamScaffoldBuilding || building==DreamseekersMod.DREAM_HALL
                            || building==DreamseekersMod.RESONANCE_LABORATORY) && building.requiresStrictPlacementOwnerCheck(),
                    "only explicit scaffold, Hall and laboratory classes retain strict sender policy");
            helper.assertTrue(building.canAcceptResources == (building==DreamseekersMod.DREAM_DEPOT)
                            && !building.isCapitol && building.cost.population == 0,
                    "only approved depot accepts dropoff; no original eight building grants capitol or population");
            if (building==DreamseekersMod.DREAM_HALL) {
                helper.assertTrue(((ProductionBuilding)building).productions.get().equals(List.of(
                        DreamseekersMod.BONE_WING_PRODUCTION,DreamseekersMod.WIND_KNIGHT_PRODUCTION,
                        DreamseekersMod.STONE_SPIRIT_THROWER_PRODUCTION,DreamseekersMod.BEETLE_MOTHER_PRODUCTION,
                        DreamseekersMod.SHADOW_GHOST_PRODUCTION,DreamseekersMod.SHADOW_SQUEAL_GHOST_PRODUCTION,
                        DreamseekersMod.TERRORBEAK_PRODUCTION,DreamseekersMod.FRIENDLY_GHOST_PRODUCTION,
                        DreamseekersMod.SHADOW_HAND_PRODUCTION,DreamseekersMod.ASH_BONE_WING_PRODUCTION,
                        DreamseekersMod.SHADOW_GOLEM_PRODUCTION,DreamseekersMod.WEEPING_SQUEAL_GHOST_PRODUCTION,
                        DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION,DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION)),
                        "Hall preserves original twelve recipes plus exactly two approved hand recipes in order");
            } else if (building==DreamseekersMod.RESONANCE_LABORATORY) {
                var expectedResearch = new ArrayList<com.solegendary.reignofnether.building.production.ProductionItem>();
                for (var family : com.dreamseekers.progression.DreamWindResearch.TIERS)
                    expectedResearch.addAll(Arrays.asList(family));
                expectedResearch.addAll(List.of(com.dreamseekers.unitprogression.DreamSpecialistResearch.PRECISION_TOOLS,
                        com.dreamseekers.unitprogression.DreamSpecialistResearch.SHELL_WEAVING,
                        com.dreamseekers.unitprogression.DreamSpecialistResearch.CONTRACT_CARE,
                        com.dreamseekers.unitprogression.DreamSpecialistResearch.STONE_SIEGE));
                expectedResearch.add(com.dreamseekers.resonance.ResonanceResearch.ITEM);
                expectedResearch.add(com.dreamseekers.neutralcontract.ContractResearch.CAPACITY);
                expectedResearch.add(com.dreamseekers.resonance.LightResonanceResearch.FIRST);
                expectedResearch.add(com.dreamseekers.resonance.LightResonanceResearch.FINAL);
                expectedResearch.addAll(com.dreamseekers.unitprogression.AdvancedUnitResearch.items());
                helper.assertTrue(expectedResearch.size()==28
                                && ((ProductionBuilding)building).productions.get().equals(expectedResearch),
                        "laboratory preserves the exact original twenty-one identities in order, then appends both luminous research tiers");
                helper.assertTrue(ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(expectedResearch.get(21))
                                .equals(com.dreamseekers.resonance.LightResonanceResearch.FIRST_ID)
                                && ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(expectedResearch.get(22))
                                .equals(com.dreamseekers.resonance.LightResonanceResearch.FINAL_ID),
                        "the two appended luminous tiers retain their exact registered IDs");
            } else helper.assertTrue(!(building instanceof ProductionBuilding),
                    "other six original structures never gain general production/research queues");
            helper.assertTrue(building.getAbilities().isEmpty(),
                    "original eight expose no generic core ability list; approved roles use their explicit service/addon routes");
            helper.assertTrue(building.cost.food >= 0 && building.cost.wood > 0 && building.cost.ore >= 0,
                    "central experimental construction scaffold is not a free resource source");
            helper.assertTrue(place(building, site, Rotation.NONE, wrong.ownerName) == null, "wrong faction rejected");
            assertBalance(helper, wrong, new int[]{10000,10000,10000,0}, "wrong faction not charged");
            helper.assertTrue(place(building, site, Rotation.NONE, poor.ownerName) == null, "poor owner rejected");
            assertBalance(helper, poor, new int[]{0,0,0,0}, "insufficient balance not charged");
            anchor.isBuilt = false;
            helper.assertTrue(place(building, site, Rotation.NONE, account.ownerName) == null, "unfinished anchor insufficient");
            anchor.isBuilt = true;
            for (Rotation rotation : Rotation.values()) {
                int[] before = balance(account);
                BuildingPlacement candidate = BuildingUtils.getNewBuildingPlacement(building, f.level, site, rotation, account.ownerName, false);
                helper.assertTrue(candidate != null && candidate.getBuilding() == building && candidate.rotation == rotation,
                        "registry factory preserves identity and rotation");
                var expected = BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(f.level), f.level, site, rotation);
                helper.assertTrue(candidate.getBlocks().size() == expected.size(), "factory keeps complete preview/template volume");
                for (int i = 0; i < expected.size(); i++)
                    helper.assertTrue(candidate.getBlocks().get(i).getBlockPos().equals(expected.get(i).getBlockPos())
                            && candidate.getBlocks().get(i).getBlockState().equals(expected.get(i).getBlockState()), "factory keeps rotated registry state");
                // This verifies the wire codec. Direct place below is NOT described as a packet-handler test.
                BuildingServerboundPacket packet = new BuildingServerboundPacket(BuildingAction.PLACE,
                        ReignOfNetherRegistries.BUILDING.getKey(building).toString(), site, BlockPos.ZERO,
                        rotation, account.ownerName, new int[0], false);
                FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    packet.encode(bytes);
                    BuildingServerboundPacket decoded = new BuildingServerboundPacket(bytes);
                    helper.assertTrue(decoded.action == BuildingAction.PLACE && decoded.itemName.equals(packet.itemName)
                            && decoded.ownerName.equals(account.ownerName) && decoded.rotation == rotation
                            && decoded.buildingPos.equals(site), "namespaced building selection round-trips in wire codec");
                } finally { bytes.release(); }
                BuildingPlacement paid = place(building, site, rotation, account.ownerName);
                helper.assertTrue(paid != null && !paid.isBuilt && !paid.selfBuilding && paid.ownerName.equals(account.ownerName),
                        "normal paid placement succeeds in " + rotation + ": " + building.structureName);
                assertBalance(helper, account, new int[]{before[0]-building.cost.food, before[1]-building.cost.wood,
                        before[2]-building.cost.ore, before[3]}, "payer debited exactly once");
                int[] paidBalance = balance(account);
                helper.assertTrue(place(building, site, rotation, account.ownerName) == null, "duplicate rejected");
                assertBalance(helper, account, paidBalance, "duplicate never debits again");
                float fraction = paid.getBlocksPlacedPercent();
                BuildingServerEvents.cancelBuilding(paid, account.ownerName);
                helper.assertTrue(!BuildingServerEvents.getBuildings().contains(paid), "cancellation removes live placement");
                assertBalance(helper, account, new int[]{paidBalance[0] + Math.round(building.cost.food*(1-fraction)),
                        paidBalance[1] + Math.round(building.cost.wood*(1-fraction)),
                        paidBalance[2] + Math.round(building.cost.ore*(1-fraction)), paidBalance[3]}, "existing cancellation fraction refunds exactly once");
            }
        }
        ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_PASS registry_faction_paid_rotations_cancel buildings=8 rotations=32 (wire codec, not packet handler)");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 160)
    public static void actualPacketHandlerRejectsForeignBudgetWorkersAndCancellation(GameTestHelper helper) throws Exception {
        Fixture f=new Fixture(helper);
        Resources owner=f.account("packetowner",DreamseekersMod.DREAMSEEKERS,10000);
        Resources attacker=f.account("packetenemy",DreamseekersMod.DREAMSEEKERS,10000);
        ServerPlayer ownerActor=f.actor(owner.ownerName), attackerActor=f.actor(attacker.ownerName);
        f.flat(1,1,100,100);
        f.install(DreamseekersMod.DREAM_ANCHOR,owner.ownerName,f.pos(4,1,4),Rotation.NONE);
        BlockPos site=f.pos(50,1,50);
        BlockPos workerPos=f.pos(30,2,30);
        FireflyWorker foreignWorker=f.worker(attacker.ownerName,workerPos.getX(),workerPos.getY(),workerPos.getZ());
        foreignWorker.setNoAi(true);
        // Context uses the real Forge handler and authenticated fake player's listener.
        // No socket, client, login, network transport or GUI is claimed by this test.
        NetworkEvent.Context ownerContext=context(ownerActor), attackerContext=context(attackerActor);
        helper.assertTrue(ownerContext.getSender()==ownerActor && attackerContext.getSender()==attackerActor,
                "actual Forge Context resolves the intended server-side sender");
        // Register a real local fake-player fixture for RoN's normal visibility
        // policy; a bot name alone has no ServerLevel player view. The test world
        // may have fog disabled: this does not prove enabled-fog raster coverage.
        // No login/socket, fabricated vision masks, or global fog toggles.
        ownerActor.moveTo(site.getX()+0.5,site.getY()+1,site.getZ()+0.5,0,0);
        f.level.addNewPlayer(ownerActor);
        setFakeServerMembership(ownerActor,true);
        PlayerServerEvents.players.add(ownerActor);
        FireflyWorker viewer=f.worker(owner.ownerName,site.getX(),site.getY()+1,site.getZ());
        viewer.setNoAi(true);
        helper.runAtTickTime(20,()->{
        String sandboxName=PREFIX+"unrelatedsandbox";
        RTSPlayer unrelatedSandbox=RTSPlayer.getNewBot(sandboxName,Factions.NONE);
        PlayerServerEvents.rtsPlayers.add(unrelatedSandbox);
        try {
            for(Building building:buildings(helper)) {
                var preview=BuildingUtils.getNewBuildingPlacement(building,f.level,site,Rotation.NONE,owner.ownerName,false);
                helper.assertTrue(BuildingValidators.isInBrightChunk(f.level,preview.centrePos,owner.ownerName),
                        "legitimate packet site satisfies normal visibility policy (fog may be disabled by test-world settings)");
                int[] before=balance(owner), attackerBefore=balance(attacker);
                String id=ReignOfNetherRegistries.BUILDING.getKey(building).toString();
                boolean resetPolicy=building.shouldDestroyOnReset;
                BuildingPlacement blocking=place(building,site,Rotation.NONE,owner.ownerName);
                helper.assertTrue(blocking!=null && !blocking.isBuilt,"ordinary pending placement provides empty-volume overlap fixture");
                int[] overlapBalance=balance(owner);
                packet(BuildingAction.PLACE,id,site.offset(1,0,0),owner.ownerName,new int[0]).handle(()->ownerContext);
                helper.assertTrue(BuildingServerEvents.getBuildings().stream().noneMatch(p->p.originPos.equals(site.offset(1,0,0))),
                        "unrelated sandbox does not bypass overlapping pending-building validation");
                assertBalance(helper,owner,overlapBalance,"rejected overlap does not charge twice");
                packet(BuildingAction.DESTROY,"",site,owner.ownerName,new int[0]).handle(()->ownerContext);
                assertBalance(helper,owner,before,"overlap fixture cancellation restores exact payer balance");
                packet(BuildingAction.PLACE,id,site,owner.ownerName,new int[0]).handle(()->attackerContext);
                helper.assertTrue(BuildingServerEvents.getBuildings().stream().noneMatch(p->p.originPos.equals(site)),
                        "unrelated sandbox player does not authorize foreign-budget packet placement");
                assertBalance(helper,owner,before,"forged place cannot debit victim");
                packet(BuildingAction.PLACE,id,site,owner.ownerName,new int[]{foreignWorker.getId()}).handle(()->ownerContext);
                helper.assertTrue(BuildingServerEvents.getBuildings().stream().noneMatch(p->p.originPos.equals(site)),
                        "authenticated payer cannot commandeer enemy worker");
                helper.assertTrue(foreignWorker.getBuildRepairGoal().getBuildingTarget()==null,
                        "rejected worker spoof has no target assignment side effect");
                assertBalance(helper,owner,before,"worker spoof cannot debit payer");
                packet(BuildingAction.PLACE,id,site,owner.ownerName,new int[0]).handle(()->ownerContext);
                BuildingPlacement placed=BuildingServerEvents.getBuildings().stream().filter(p->p.originPos.equals(site)).findFirst().orElse(null);
                helper.assertTrue(placed!=null && placed.getBuilding()==building && placed.ownerName.equals(owner.ownerName),
                        "owner's actual decoded handler path places the registered building");
                int[] afterPay={before[0]-building.cost.food,before[1]-building.cost.wood,before[2]-building.cost.ore,before[3]};
                assertBalance(helper,owner,afterPay,"packet handler charges authenticated owner's account once");
                helper.assertTrue(building.shouldDestroyOnReset==resetPolicy,"unrelated sandbox never mutates the registered definition's reset policy");
                for(String forgedOwner:List.of(attacker.ownerName,owner.ownerName)) {
                    packet(BuildingAction.DESTROY,"",site,forgedOwner,new int[0]).handle(()->attackerContext);
                    helper.assertTrue(BuildingServerEvents.getBuildings().contains(placed),
                            "foreign cancellation is rejected using actual placement owner, regardless of payload owner or unrelated sandbox");
                    assertBalance(helper,owner,afterPay,"rejected cancellation cannot refund or destroy victim building");
                }
                float fraction=placed.getBlocksPlacedPercent();
                packet(BuildingAction.DESTROY,"",site,owner.ownerName,new int[0]).handle(()->ownerContext);
                helper.assertTrue(!BuildingServerEvents.getBuildings().contains(placed),"actual owner packet can cancel unfinished scaffold");
                assertBalance(helper,owner,new int[]{afterPay[0]+Math.round(building.cost.food*(1-fraction)),
                        afterPay[1]+Math.round(building.cost.wood*(1-fraction)),afterPay[2]+Math.round(building.cost.ore*(1-fraction)),afterPay[3]},
                        "legitimate packet cancellation follows existing refund policy");
                assertBalance(helper,attacker,attackerBefore,"all spoof attempts leave attacker budget unchanged");
                // Explicit allied control remains supported; an alliance alone does not grant it.
                AlliancesServerEvents.addAlliance(attacker.ownerName,owner.ownerName);
                try {
                    packet(BuildingAction.PLACE,id,site,owner.ownerName,new int[0]).handle(()->attackerContext);
                    helper.assertTrue(BuildingServerEvents.getBuildings().stream().noneMatch(p->p.originPos.equals(site)),
                            "alliance without owner opt-in cannot spend allied budget");
                    AlliancesServerEvents.playersWithAlliedControl.add(owner.ownerName);
                    int[] allyBefore=balance(owner);
                    packet(BuildingAction.PLACE,id,site,owner.ownerName,new int[0]).handle(()->attackerContext);
                    BuildingPlacement allied=BuildingServerEvents.getBuildings().stream().filter(p->p.originPos.equals(site)).findFirst().orElse(null);
                    helper.assertTrue(allied!=null && allied.ownerName.equals(owner.ownerName),"explicitly authorized ally retains normal placement control");
                    assertBalance(helper,owner,new int[]{allyBefore[0]-building.cost.food,allyBefore[1]-building.cost.wood,allyBefore[2]-building.cost.ore,allyBefore[3]},
                            "allied control spends actual owner budget, never sender budget");
                    packet(BuildingAction.DESTROY,"",site,attacker.ownerName,new int[0]).handle(()->attackerContext);
                    helper.assertTrue(!BuildingServerEvents.getBuildings().contains(allied),"authorized ally cancellation checks actual building owner, not forged payload");
                    assertBalance(helper,owner,allyBefore,"same-tick unbuilt allied cancellation refunds its payer exactly");
                    assertBalance(helper,attacker,attackerBefore,"allied control cannot transfer refund into sender account");
                } finally {
                    AlliancesServerEvents.playersWithAlliedControl.remove(owner.ownerName);
                    AlliancesServerEvents.removeAlliance(attacker.ownerName,owner.ownerName);
                }
            }
        } finally {
            PlayerServerEvents.rtsPlayers.remove(unrelatedSandbox);
            PlayerServerEvents.players.remove(ownerActor);
            setFakeServerMembership(ownerActor,false);
            f.level.removePlayerImmediately(ownerActor,Entity.RemovalReason.DISCARDED);
        }
        ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_PASS actual_packet_sender_budget_worker_cancel buildings=8 (headless handler, normal visibility policy; not enabled-fog raster, network transport or client GUI)");
        helper.succeed();
        });
    }
    private static BuildingServerboundPacket packet(BuildingAction action,String id,BlockPos site,String owner,int[] workers) {
        BuildingServerboundPacket outgoing=new BuildingServerboundPacket(action,id,site,BlockPos.ZERO,Rotation.NONE,owner,workers,false);
        FriendlyByteBuf bytes=new FriendlyByteBuf(Unpooled.buffer());
        try { outgoing.encode(bytes);return new BuildingServerboundPacket(bytes); }
        finally { bytes.release(); }
    }
    /** Test fixture only: Forge exposes an unmodifiable getPlayers() view. Keep
     * real player membership coherent without login, sockets or forged fog masks. */
    @SuppressWarnings("unchecked")
    private static void setFakeServerMembership(ServerPlayer actor,boolean present) {
        try {
            var list=actor.serverLevel().getServer().getPlayerList();
            var playersField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            var byIdField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            playersField.setAccessible(true);byIdField.setAccessible(true);
            List<ServerPlayer> players=(List<ServerPlayer>)playersField.get(list);
            java.util.Map<UUID,ServerPlayer> byId=(java.util.Map<UUID,ServerPlayer>)byIdField.get(list);
            if(present) { if(!players.contains(actor)) players.add(actor);byId.put(actor.getUUID(),actor); }
            else { players.remove(actor);byId.remove(actor.getUUID(),actor); }
        } catch(ReflectiveOperationException error) { throw new RuntimeException("local fake-player test membership",error); }
    }

    private static NetworkEvent.Context context(ServerPlayer actor) throws Exception {
        Connection connection=new Connection(PacketFlow.SERVERBOUND);
        connection.setListener(actor.connection);
        var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,-1);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 7500)
    public static void eightActualWorkersCompleteThenSurviveCodecAndTakeDamage(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Resources account = f.account("construct", DreamseekersMod.DREAMSEEKERS, 10000);
        f.flat(1, 1, 108, 130);
        f.install(DreamseekersMod.DREAM_ANCHOR, account.ownerName, f.pos(4, 1, 4), Rotation.NONE);
        List<BuildingPlacement> placements = new ArrayList<>();
        List<FireflyWorker> workers = new ArrayList<>();
        List<Building> buildings = buildings(helper);
        for (int i=0; i<buildings.size(); i++) {
            int x = 32 + (i % 2) * 44, z = 32 + (i / 2) * 26;
            Rotation rotation = Rotation.values()[i % Rotation.values().length];
            BuildingPlacement placement = place(buildings.get(i), f.pos(x,1,z), rotation, account.ownerName);
            helper.assertTrue(placement != null && !placement.isBuilt && !placement.selfBuilding, "real paid unfinished construction: " + buildings.get(i).structureName);
            placements.add(placement);
            FireflyWorker worker = f.worker(account.ownerName, placement.minCorner.getX()-2, placement.minCorner.getY(), placement.centrePos.getZ());
            workers.add(worker);
            UnitServerEvents.addActionItem(account.ownerName, UnitAction.BUILD_REPAIR, -1, new int[]{worker.getId()}, placement.originPos, placement.originPos);
        }
        int[] afterPayment = balance(account);
        boolean[] finished = {false};
        for (int tick=1; tick<7300; tick++) {
            int currentTick = tick;
            helper.runAtTickTime(tick, () -> {
                if (finished[0]) return;
                for (int i=0; i<placements.size(); i++)
                    if (placements.get(i).isBuilt) workers.get(i).setNoAi(true);
                if (!placements.stream().allMatch(p -> p.isBuilt)) return;
                finished[0] = true;
                for (BuildingPlacement placement : placements) {
                    placement.initPlacedBlocks();
                    helper.assertTrue(placement.getBlocksPlaced() == placement.getBlocksTotal(), "normal worker queue + real updates retain every nonair block: " + placement.getBuilding().structureName);
                    helper.assertTrue(!placement.selfBuilding, "no accelerated queue or self-build was used");
                }
                assertBalance(helper, account, afterPayment, "construction does not invent resource production or charge twice");
                // Exercise every final support with real neighbor notifications, then let
                // scheduled updates run. Cached placed counts alone can hide dropped lamps.
                for (BuildingPlacement placement : placements)
                    for (BuildingBlock block : placement.getBlocks())
                        if (!block.getBlockState().isAir())
                            f.level.updateNeighborsAt(block.getBlockPos(),f.level.getBlockState(block.getBlockPos()).getBlock());
                helper.runAtTickTime(currentTick+20, () -> {
                    for (BuildingPlacement placement : placements) {
                        placement.initPlacedBlocks();
                        helper.assertTrue(placement.getBlocksPlaced()==placement.getBlocksTotal(),
                                "all template blocks survive delayed neighbor updates: "+placement.getBuilding().structureName);
                        for (BuildingBlock block:placement.getBlocks()) if(!block.getBlockState().isAir()) {
                            var actual=f.level.getBlockState(block.getBlockPos());
                            helper.assertTrue(actual.getBlock()==block.getBlockState().getBlock() && actual.canSurvive(f.level,block.getBlockPos()),
                                    "real structure retains correct block with valid support: "+placement.getBuilding().structureName+"/"+block.getBlockPos());
                        }
                    }
                    assertBalance(helper,account,afterPayment,"support updates do not trigger hidden paid repairs or income");
                    codecRoundtrip(helper, placements);
                    for (BuildingPlacement placement : placements) {
                        int placed = placement.getBlocksPlaced();
                        placement.destroyRandomBlocks(4);
                        helper.assertTrue(placement.getBlocksPlaced() < placed, "real combat damage removes blocks: " + placement.getBuilding().structureName);
                        placement.destroyRandomBlocks(100000);
                    }
                });
                helper.runAtTickTime(currentTick+30, () -> {
                    helper.assertTrue(placements.stream().noneMatch(p -> BuildingServerEvents.getBuildings().contains(p)), "normal server destruction removes all eight damaged buildings");
                    assertBalance(helper, account, afterPayment, "destroyed scaffolds never award ore/food/wood/emerald");
                    ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_PASS eight_worker_completion_codec_damage elapsedTicks={}", currentTick);
                    helper.succeed();
                });
            });
        }
        for (int diagnostic : new int[]{100,1000,5000,7000}) helper.runAtTickTime(diagnostic, () -> {
            if (!finished[0]) ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_BUILD_DIAGNOSTIC tick={} buildings={}", diagnostic,
                    placements.stream().map(p -> p.getBuilding().structureName + "=" + p.getBlocksPlaced() + "/" + p.getBlocksTotal()
                            + " builders=" + p.getBuilders().size() + " missing=" + p.getBlocks().stream().filter(b -> !b.getBlockState().isAir() && !b.isPlaced(f.level))
                            .collect(java.util.stream.Collectors.groupingBy(b -> b.getBlockState().toString(), java.util.stream.Collectors.counting()))).toList());
        });
        helper.runAtTickTime(7400, () -> helper.assertTrue(finished[0], "all eight must finish via genuine worker AI at the unmodified build rate"));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void sandboxForgetDetachesWithoutDestroyingGeometryOrStoredIdentity(GameTestHelper helper) {
        Fixture f=new Fixture(helper);
        Resources owner=f.account("detach",DreamseekersMod.DREAMSEEKERS,10000);
        f.flat(1,1,45,45);
        BuildingPlacement original=(BuildingPlacement)f.install(buildings(helper).get(0),owner.ownerName,f.pos(8,1,8),Rotation.NONE);
        UUID identity=view(original).stateSnapshot().instanceId();
        ListTag saved=original.getDataStorage().write();
        var worldBefore=original.getBlocks().stream().map(b->f.level.getBlockState(b.getBlockPos())).toList();
        SandboxServer.removeBuilding(original.originPos);
        helper.assertTrue(!BuildingServerEvents.getBuildings().contains(original) && view(original).lifecycleCount(DETACHED)==1
                && view(original).lifecycleCount(DreamFunctionService.Lifecycle.DESTROYED)==0,"real sandbox forget dispatches detach, not destruction");
        helper.assertTrue(original.getDataStorage().write().equals(saved),"detach does not mutate persisted owner/completion/identity metadata");
        helper.assertTrue(worldBefore.equals(original.getBlocks().stream().map(b->f.level.getBlockState(b.getBlockPos())).toList()),
                "sandbox forget leaves all world geometry intact");
        status(helper,view(original).request(f.actor(owner.ownerName),requestFor(0,UUID.randomUUID(),original.originPos)),NOT_LIVE,
                "detached reference cannot perform functional requests");
        original.onRemoved(BuildingPlacement.RemovalReason.SANDBOX);
        helper.assertTrue(view(original).lifecycleCount(DETACHED)==1,"repeated removal notification is idempotent");
        assertTerminalOnBuiltNoop(helper,original,"detached stale reference");
        BuildingServerEvents.getBuildings().add(original);
        status(helper,view(original).request(f.actor(owner.ownerName),requestFor(0,UUID.randomUUID(),original.originPos)),NOT_LIVE,
                "a stale detached object cannot be revived by list reinsertion");
        BuildingServerEvents.getBuildings().remove(original);
        BuildingPlacement fresh=(BuildingPlacement)BuildingUtils.getNewBuildingPlacement(original.getBuilding(),f.level,
                original.originPos,original.rotation,original.ownerName,false);
        installDecodedStorage(fresh,DataStorage.read(saved,f.level.getServer()));fresh.isBuilt=true;
        BuildingServerEvents.getBuildings().add(fresh);fresh.tick(f.level);
        helper.assertTrue(view(fresh).lifecycleCount(RESTORED)==1 && identity.equals(view(fresh).stateSnapshot().instanceId()),
                "a legitimate new restored placement reattaches persisted identity, distinct from stale object revival");
        assertBalance(helper,owner,new int[]{10000,10000,10000,0},"forget/restore does not refund, grant or spend resources");
        ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_PASS sandbox_detach_no_geometry_or_currency_change");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void ownerChangeImmediatelyBeforeCancelPreservesLifecycleAuthority(GameTestHelper helper) {
        Fixture f=new Fixture(helper);
        Resources original=f.account("instantcancel",DreamseekersMod.DREAMSEEKERS,10000);
        Resources recipient=f.account("cancelrecipient",DreamseekersMod.DREAMSEEKERS,10000);
        f.flat(1,1,85,85);
        f.install(DreamseekersMod.DREAM_ANCHOR,original.ownerName,f.pos(4,1,4),Rotation.NONE);
        // Keep the transferred-to account live after the subject is cancelled;
        // otherwise ordinary RoN defeat removes its ledger and scores entirely.
        f.install(DreamseekersMod.RESONANCE_DAIS,recipient.ownerName,f.pos(4,1,30),Rotation.NONE);
        Building building=buildings(helper).get(0);
        BlockPos site=f.pos(45,1,45);
        BuildingPlacement untouched=(BuildingPlacement)place(building,site,Rotation.NONE,original.ownerName);
        helper.assertTrue(untouched!=null && view(untouched).stateSnapshot().instanceId()==null,"paid placement need not attach before first tick/save");
        BuildingServerEvents.cancelBuilding(untouched,original.ownerName);
        helper.assertTrue(view(untouched).lifecycleCount(ATTACHED)==0 && view(untouched).lifecycleCount(DreamFunctionService.Lifecycle.DESTROYED)==1
                && view(untouched).stateSnapshot().instanceId()==null,"cancel before first tick does not create persistent functional identity");
        assertTerminalOnBuiltNoop(helper,untouched,"cancelled before attachment");
        assertBalance(helper,original,new int[]{10000,10000,10000,0},"same-tick unbuilt cancellation reverses exact debit");
        BuildingPlacement attached=(BuildingPlacement)place(building,site,Rotation.NONE,original.ownerName);
        helper.assertTrue(attached!=null,"second real paid fixture succeeds");
        attached.getDataStorage();
        UUID identity=view(attached).stateSnapshot().instanceId();
        helper.assertTrue(identity!=null && view(attached).lifecycleCount(ATTACHED)==1,"actual save boundary attached once");
        attached.ownerName=recipient.ownerName;
        // No intervening getDataStorage, request or world tick: cancellation first removes
        // the object from RoN's live list and then calls its real destroy override.
        BuildingServerEvents.cancelBuilding(attached,recipient.ownerName);
        helper.assertTrue(view(attached).lifecycleCount(OWNER_CHANGED)==1 && view(attached).lifecycleCount(DreamFunctionService.Lifecycle.DESTROYED)==1
                && view(attached).stateSnapshot().ownerRevision()==1 && view(attached).stateSnapshot().owner().equals(recipient.ownerName)
                && identity.equals(view(attached).stateSnapshot().instanceId()),"immediate removal reports the current owner with stable identity before destroy");
        helper.assertTrue(!BuildingServerEvents.getBuildings().contains(attached),"immediate cancel removed live identity");
        assertBalance(helper,original,new int[]{10000-building.cost.food,10000-building.cost.wood,10000-building.cost.ore,0},
                "original payer is debited once before explicit test ownership transfer");
        assertBalance(helper,recipient,new int[]{10000+building.cost.food,10000+building.cost.wood,10000+building.cost.ore,0},
                "existing cancellation rule credits current owner, not a stale function owner");
        ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_PASS immediate_owner_cancel_lifecycle");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 160)
    public static void typedHooksFailClosedThroughAttachOwnerRestoreAndDestroy(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Resources owner = f.account("hooks", DreamseekersMod.DREAMSEEKERS, 10000);
        Resources recipient = f.account("newowner", DreamseekersMod.DREAMSEEKERS, 10000);
        Resources wrongFaction = f.account("wrongroleowner", Factions.VILLAGERS, 10000);
        ServerPlayer actor = f.actor(owner.ownerName), newActor = f.actor(recipient.ownerName);
        ServerPlayer wrongActor = f.actor(wrongFaction.ownerName);
        f.flat(1,1,108,130);
        // Non-subject sentinel, spatially isolated from all eight roles. Unlike
        // a capitol it spawns no animals; it only prevents ordinary last-building
        // defeat so terminal callbacks are checked against a live owner/ledger.
        f.install(DreamseekersMod.RESONANCE_DAIS,recipient.ownerName,f.pos(4,1,4),Rotation.NONE);
        FireflyWorker target = f.worker(owner.ownerName,f.pos(10,2,20).getX(),f.pos(10,2,20).getY(),f.pos(10,2,20).getZ());
        target.setNoAi(true);
        float targetHealth = target.getHealth();
        int[] ownerBefore = balance(owner), recipientBefore = balance(recipient), wrongBefore = balance(wrongFaction);
        List<BuildingPlacement> subjects = new ArrayList<>();
        List<DreamFunctionRequest> requests = new ArrayList<>();
        List<Building> definitions = buildings(helper);
        for (int i=0;i<definitions.size();i++) {
            Building building = definitions.get(i);
            BlockPos site = f.pos(32+(i%2)*44,1,32+(i/2)*26);
            BuildingPlacement preview = (BuildingPlacement) BuildingUtils.getNewBuildingPlacement(building,f.level,
                    site,Rotation.values()[i%4],owner.ownerName,false);
            DreamFunctionRequest request = requestFor(i,target.getUUID(),site);
            helper.assertTrue(view(preview).stateSnapshot().instanceId()==null && view(preview).lifecycleCount(ATTACHED)==0,
                    "creating an unregistered preview/factory has no server lifecycle or UUID side effect");
            status(helper,view(preview).request(actor,request),NOT_LIVE,"unregistered clone cannot issue effects");
            BuildingPlacement p=(BuildingPlacement) f.install(building,owner.ownerName,site,Rotation.values()[i%4]);
            helper.assertTrue(view(p).lifecycleCount(ATTACHED)==1 && view(p).lifecycleCount(COMPLETED)==1,
                    "real registered onBuilt attaches and completes exactly once");
            helper.assertTrue(view(p).stateSnapshot().supported() && view(p).stateSnapshot().instanceId()!=null && view(p).stateSnapshot().built(),
                    "real completed placement owns supported versioned metadata");
            p.getDataStorage(); p.getDataStorage();
            helper.assertTrue(view(p).lifecycleCount(ATTACHED)==1 && view(p).lifecycleCount(COMPLETED)==1,"repeated save observation is idempotent");
            status(helper,view(p).request(actor,request),UNAVAILABLE,"valid typed role is explicitly not enabled");
            status(helper,view(p).request(null,request),UNAUTHORIZED,"null actor cannot issue effects");
            status(helper,view(p).request(newActor,request),UNAUTHORIZED,"foreign authenticated sender cannot issue effects");
            status(helper,view(p).request(actor,null),INVALID_REQUEST,"null typed request is rejected");
            status(helper,view(p).request(actor,requestFor((i+1)%8,target.getUUID(),site)),WRONG_ROLE,"request cannot switch the building role");
            if(i==0) status(helper,view(p).request(actor,new DreamFunctionRequest.Logistics(target.getUUID(),site,DreamseekersMod.id("test_resource"),1,
                    DreamFunctionRequest.LogisticsOperation.DROPOFF)),UNAVAILABLE,"dropoff stub does not silently enable resource delivery");
            if(i==2) {
                status(helper,view(p).request(actor,new DreamFunctionRequest.ShadowAtomic(UUID.randomUUID(),Long.MAX_VALUE,1)),INVALID_REQUEST,"shadow arithmetic overflow rejected");
                status(helper,view(p).request(actor,new DreamFunctionRequest.ShadowAtomic(UUID.randomUUID(),0,-1)),INVALID_REQUEST,"shadow underflow rejected");
                status(helper,view(p).request(actor,new DreamFunctionRequest.ShadowAtomic(UUID.randomUUID(),1,-1)),UNAVAILABLE,"valid spend remains disabled");
            }
            if(i==5) for(var operation:DreamFunctionRequest.ContractOperation.values())
                status(helper,view(p).request(actor,new DreamFunctionRequest.Contract(target.getUUID(),DreamseekersMod.id("test_contract"),operation)),UNAVAILABLE,"all contract/taming queries remain disabled");
            if(i==6) for(var operation:DreamFunctionRequest.ResonanceOperation.values())
                status(helper,view(p).request(actor,new DreamFunctionRequest.Resonance(site,DreamseekersMod.id("test_effect"),operation)),UNAVAILABLE,"network membership/effects remain disabled");
            if(i==7) for(var operation:DreamFunctionRequest.DefenseOperation.values())
                status(helper,view(p).request(actor,new DreamFunctionRequest.Defense(target.getUUID(),operation)),UNAVAILABLE,"target/vision queries remain disabled");
            p.isBuilt=false;
            p.getDataStorage();
            status(helper,view(p).request(actor,request),NOT_BUILT,"unfinished/deactivated placement cannot perform effects");
            helper.assertTrue(view(p).lifecycleCount(DEACTIVATED)==1,"completion loss reaches lifecycle hook");
            p.isBuilt=true;
            p.getDataStorage();
            helper.assertTrue(view(p).lifecycleCount(COMPLETED)==2,"re-completion observed once");
            subjects.add(p); requests.add(request);
        }
        helper.runAtTickTime(20,()->{
            helper.assertTrue(target.isAlive() && target.getHealth()==targetHealth && target.getActiveEffectsMap().isEmpty(),
                    "disabled defense/contract/resonance never damage, tame or buff target");
            for(int i=0;i<subjects.size();i++) {
                BuildingPlacement p=subjects.get(i);
                UUID identity=view(p).stateSnapshot().instanceId();
                p.ownerName=recipient.ownerName; // RoN capture/ownership uses this public authoritative field.
                p.getDataStorage();
                helper.assertTrue(view(p).lifecycleCount(OWNER_CHANGED)==1 && view(p).stateSnapshot().ownerRevision()==1
                        && view(p).stateSnapshot().owner().equals(recipient.ownerName),"authoritative owner mutation reaches extension before next request/save");
                status(helper,view(p).request(actor,requests.get(i)),UNAUTHORIZED,"old owner loses authority immediately");
                status(helper,view(p).request(newActor,requests.get(i)),UNAVAILABLE,"new valid owner sees unavailable role, not old owner's state");
                DataStorage decoded=DataStorage.read(p.getDataStorage().write(),f.level.getServer());
                BuildingPlacement restored=(BuildingPlacement)BuildingUtils.getNewBuildingPlacement(p.getBuilding(),f.level,
                        p.originPos,p.rotation,p.ownerName,false);
                installDecodedStorage(restored,decoded);
                restored.isBuilt=p.isBuilt;
                BuildingServerEvents.getBuildings().remove(p);
                BuildingServerEvents.getBuildings().add(restored);
                restored.tick(f.level);
                helper.assertTrue(view(restored).lifecycleCount(RESTORED)==1 && view(restored).lifecycleCount(ATTACHED)==0
                        && view(restored).lifecycleCount(COMPLETED)==0,"restore dispatches RESTORED, never duplicate completion grants");
                helper.assertTrue(identity.equals(view(restored).stateSnapshot().instanceId()) && view(restored).stateSnapshot().ownerRevision()==1
                        && view(restored).stateSnapshot().owner().equals(recipient.ownerName),"versioned metadata preserves UUID/owner revision across codec+factory");
                status(helper,view(restored).request(newActor,requests.get(i)),UNAVAILABLE,"restored role remains disabled");
                subjects.set(i,restored);
            }
            assertBalance(helper,owner,ownerBefore,"old owner unchanged by all typed requests/lifecycle");
            assertBalance(helper,recipient,recipientBefore,"new owner receives no lifecycle windfall");
        });
        helper.runAtTickTime(40,()->{
            for(int i=0;i<subjects.size();i++) {
                BuildingPlacement p=subjects.get(i);
                p.ownerName=wrongFaction.ownerName;
                status(helper,view(p).request(wrongActor,requests.get(i)),WRONG_FACTION,"own sender in another faction cannot use future role");
                p.ownerName=recipient.ownerName;
                p.getDataStorage();
                CompoundTag future=new CompoundTag();
                future.putInt("schemaVersion",99);future.putString("futureField","must-not-discard");
                p.getDataStorage().setData(DreamFunctionalPlacement.STATE,DreamFunctionState.read(future));
                status(helper,view(p).request(newActor,requests.get(i)),UNSUPPORTED_SCHEMA,"unknown schema fails closed, not reset to enabled");
                helper.assertTrue(p.getDataStorage().getData(DreamFunctionalPlacement.STATE).write().equals(future),
                        "future schema payload preserved without downgrade");
                BuildingServerEvents.getBuildings().remove(p);
                status(helper,view(p).request(newActor,requests.get(i)),NOT_LIVE,"removed object cannot service a stale reference");
                p.destroy(f.level);p.destroy(f.level);
                helper.assertTrue(view(p).lifecycleCount(DreamFunctionService.Lifecycle.DESTROYED)==1,"real destroy hook is idempotent");
                assertTerminalOnBuiltNoop(helper,p,"destroyed stale reference");
                status(helper,view(p).request(newActor,requests.get(i)),DreamFunctionResult.Status.DESTROYED,"destroyed object cannot be reused");
            }
        });
        helper.runAtTickTime(80,()->{
            assertBalance(helper,owner,ownerBefore,"disabled typed hooks never grant/spend old-owner currency");
            assertBalance(helper,recipient,recipientBefore,"disabled typed hooks never grant/spend new-owner currency");
            assertBalance(helper,wrongFaction,wrongBefore,"disabled typed hooks never touch wrong faction ledger");
            helper.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Unit u && (u.getOwnerName().equals(owner.ownerName)
                    || u.getOwnerName().equals(recipient.ownerName) || u.getOwnerName().equals(wrongFaction.ownerName))).count()==1,
                    "production/contracts/lifecycle do not spawn or convert any units");
            helper.assertTrue(target.isAlive() && target.getHealth()==targetHealth && target.getActiveEffectsMap().isEmpty()
                    && target.getOwnerName().equals(owner.ownerName),"disabled hooks leave target health/effects/ownership intact");
            ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_PASS typed_noops_owner_restore_unknown_schema_destroy buildings=8");
            helper.succeed();
        });
    }

    private static DreamFunctionalView view(BuildingPlacement placement) { return (DreamFunctionalView)placement; }

    private static DreamFunctionRequest requestFor(int role,UUID target,BlockPos source) {
        var key=DreamseekersMod.id("disabled_test_request");
        return switch(role) {
            case 0 -> new DreamFunctionRequest.Logistics(target,source,key,1,DreamFunctionRequest.LogisticsOperation.GATHER);
            case 1 -> new DreamFunctionRequest.OreExtraction(source,1);
            case 2 -> new DreamFunctionRequest.ShadowAtomic(UUID.randomUUID(),0,1);
            case 3 -> new DreamFunctionRequest.Production(key);
            case 4 -> new DreamFunctionRequest.Research(key);
            case 5 -> new DreamFunctionRequest.Contract(target,key);
            case 6 -> new DreamFunctionRequest.Resonance(source,key);
            case 7 -> new DreamFunctionRequest.Defense(target);
            default -> throw new IllegalArgumentException("unknown test role");
        };
    }
    private static void status(GameTestHelper helper,DreamFunctionResult result,DreamFunctionResult.Status expected,String reason) {
        helper.assertTrue(result.status()==expected && !result.available() && result.reasonKey()!=null && !result.reasonKey().isBlank(),
                reason+": expected="+expected+", actual="+result);
    }

    /** A stale caller must not reach base onBuilt, which also increments real RTS scores. */
    private static void assertTerminalOnBuiltNoop(GameTestHelper helper,BuildingPlacement placement,String reason) {
        boolean built=placement.isBuilt;
        var snapshot=view(placement).stateSnapshot();
        int[] lifecycle=Arrays.stream(DreamFunctionService.Lifecycle.values()).mapToInt(view(placement)::lifecycleCount).toArray();
        RTSPlayer owner=PlayerServerEvents.getRTSPlayer(placement.ownerName);
        helper.assertTrue(owner!=null,"terminal callback fixture has an authoritative RTS owner");
        int[] scores=owner.scores.getScoreListAsArray();
        placement.onBuilt();
        helper.assertTrue(placement.isBuilt==built && view(placement).stateSnapshot().equals(snapshot)
                        && Arrays.equals(lifecycle,Arrays.stream(DreamFunctionService.Lifecycle.values()).mapToInt(view(placement)::lifecycleCount).toArray())
                        && Arrays.equals(scores,owner.scores.getScoreListAsArray()),
                reason+": onBuilt cannot change completion, metadata, lifecycle or construction score");
    }

    /** Local SavedData codec + disk bytes + factory. Does not reset global server state or claim a process restart. */
    private static void codecRoundtrip(GameTestHelper helper, List<BuildingPlacement> placements) {
        BuildingSaveData saved = new BuildingSaveData();
        for (BuildingPlacement p : placements)
            saved.buildings.add(new BuildingSave(p.originPos, p.getLevel(), p.getBuilding(), p.ownerName, p.rotation,
                    p.originPos, false, p.isBuilt, p.getUpgradeLevel(), null, BlockPos.ZERO, p.scenarioRoleIndex,
                    p.getDataStorage(), p.partialBlocksDestroyed, new ListTag()));
        try {
            var file = Files.createTempFile("dreamseekers-eight-codec-", ".nbt");
            BuildingSaveData loaded;
            try {
                NbtIo.writeCompressed(saved.save(new CompoundTag()), file.toFile());
                loaded = BuildingSaveData.load(NbtIo.readCompressed(file.toFile()), helper.getLevel().getServer());
            } finally { Files.deleteIfExists(file); }
            helper.assertTrue(loaded.buildings.size() == 8, "all eight named buildings survive real compressed SavedData codec");
            for (int i=0; i<placements.size(); i++) {
                BuildingPlacement original = placements.get(i);
                BuildingSave record = loaded.buildings.get(i);
                helper.assertTrue(record.building == original.getBuilding() && record.ownerName.equals(original.ownerName)
                        && record.rotation == original.rotation && record.originPos.equals(original.originPos) && record.isBuilt,
                        "identity/owner/rotation/completion survive codec: " + original.getBuilding().structureName);
                BuildingPlacement restored = BuildingUtils.getNewBuildingPlacement(record.building, helper.getLevel(), record.originPos,
                        record.rotation, record.ownerName, record.isDiagonalBridge);
                installDecodedStorage(restored,record.dataStorage);
                restored.isBuilt = record.isBuilt;
                restored.initPlacedBlocks();
                helper.assertTrue(restored.getBlocksPlaced() == restored.getBlocksTotal(), "loaded factory sees unchanged real world template blocks");
            }
        } catch (Exception e) { throw new RuntimeException("eight-building compressed SavedData/factory roundtrip", e); }
    }

    /** Test-only normal-loader field injection; no production visibility/API change. */
    private static void installDecodedStorage(BuildingPlacement placement,DataStorage storage) {
        try {
            var field=BuildingPlacement.class.getDeclaredField("dataStorage");
            field.setAccessible(true);
            field.set(placement,storage);
        } catch (ReflectiveOperationException error) { throw new RuntimeException("test loader DataStorage injection",error); }
    }

    private static List<Building> buildings(GameTestHelper helper) {
        List<Building> result = new ArrayList<>();
        for (String id : IDS) {
            Building building = ReignOfNetherRegistries.BUILDING.get(DreamseekersMod.id(id));
            helper.assertTrue(building != null && id.equals(building.structureName), "exact namespaced registry identity: " + id);
            result.add(building);
        }
        return result;
    }
    private static BuildingPlacement place(Building b, BlockPos origin, Rotation rotation, String owner) {
        return BuildingServerEvents.placeBuilding(b, origin, rotation, owner, new int[0], false, false, false, true);
    }
    private static int[] balance(Resources r) { return new int[]{r.food,r.wood,r.ore,r.emerald}; }
    private static void assertBalance(GameTestHelper h, Resources r, int[] expected, String reason) {
        h.assertTrue(ResourcesServerEvents.resourcesList.contains(r),reason+": account remains in the authoritative live ledger");
        h.assertTrue(Arrays.equals(balance(r),expected), reason+": actual="+Arrays.toString(balance(r))+", expected="+Arrays.toString(expected));
    }
    private static final class Fixture {
        final GameTestHelper helper;
        final ServerLevel level;
        Fixture(GameTestHelper helper) { this.helper=helper; this.level=helper.getLevel(); }
        BlockPos pos(int x, int y, int z) { return BuildingAssetGameTests.outdoorPos(helper,x,y,z); }
        void flat(int x,int z,int width,int depth) {
            BlockPos p=pos(x,1,z);
            for(int dx=0;dx<width;dx++) for(int dz=0;dz<depth;dz++) {
                level.setBlock(p.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);
                for(int dy=1;dy<=20;dy++) level.setBlock(p.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
            }
            BuildingAssetGameTests.clearPreexistingFixtureDrops(helper,p,width,depth);
            helper.assertTrue(level.canSeeSky(p.above()), "new fixture is genuinely outdoors");
        }
        Resources account(String suffix,Faction faction,int amount) {
            String name=PREFIX+suffix;
            helper.assertTrue(PlayerServerEvents.getRTSPlayer(name)==null,"unique scaffold test account");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,faction));
            Resources resources=new Resources(name,amount,amount,amount);
            ResourcesServerEvents.resourcesList.add(resources);
            return resources;
        }
        BuildingPlacement install(Building b,String owner,BlockPos p,Rotation rotation) {
            for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(b.getRelativeBlockData(level),level,p,rotation))
                level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            BuildingPlacement result=BuildingUtils.getNewBuildingPlacement(b,level,p,rotation,owner,false);
            BuildingServerEvents.getBuildings().add(result);
            result.onBuilt(); // Explicit finished fixture only. The construction test never calls this on its eight subjects.
            return result;
        }
        FireflyWorker worker(String owner,int x,int y,int z) {
            FireflyWorker worker=DreamseekersMod.FIREFLY_WORKER.get().create(level);
            helper.assertTrue(worker!=null,"real worker type exists");
            worker.setOwnerName(owner);
            worker.moveTo(x+0.5,y,z+0.5,0,0);
            helper.assertTrue(level.addFreshEntity(worker),"real worker joins world");
            return worker;
        }
        ServerPlayer actor(String owner) {
            return FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(owner.getBytes(StandardCharsets.UTF_8)),owner));
        }
    }
}
