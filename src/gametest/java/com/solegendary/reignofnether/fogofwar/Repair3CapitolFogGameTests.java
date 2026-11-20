package com.solegendary.reignofnether.fogofwar;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("api65_repair3_scenario")
@PrefixGameTestTemplate(false)
public class Repair3CapitolFogGameTests {
    @GameTest(templateNamespace="api65_repair3_scenario", template="ore_mine_test_arena", batch="repair3_fog_eligibility")
    public static void restoredSeatIsRevealedOnlyWhileActuallyWithoutCapitol(GameTestHelper h) {
        int limit = PlayerServerEvents.TICKS_TO_REVEAL;
        var p = RTSPlayer.getNewPlayer("Repair3Fog", Factions.VILLAGERS, 8351, 0, false, -1L, -1L);
        var noCapitol = Set.of(p.name);
        var data = new RTSPlayerSaveData();
        data.rtsPlayers.add(p);
        for (int ticks : new int[]{limit - 1, limit, limit + 1}) {
            p.ticksWithoutCapitol = ticks;
            var loaded = RTSPlayerSaveData.load(data.save(new CompoundTag())).rtsPlayers;
            h.assertTrue(loaded.size() == 1 && loaded.get(0).ticksWithoutCapitol == ticks, "real roster NBT roundtrip");
            var restored = FogOfWarServerEvents.revealedOwnersForMap(loaded, noCapitol, Set.of());
            h.assertTrue(restored.contains(p.name) == (ticks >= limit), "first computed fog mask does not require a new timer edge");
            h.assertTrue(!FogOfWarServerEvents.revealedOwnersForMap(loaded, noCapitol, noCapitol).contains(p.name),
                    "an existing or newly built capitol wins over the old timer");
            h.assertTrue(!FogOfWarServerEvents.revealedOwnersForMap(loaded, Set.of(), Set.of()).contains(p.name)
                            && !FogOfWarServerEvents.revealedOwnersForMap(List.of(), noCapitol, Set.of()).contains(p.name),
                    "zero buildings and a departed seat never retain a rule reveal");
        }
        h.succeed();
    }
}
