package com.solegendary.reignofnether.research;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.*;

import java.util.*;

/** Normal saved-data and production regressions; no network or authorization probes. */
@GameTestHolder("api59_research_compat")
@PrefixGameTestTemplate(false)
public final class ResearchCompatibilityGameTests {
    private static final String BATCH = "api59_research_compat";
    private static final String PREFIX = "Api59Research_";
    private static final List<ProductionPlacement> HOSTS = new ArrayList<>();

    private static ResourceLocation key(String path) {
        return ResourceLocation.fromNamespaceAndPath("reignofnether", path);
    }

    private static CompoundTag entry(String owner, String field, String value) {
        CompoundTag entry = new CompoundTag();
        entry.putString("ownerName", owner);
        entry.putString(field, value);
        return entry;
    }

    private static CompoundTag document(ListTag entries) {
        CompoundTag document = new CompoundTag();
        document.put("researchItems", entries);
        return document;
    }

    @GameTest(templateNamespace="api59_research_compat", template="ore_mine_test_arena", batch=BATCH, timeoutTicks=100)
    public static void allExplicitLegacyNamesRetainRegisteredResearchIdentity(GameTestHelper h) {
        String[][] aliases = {
            {"Iron Beacon", "beacon_level_1"}, {"Gold Beacon", "beacon_level_2"},
            {"Emerald Beacon", "beacon_level_3"}, {"Diamond Beacon", "beacon_level_4"},
            {"Netherite Beacon", "beacon_level_5"}, {"Walls of Fire", "blaze_firewall"},
            {"Shield Tactics", "brute_shields"}, {"Officer's Quarters", "castle_flag"},
            {"Drowned Zombies", "research_drowned"}, {"Vexing Summons", "evoker_vexes"},
            {"Husk Zombies", "research_husks"}, {"Lightning Rod", "lab_lightning_rod"},
            {"Multishot Crossbows", "pillager_crossbows"}, {"Civilian Portal", "portal_for_civilian"},
            {"Military Portal", "portal_for_military"}, {"Transport Portal", "portal_for_transport"},
            {"Ravager Artillery", "ravager_cavalry"}, {"Worker Carry Bags", "resource_capacity"},
            {"Infested Defences", "silverfish"}, {"Slimy Conversion", "slime_conversion"},
            {"Sticky Webbing", "spider_webs"}, {"Stray Skeletons", "research_strays"},
            {"Diamond Axes", "vindicator_axes"}, {"Wither Death Clouds", "wither_clouds"}
        };
        ListTag entries = new ListTag();
        for (int i = 0; i < aliases.length; i++)
            entries.add(entry(PREFIX + i, "researchName", aliases[i][0]));
        var loaded = ResearchSaveData.load(document(entries));
        h.assertTrue(loaded.researchItems.size() == aliases.length, "all 24 supported legacy records retained");
        for (int i = 0; i < aliases.length; i++) {
            var expected = key(aliases[i][1]);
            var actual = loaded.researchItems.get(i);
            var item = ReignOfNetherRegistries.PRODUCTION_ITEM.get(expected);
            h.assertTrue(item != null && item.workKind == ProductionItem.WorkKind.RESEARCH,
                    "alias target is registered research: " + expected);
            h.assertTrue(actual.equals(Pair.of(PREFIX + i, expected)), "owner and canonical ID: " + aliases[i][0]);
        }
        h.assertTrue(ResearchSaveData.load(loaded.save(new CompoundTag())).researchItems.equals(loaded.researchItems),
                "migrated canonical keys remain stable when saved again");
        System.out.println("API59_RESEARCH_LEGACY_PASS aliases=" + aliases.length);
        h.succeed();
    }

    @GameTest(templateNamespace="api59_research_compat", template="ore_mine_test_arena", batch=BATCH, timeoutTicks=100)
    public static void alreadyResavedBrokenLegacyKeysAreRepaired(GameTestHelper h) {
        ListTag entries = new ListTag();
        String[] paths = {"husks", "drowned", "strays"};
        for (String path : paths) entries.add(entry(PREFIX + path, "researchKey", key(path).toString()));
        var loaded = ResearchSaveData.load(document(entries));
        h.assertTrue(loaded.researchItems.size() == 3, "all three resaved entries retained");
        for (int i = 0; i < paths.length; i++)
            h.assertTrue(loaded.researchItems.get(i).equals(Pair.of(PREFIX + paths[i], key("research_" + paths[i]))),
                    "old buggy migration output repaired: " + paths[i]);
        var roundtrip = ResearchSaveData.load(loaded.save(new CompoundTag()));
        h.assertTrue(roundtrip.researchItems.equals(loaded.researchItems), "repaired keys are idempotent");
        System.out.println("API59_RESEARCH_RESAVED_KEYS_PASS entries=3");
        h.succeed();
    }

    @GameTest(templateNamespace="api59_research_compat", template="ore_mine_test_arena", batch=BATCH, timeoutTicks=100)
    public static void modernAndForeignKeysRemainUnchanged(GameTestHelper h) {
        ResearchSaveData data = new ResearchSaveData();
        for (String path : new String[]{"research_husks", "research_drowned", "research_strays", "militia_bows", "future_research"})
            data.researchItems.add(Pair.of(PREFIX + path, key(path)));
        for (String path : new String[]{"husks", "drowned", "strays"})
            data.researchItems.add(Pair.of(PREFIX + "foreign", ResourceLocation.fromNamespaceAndPath("example_addon", path)));
        h.assertTrue(ResearchSaveData.load(data.save(new CompoundTag())).researchItems.equals(data.researchItems),
                "valid, unknown and foreign-namespace IDs are not rewritten or dropped");
        h.succeed();
    }

