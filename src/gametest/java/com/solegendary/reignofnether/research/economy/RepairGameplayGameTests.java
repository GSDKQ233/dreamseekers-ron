package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.placements.StockpilePlacement;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@GameTestHolder("api65_repair_play") @PrefixGameTestTemplate(false)
public final class RepairGameplayGameTests {
    private static final String ARENA = "ore_mine_test_arena";

    private static VillagerUnit worker(Api65EconomyGameTests.F f, BlockPos pos) {
        VillagerUnit worker = EntityRegistrar.VILLAGER_UNIT.get().create(f.h.getLevel());
        worker.setOwnerName(f.owner);
        worker.moveTo(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        f.h.assertTrue(f.h.getLevel().addFreshEntity(worker), "native worker joined the test level");
        f.entities.add(worker);
        return worker;
    }

    @GameTest(templateNamespace="api65_repair_play",template=ARENA,batch="repair_play_cargo")
    public static void mixedCargoCreditsAllCategoriesOnce(GameTestHelper h) throws Exception {
        try (var f = new Api65EconomyGameTests.F(h, "cargo")) {
            ProductionPlacement town = f.install(Buildings.TOWN_CENTRE);
            VillagerUnit worker = worker(f, town.getClosestGroundPos(town.centrePos, 1));
            var apple = new ItemStack(Items.GOLDEN_APPLE, 2);
            apple.getOrCreateTag().putString("Fixture", "keep exactly");
            worker.getItems().addAll(List.of(new ItemStack(Items.WHEAT, 2), apple,
                    new ItemStack(Items.STICK, 3), new ItemStack(Items.STONE, 5), new ItemStack(Items.EMERALD, 7)));
            var goal = worker.getReturnResourcesGoal();
            goal.setBuildingTarget(town);
            goal.depositItems();
            h.assertTrue(worker.getItems().size() == 1 && worker.getItems().get(0) == apple
                    && apple.getCount() == 2 && "keep exactly".equals(apple.getTag().getString("Fixture")),
                    "only unmapped tagged golden apples survive, unchanged");
            int base = 100000;
            h.assertTrue(f.bank.food == base+8 && f.bank.wood == base+3 && f.bank.ore == base+5 && f.bank.emerald == base+7,
                    "actual four-component credit; emerald contributes to total only");
            var scores = PlayerServerEvents.getRTSPlayer(f.owner).scores;
            h.assertTrue(scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED) == 23
                    && scores.scoreList.get(RTSPlayerScoresEnum.FOOD_HARVESTED) == 8
                    && scores.scoreList.get(RTSPlayerScoresEnum.WOOD_HARVESTED) == 3
                    && scores.scoreList.get(RTSPlayerScoresEnum.ORES_HARVESTED) == 5, "all harvest statistics credited");
            var balance = ResearchEconomyFixtures.snapshot(f.bank);
            var scored = scores.getScoreListAsArray();
            goal.setBuildingTarget(town);
            goal.depositItems();
            h.assertTrue(balance.equals(ResearchEconomyFixtures.snapshot(f.bank)) && Arrays.equals(scored,scores.getScoreListAsArray())
                    && worker.getItems().get(0) == apple, "second arrival with only unvalued cargo credits nothing");
            manualChests(f);
        }
        h.succeed();
    }

    private static void manualChests(Api65EconomyGameTests.F f) {
        for (boolean stockpile : new boolean[]{true,false}) {
            var host = f.install(stockpile ? Buildings.OAK_STOCKPILE : Buildings.PORTAL_CIVILIAN);
            var chest = host.getBlocks().stream().map(b -> f.h.getLevel().getBlockEntity(b.getBlockPos()))
                    .filter(ChestBlockEntity.class::isInstance).map(ChestBlockEntity.class::cast).findFirst().orElseThrow();
            var sand = new ItemStack(Items.SOUL_SAND, 3);
            sand.getOrCreateTag().putString("keep", "exact slot");
            var gems = new ItemStack(Items.EMERALD, 5);
            gems.getOrCreateTag().putInt("lot", 41);
            chest.setItem(2, new ItemStack(Items.WHEAT, 2)); chest.setItem(7, sand); chest.setItem(16, gems);
            Runnable deposit = stockpile ? ((StockpilePlacement)host)::checkAndConsumeChestItems : ((PortalPlacement)host)::checkAndConsumeChestItems;
            int food = f.bank.food, emerald = f.bank.emerald;
            ResourcesServerEvents.resourcesList.remove(f.bank);
            try {
                deposit.run();
                f.h.assertTrue(chest.getItem(2).getCount()==2 && chest.getItem(7)==sand && chest.getItem(16)==gems,
                        "absent recipient cannot consume a manual deposit");
            } finally { ResourcesServerEvents.resourcesList.add(f.bank); }
            deposit.run(); deposit.run();
            f.h.assertTrue(chest.getItem(2).isEmpty() && chest.getItem(7)==sand && sand.getCount()==3
                            && (stockpile ? chest.getItem(16).isEmpty() : chest.getItem(16)==gems && gems.getCount()==5)
                            && f.bank.food==food+8 && f.bank.emerald==emerald+(stockpile?5:0),
                    "only paid slots consumed once; civilian portal leaves its unaccepted emeralds in place");
        }
    }

