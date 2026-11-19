package com.dreamseekers;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.faction.RandomStartFactionSelection;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@GameTestHolder("api59_random_start")
@PrefixGameTestTemplate(false)
public final class Api59RandomFactionGameTests {
    @GameTest(templateNamespace = "api59_random_start", template = "dreamseekers_test_arena", timeoutTicks = 100)
    public static void actualRegisteredRandomRosterIncludesDreamseekers(GameTestHelper helper) {
        var candidates = RandomStartFactionSelection.candidates(Factions.PLAYABLE_FACTIONS,
                Factions::getFaction, faction ->
                        ForgeRegistries.ENTITY_TYPES.containsKey(faction.workerEntityType)
                                && ForgeRegistries.ENTITY_TYPES.containsKey(faction.scoutEntityType)
                                && ReignOfNetherRegistries.BUILDING.containsKey(faction.capitolBuilding));
        var expected = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS,
                DreamseekersMod.DREAMSEEKERS);
        helper.assertTrue(candidates.size() == 4 && candidates.containsAll(expected),
                "actual registered core+addon roster has exactly four start-capable factions");
        helper.assertTrue(!candidates.contains(Factions.NEUTRAL) && !candidates.contains(Factions.NONE)
                && !candidates.contains(Factions.RANDOM), "reserved identities excluded");
        for (int i = 0; i < candidates.size(); i++) {
            final int index = i;
            helper.assertTrue(RandomStartFactionSelection.select(candidates, bound -> {
                helper.assertTrue(bound == 4, "uniform draw bound uses full actual roster");
                return index;
            }) == candidates.get(i), "each actual registered faction is deterministically selectable");
        }
        helper.assertTrue(!Factions.CLASSIC_FACTIONS.contains(DreamseekersMod.DREAMSEEKERS.key),
                "ordinary random selection does not change the classic panorama/wave roster");
        helper.succeed();
    }
}
