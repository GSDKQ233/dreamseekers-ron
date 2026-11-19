package com.dreamseekers;

import com.solegendary.reignofnether.alliance.AllianceSaveData;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.scenario.ScenarioRole;
import com.solegendary.reignofnether.scenario.ScenarioServerEvents;
import com.solegendary.reignofnether.player.RTSPlayerSaveData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** Codec/lifecycle mutation contracts; actual cold restart is tested in separate JVMs. */
@GameTestHolder("api59_match_persistence")
@PrefixGameTestTemplate(false)
public final class OrdinaryMatchPersistenceGameTests {
    private static CompoundTag playerTag() {
        RTSPlayerSaveData data = RTSPlayerSaveData.load(new CompoundTag());
        data.rtsPlayers.add(RTSPlayer.getNewPlayer("PersistenceCodec", Factions.VILLAGERS, 97001, 11, false, -1L, -1L));
        return data.save(new CompoundTag());
    }

    @GameTest(template="dreamseekers_test_arena")
    public static void modernPlayerPreferencesRoundTrip(GameTestHelper h) {
        RTSPlayer p = RTSPlayerSaveData.load(playerTag()).rtsPlayers.get(0);
        h.assertTrue(p.startPosColorId == 11 && !p.isDogPerson && p.id == 97001 && p.faction == Factions.VILLAGERS,
                "modern player fields and identity survive codec roundtrip");
        h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena")
    public static void oldPlayerPreferencesKeepHistoricalDefaults(GameTestHelper h) {
        CompoundTag tag = playerTag();
        CompoundTag p = tag.getList("rtsplayers", Tag.TAG_COMPOUND).getCompound(0);
        p.remove("startPosColorId"); p.remove("isDogPerson");
        RTSPlayer restored = RTSPlayerSaveData.load(tag).rtsPlayers.get(0);
        h.assertTrue(restored.startPosColorId == 0 && restored.isDogPerson,
                "older saves retain color zero and dog default instead of becoming cat owners");
        h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena")
    public static void allianceCodecCanonicalPairsAndLegacyEmpty(GameTestHelper h) {
        h.assertTrue(AllianceSaveData.load(new CompoundTag()).snapshot().isEmpty(), "old worlds have no fabricated alliances");
        CompoundTag tag = new CompoundTag(); ListTag list = new ListTag();
        for (String[] names : new String[][] {{"A", "B"}, {"B", "A"}, {"A", "A"}, {"", "C"}}) {
            CompoundTag pair = new CompoundTag(); pair.putString("first", names[0]); pair.putString("second", names[1]); list.add(pair);
        }
        tag.put("alliances", list);
        AllianceSaveData data = AllianceSaveData.load(tag);
        h.assertTrue(data.snapshot().equals(Map.of("A", Set.of("B"), "B", Set.of("A"))), "duplicates collapse and only valid bidirectional pairs restore");
        CompoundTag encoded = data.save(new CompoundTag());
        h.assertTrue(encoded.getList("alliances", Tag.TAG_COMPOUND).size() == 1, "one stable undirected pair per alliance");
        h.assertTrue(AllianceSaveData.load(encoded).snapshot().equals(data.snapshot()), "canonical roundtrip");
        h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena")
    public static void liveAdditionAndRemovalUpdateWorldData(GameTestHelper h) {
        String a = "Api59MutationA", b = "Api59MutationB";
        AllianceSaveData data = AllianceSaveData.getInstance(h.getLevel());
        try {
            AlliancesServerEvents.addAlliance(a, b);
            h.assertTrue(data.snapshot().getOrDefault(a, Set.of()).contains(b) && data.isDirty(), "addition updates dirty world save data immediately");
            AlliancesServerEvents.removeAlliance(a, b);
            h.assertTrue(!data.snapshot().containsKey(a) && !data.snapshot().containsKey(b), "removal cannot resurrect on next save");
        } finally { AlliancesServerEvents.removeAlliance(a, b); }
        h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena")
    public static void resetPersistsEmptyAllianceGraph(GameTestHelper h) {
        AllianceSaveData data = AllianceSaveData.getInstance(h.getLevel());
        Map<String, Set<String>> before = data.snapshot();
        try {
            AlliancesServerEvents.addAlliance("Api59ResetA", "Api59ResetB");
            AlliancesServerEvents.resetAllAlliances();
            h.assertTrue(data.snapshot().isEmpty(), "match reset persists empty graph, including when no replacement pairs follow");
        } finally {
            AlliancesServerEvents.resetAllAlliances();
            before.forEach((owner, allies) -> allies.forEach(ally -> AlliancesServerEvents.addAlliance(owner, ally)));
        }
        h.succeed();
    }

    @GameTest(template="dreamseekers_test_arena")
    public static void allianceViewsCannotBypassPersistence(GameTestHelper h) {
        String a = "Api59ImmutableA", b = "Api59ImmutableB";
        try {
            AlliancesServerEvents.addAlliance(a, b);
            boolean rejected = false;
            try { AlliancesServerEvents.getAllAllies(a).clear(); } catch (UnsupportedOperationException expected) { rejected = true; }
            h.assertTrue(rejected && AlliancesServerEvents.isAllied(a, b), "alliance mutation requires tracked public API");
            Set<String> saved = AllianceSaveData.getInstance(h.getLevel()).snapshot().get(a);
            rejected = false;
            try { saved.clear(); } catch (UnsupportedOperationException expected) { rejected = true; }
            h.assertTrue(rejected, "persisted snapshot also protects internal graph");
        } finally { AlliancesServerEvents.removeAlliance(a, b); }
        h.succeed();
    }
    @GameTest(template="dreamseekers_test_arena")
    public static void coopRebuildReplacesSavedGraph(GameTestHelper h) {
        verifyModeRebuild(h, false);
    }

    @GameTest(template="dreamseekers_test_arena")
    public static void scenarioRebuildReplacesSavedGraph(GameTestHelper h) {
        verifyModeRebuild(h, true);
    }

    private static void verifyModeRebuild(GameTestHelper h, boolean scenario) {
        AllianceSaveData data = AllianceSaveData.getInstance(h.getLevel());
        Map<String, Set<String>> before = data.snapshot();
        var players = new ArrayList<>(PlayerServerEvents.rtsPlayers);
        var roles = new ArrayList<>(ScenarioServerEvents.scenarioRoles);
        String a = "Api59ModeA", b = "Api59ModeB", enemy = "Api59ModeEnemy";
        try {
            PlayerServerEvents.rtsPlayers.clear();
            ScenarioServerEvents.scenarioRoles.clear();
            ScenarioRole first = new ScenarioRole(0); first.name = a; first.teamNumber = 1;
            ScenarioRole second = new ScenarioRole(1); second.name = b; second.teamNumber = 1;
            ScenarioRole third = new ScenarioRole(2); third.name = enemy; third.teamNumber = 2; third.isNpc = true;
            ScenarioServerEvents.scenarioRoles.addAll(java.util.List.of(first, second, third));
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewScenarioPlayer(a, Factions.VILLAGERS, 97002, 0));
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewScenarioPlayer(b, Factions.VILLAGERS, 97003, 1));
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewScenarioPlayer(enemy, Factions.MONSTERS, -97004, 2));
            AlliancesServerEvents.addAlliance(a, enemy);
            if (scenario) AlliancesServerEvents.applyScenarioAlliances();
            else AlliancesServerEvents.applyCoopAlliances();
            h.assertTrue(AlliancesServerEvents.isAllied(a, b) && !AlliancesServerEvents.isAllied(a, enemy),
                    "existing mode rules replace stale ordinary relationships and exclude opposing/NPC player");
            h.assertTrue(data.snapshot().equals(Map.of(a, Set.of(b), b, Set.of(a))),
                    "mode-rebuilt graph is persisted, not the earlier ordinary graph");
            PlayerServerEvents.rtsPlayers.clear(); ScenarioServerEvents.scenarioRoles.clear();
            if (scenario) AlliancesServerEvents.applyScenarioAlliances();
            else AlliancesServerEvents.applyCoopAlliances();
            h.assertTrue(data.snapshot().isEmpty(), "zero-pair mode rebuild also clears saved relationships");
        } finally {
            PlayerServerEvents.rtsPlayers.clear(); PlayerServerEvents.rtsPlayers.addAll(players);
            ScenarioServerEvents.scenarioRoles.clear(); ScenarioServerEvents.scenarioRoles.addAll(roles);
            AlliancesServerEvents.resetAllAlliances();
            before.forEach((owner, allies) -> allies.forEach(ally -> AlliancesServerEvents.addAlliance(owner, ally)));
        }
        h.succeed();
    }

}