    private static void cancel(Api65EconomyGameTests.F f, ProductionPlacement host, ProductionItem item, UUID observed) {
        cancel(f, host, item, observed, BuildingAction.CANCEL_PRODUCTION);
    }

    private static void cancel(Api65EconomyGameTests.F f, ProductionPlacement host, ProductionItem item, UUID observed, BuildingAction action) {
        String key = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString();
        var packet = new BuildingProductionServerboundPacket(action, key, host.originPos, observed);
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(bytes);
            var decoded = new BuildingProductionServerboundPacket(bytes);
            f.h.assertTrue(java.util.Objects.equals(decoded.expectedEntry, observed) && bytes.readableBytes() == 0,
                    "queue intent survives the real request codec");
            decoded.handle(() -> f.peer.context);
        } finally { bytes.release(); }
        f.peer.channel.runPendingTasks();
    }

    private static void assertReplacementReply(Api65EconomyGameTests.F f, ProductionPlacement host, UUID replacement) {
        int id = ResearchEconomyFixtures.discriminator(new BuildingProductionClientboundPacket(BuildingAction.SYNC_PRODUCTION, "", host.originPos));
        int snapshots = 0;
        for (Object output : f.peer.drain()) if (output instanceof ClientboundCustomPayloadPacket frame) {
            var b = new FriendlyByteBuf(frame.getData().copy());
            try {
                if (b.readVarInt() != id) continue;
                var reply = new BuildingProductionClientboundPacket(b);
                f.h.assertTrue(reply.action == BuildingAction.SYNC_PRODUCTION && b.readableBytes() == 0
                        && reply.rows().size() == 1 && replacement.equals(reply.rows().get(0).entryId()),
                        "stale actor receives the authoritative replacement, not a cancel notice");
                snapshots++;
            } finally { b.release(); }
        }
        f.h.assertTrue(snapshots == 1, "one directed replacement reply");
    }

    @GameTest(templateNamespace="api65_repair_play",template=ARENA,batch="repair_play_front")
    public static void staleFrontCannotCancelIdenticalReplacement(GameTestHelper h) throws Exception {
        try (var f = new Api65EconomyGameTests.F(h, "heads")) {
            var town = f.install(Buildings.TOWN_CENTRE);
            var recipe = ProductionItems.VILLAGER;
            f.production(town, recipe);
            f.production(town, recipe);
            h.assertTrue(town.productionQueue.size() == 2, "two real paid orders of one recipe");
            var a = town.productionQueue.get(0);
            var b = town.productionQueue.get(1);
            h.assertTrue(!a.queueEntryId.equals(b.queueEntryId), "orders have distinct public identities");
            a.ticksLeft = 0;
            town.tick(h.getLevel());
            h.assertTrue(a.completed && town.productionQueue.size() == 1 && town.productionQueue.get(0) == b,
                    "first order genuinely completed and second took its place");
            f.peer.drain();
            var paid = ResearchEconomyFixtures.snapshot(f.bank);
            cancel(f, town, recipe, a.queueEntryId);
            h.assertTrue(town.productionQueue.get(0) == b && paid.equals(ResearchEconomyFixtures.snapshot(f.bank)),
                    "late A click cannot cancel or refund B");
            assertReplacementReply(f, town, b.queueEntryId);
            cancel(f, town, ProductionItems.SCOUT_DOG, b.queueEntryId);
            h.assertTrue(town.productionQueue.get(0) == b && paid.equals(ResearchEconomyFixtures.snapshot(f.bank)),
                    "even current identity cannot cancel a mismatched recipe");
            assertReplacementReply(f, town, b.queueEntryId);
            cancel(f, town, recipe, null);
            assertReplacementReply(f, town, b.queueEntryId);
            cancel(f, town, recipe, b.queueEntryId);
            h.assertTrue(town.productionQueue.isEmpty() && !paid.equals(ResearchEconomyFixtures.snapshot(f.bank)),
                    "current B click cancels and refunds B");
            var refunded = ResearchEconomyFixtures.snapshot(f.bank);
            cancel(f, town, recipe, b.queueEntryId);
            h.assertTrue(refunded.equals(ResearchEconomyFixtures.snapshot(f.bank)), "repeat click does not refund twice");
            try (var delegate = new Api65EconomyGameTests.F(h, "crew")) {
                AlliancesServerEvents.addAlliance(f.owner, delegate.owner);
                try {
                    h.assertTrue(BuildingProductionServerboundPacket.canOperate(f.peer.actor, town)
                            && !BuildingProductionServerboundPacket.canOperate(delegate.peer.actor, town), "owner and ungranted ally are distinct");
                    AlliancesServerEvents.playersWithAlliedControl.add(f.owner);
                    int balance = f.bank.food, crewBalance = delegate.bank.food;
                    delegate.production(town, recipe);
                    f.production(town, recipe);
                    h.assertTrue(town.productionQueue.size() == 2, "both granted ally and owner may start production");
                    var first = town.productionQueue.get(0); var tail = town.productionQueue.get(1);
                    h.assertTrue(first.ticksLeft == tail.ticksLeft && first.nativeQuote.food() > 0, "two untouched same-recipe jobs");
                    cancel(delegate, town, recipe, tail.queueEntryId, BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION);
                    h.assertTrue(town.productionQueue.equals(List.of(first)) && f.bank.food == balance-first.nativeQuote.food()
                            && delegate.bank.food == crewBalance, "only selected tail refunded, to actual payer");
                    cancel(delegate, town, recipe, tail.queueEntryId, BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION);
                    h.assertTrue(town.productionQueue.equals(List.of(first)) && f.bank.food == balance-first.nativeQuote.food(),
                            "replayed tail cannot remove head or credit payer twice");
                    AlliancesServerEvents.playersWithAlliedControl.remove(f.owner);
                    h.assertTrue(!BuildingProductionServerboundPacket.canOperate(delegate.peer.actor, town)
                            && BuildingProductionServerboundPacket.canOperate(f.peer.actor, town), "revocation is effective without relogin");
                    cancel(f, town, recipe, first.queueEntryId);
                    h.assertTrue(f.bank.food == balance && town.productionQueue.isEmpty(), "surviving head retained its original quote");
                } finally {
                    AlliancesServerEvents.playersWithAlliedControl.remove(f.owner);
                    AlliancesServerEvents.removeAlliance(f.owner, delegate.owner);
                }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace="api65_repair_play",template=ARENA,batch="repair_play_codec")
    public static void publicAndPrivateQueueCodecsPreserveEntryIds(GameTestHelper h) throws Exception {
        ResearchEconomyFixtures.register();
        try (var f = new Api65EconomyGameTests.F(h, "view")) {
            var host = f.install(Buildings.BLACKSMITH);
            var a = new ActiveProduction(ProductionItems.VILLAGER, false, f.owner);
            var research = new ActiveProduction(ResearchEconomyFixtures.mixed, false, f.owner);
            var b = new ActiveProduction(ProductionItems.VILLAGER, false, f.owner);
            UUID receipt = UUID.randomUUID();
            research.receiptResearch = new ReceiptResearchBinding(receipt, f.owner, f.bank.accountEpoch,
                    ResearchPlacementIdentity.serverIdentity(host), host.getCaptureRevision());
            a.ticksLeft = 0; b.ticksLeft = 4.5f;
            // Synthesize the queue to test both view formats, not eligibility, payment or receipt settlement.
            host.productionQueue.addAll(List.of(a, research, b));
            var bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                BuildingProductionClientboundPacket.publicSnapshot(host).encode(bytes);
                var publicRows = new BuildingProductionClientboundPacket(bytes).rows();
                h.assertTrue(bytes.readableBytes() == 0 && publicRows.size() == 2
                        && publicRows.get(0).entryId().equals(a.queueEntryId) && publicRows.get(0).ticks() == 0
                        && publicRows.get(1).entryId().equals(b.queueEntryId) && publicRows.get(1).ticks() == 4.5f,
                        "public snapshot preserves same-recipe identities and exact progress, omits private research");
                bytes.clear();
                var packet = new ResearchQueueClientboundPacket(UUID.randomUUID(), 1, "ready", host, f.bank,
                        new EconomyActionGate.Token(UUID.randomUUID(), f.bank.accountEpoch), 0);
                packet.encode(bytes);
                var rows = new ResearchQueueClientboundPacket(bytes).entries;
                h.assertTrue(bytes.readableBytes() == 0 && rows.size() == 3
                        && rows.get(0).queueEntryId().equals(a.queueEntryId) && rows.get(0).receipt() == null
                        && rows.get(1).queueEntryId().equals(research.queueEntryId) && rows.get(1).receipt().equals(receipt)
                        && rows.get(2).queueEntryId().equals(b.queueEntryId) && rows.get(2).receipt() == null,
                        "owner-only mixed snapshot transports both independent identities");
                h.assertTrue(!host.cancelObservedHead(research.item, research.queueEntryId), "ordinary click cannot target a non-head");
                host.productionQueue.remove(a);
                h.assertTrue(!host.cancelObservedHead(research.item, research.queueEntryId)
                        && host.productionQueue.get(0) == research, "ordinary public cancel cannot detach receipt-backed research");
            } finally { bytes.release(); host.productionQueue.clear(); }
        }
        h.succeed();
    }

    private static BuildingPlacement foundation(Api65EconomyGameTests.F f, int n, boolean queue, int... workers) {
        var pos = f.h.absolutePos(new BlockPos(3 + n * 18, 2, 4));
        // Command fixture skips terrain/economy checks; the production builder assignment path is unchanged.
        var b = BuildingServerEvents.placeBuilding(Buildings.VILLAGER_HOUSE, pos, Rotation.NONE, f.owner, workers,
                queue, false, true, true);
        f.h.assertTrue(b != null && BuildingUtils.isBuildingBuildable(false, b), "registered unfinished foundation");
        f.buildings.add(b);
        return b;
    }

    @GameTest(templateNamespace="api65_repair_play",template=ARENA,batch="repair_play_shift")
    public static void firstShiftBuildKeepsActiveWork(GameTestHelper h) throws Exception {
        try (var f = new Api65EconomyGameTests.F(h, "build")) {
            var w1 = worker(f, h.absolutePos(new BlockPos(1,2,1)));
            var w2 = worker(f, h.absolutePos(new BlockPos(2,2,1)));
            var g1 = w1.getBuildRepairGoal(); var g2 = w2.getBuildRepairGoal();
            var a = foundation(f,0,false,w1.getId());
            h.assertTrue(g1.getBuildingTarget() == a && g1.queuedBuildings.isEmpty(), "immediate A is active but not queued");
            var b = foundation(f,1,true,w1.getId(),w2.getId());
            var c = foundation(f,2,true,w1.getId(),w2.getId());
            h.assertTrue(g1.getBuildingTarget() == a && g1.queuedBuildings.equals(List.of(b,c))
                    && g2.getBuildingTarget() == b && g2.queuedBuildings.equals(List.of(b,c)),
                    "each worker independently preserves active work; idle worker starts the first queued house");
            for (var block : a.getBlocks()) h.getLevel().setBlock(block.getBlockPos(), block.getBlockState(), 2);
            a.initPlacedBlocks();
            h.assertTrue(!BuildingUtils.isBuildingBuildable(false,a), "A really reached its full structure");
            g1.tick();
            h.assertTrue(g1.getBuildingTarget() == b, "after completion A advances to B");
            BuildingServerEvents.getBuildings().remove(b); // active foundation removed before completion
            g1.tick(); g2.tick();
            h.assertTrue(g1.getBuildingTarget() == c && g2.getBuildingTarget() == c, "lost B advances both workers to C");
            var d = foundation(f,3,false,w1.getId(),w2.getId());
            h.assertTrue(g1.getBuildingTarget() == d && g2.getBuildingTarget() == d
                    && g1.queuedBuildings.isEmpty() && g2.queuedBuildings.isEmpty(), "immediate replacement still clears the old queue");
        }
        h.succeed();
    }
}