    private static ProductionPlacement host(GameTestHelper h, String suffix) {
        String owner = PREFIX + suffix;
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner, Factions.VILLAGERS));
        ResourcesServerEvents.resourcesList.add(new Resources(owner, 10000, 10000, 10000));
        var horizontal = h.absolutePos(new BlockPos(12, 0, 12));
        var origin = new BlockPos(horizontal.getX(), Math.min(160, h.getLevel().getMaxBuildHeight() - 24) + 1, horizontal.getZ());
        for (var block : BuildingUtils.getAbsoluteBlockData(Buildings.BLACKSMITH.getRelativeBlockData(h.getLevel()),
                h.getLevel(), origin, Rotation.NONE))
            h.getLevel().setBlock(block.getBlockPos(), block.getBlockState(), 2);
        var host = (ProductionPlacement) BuildingUtils.getNewBuildingPlacement(Buildings.BLACKSMITH,
                h.getLevel(), origin, Rotation.NONE, owner, false);
        BuildingServerEvents.getBuildings().add(host);
        HOSTS.add(host);
        host.onBuilt();
        h.assertTrue(host.isBuilt, "finished real blacksmith fixture");
        return host;
    }

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel ignored) {
        for (var host : HOSTS) {
            host.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
            BuildingServerEvents.getBuildings().remove(host);
            ResearchServerEvents.removeAllResearchFor(host.ownerName);
        }
        HOSTS.clear();
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
    }

    @GameTest(templateNamespace="api59_research_compat", template="ore_mine_test_arena", batch=BATCH, timeoutTicks=120)
    public static void paidLegacyResearchCompletionDoesNotProducePhantomUnitScores(GameTestHelper h) {
        var host = host(h, "Paid");
        var player = PlayerServerEvents.getRTSPlayer(host.ownerName);
        var account = ResourcesServerEvents.resourcesList.stream().filter(r -> r.ownerName.equals(host.ownerName)).findFirst().orElseThrow();
        var item = ProductionItems.RESEARCH_MILITIA_BOWS;
        int total = player.scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
        int military = player.scores.scoreList.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED);
        int worker = player.scores.scoreList.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED);
        h.assertTrue(host.startProductionItem(item) && host.productionQueue.size() == 1,
                "normal paid native research queue accepted");
        h.assertTrue(account.food < 10000 || account.wood < 10000 || account.ore < 10000, "real payment occurred");
        // Fixture acceleration only: the ordinary production tick still owns completion.
        host.productionQueue.get(0).ticksLeft = 1;
        h.startSequence().thenWaitUntil(() -> h.assertTrue(host.productionQueue.isEmpty()
                        && ResearchServerEvents.playerHasResearch(host.ownerName, item), "actual research completion"))
                .thenExecute(() -> {
                    h.assertTrue(UnitServerEvents.getAllUnits().stream().noneMatch(e -> e instanceof Unit u
                                    && u.getOwnerName().equals(host.ownerName)), "research created no owned unit");
                    h.assertTrue(player.scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED) == total
                                    && player.scores.scoreList.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED) == military
                                    && player.scores.scoreList.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED) == worker,
                            "all three produced-unit scores unchanged after paid research completion");
                    System.out.println("API59_RESEARCH_PAID_COMPLETION_PASS actualQueue=true acceleratedRemainingTicks=1");
                }).thenSucceed();
    }

    @GameTest(templateNamespace="api59_research_compat", template="ore_mine_test_arena", batch=BATCH, timeoutTicks=100)
    public static void allRegisteredResearchScoreHooksAreNoOps(GameTestHelper h) {
        var host = host(h, "Hooks");
        var player = PlayerServerEvents.getRTSPlayer(host.ownerName);
        var before = new LinkedHashMap<>(player.scores.scoreList);
        int checked = 0;
        for (var item : ReignOfNetherRegistries.PRODUCTION_ITEM) {
            if (item.workKind == ProductionItem.WorkKind.RESEARCH) {
                item.recordScore(host);
                checked++;
                h.assertTrue(player.scores.scoreList.equals(before), "research hook does not score: "
                        + ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item));
            }
        }
        h.assertTrue(checked >= 52, "all 52 legacy research definitions and any loaded receipt research checked");
        System.out.println("API59_RESEARCH_SCORE_HOOKS_PASS researchItems=" + checked + " directHook=true");
        h.succeed();
    }

    @GameTest(templateNamespace="api59_research_compat", template="ore_mine_test_arena", batch=BATCH, timeoutTicks=100)
    public static void ordinaryWorkerAndMilitaryScoreHooksStillCount(GameTestHelper h) {
        var host = host(h, "UnitHooks");
        var player = PlayerServerEvents.getRTSPlayer(host.ownerName);
        int total = player.scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
        int worker = player.scores.scoreList.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED);
        int military = player.scores.scoreList.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED);
        for (var item : List.of(ProductionItems.VILLAGER, ProductionItems.ZOMBIE_VILLAGER, ProductionItems.GRUNT, ProductionItems.ZOMBIE))
            item.recordScore(host);
        h.assertTrue(player.scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED) == total + 4
                        && player.scores.scoreList.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED) == worker + 3
                        && player.scores.scoreList.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED) == military + 1,
                "unit score hooks retain original worker/military classification");
        System.out.println("API59_UNIT_SCORE_HOOK_CONTROL_PASS total=4 worker=3 military=1 directHook=true");
        h.succeed();
    }
}
