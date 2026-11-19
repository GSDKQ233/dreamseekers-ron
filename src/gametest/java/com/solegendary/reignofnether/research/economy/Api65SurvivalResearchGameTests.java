package com.solegendary.reignofnether.research.economy;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.survival.spawners.MonsterWaveSpawner;
import com.solegendary.reignofnether.survival.spawners.PiglinWaveSpawner;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** Direct existing upgrade service used by registered spawnMonsterWave/spawnPiglinWave.
 * API64-compatible; no new helper required, no artificial entity spawn failure. */
@GameTestHolder("api65_survival") @PrefixGameTestTemplate(false)
public final class Api65SurvivalResearchGameTests {
    @SuppressWarnings("unchecked") private static <T> List<T> state(String name)throws ReflectiveOperationException {
        Field f=ResearchServerEvents.class.getDeclaredField(name);f.setAccessible(true);return (List<T>)f.get(null);
    }
    @GameTest(templateNamespace="api65_survival",template="ore_mine_test_arena",batch="api65_survival_tier6",timeoutTicks=120)
    public static void monsterSixthTierGrantsSlimeNotMagmaAndPiglinRetainsMagma(GameTestHelper h)throws Exception {
        List<Pair<String,ResourceLocation>> researches=state("researchItems");
        List<Pair<String,String>> cheats=state("cheatItems");
        var oldResearch=new ArrayList<>(researches);var oldCheats=new ArrayList<>(cheats);
        String owner=SurvivalServerEvents.ENEMY_OWNER_NAME;
        try {
            ResearchServerEvents.removeAllResearchFor(owner);ResearchServerEvents.removeAllCheatsFor(owner);
            MonsterWaveSpawner.checkAndApplyUpgrades(5);
            h.assertTrue(ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_SPIDER_WEBS)
                    &&!ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_SLIME_CONVERSION)
                    &&!ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_CUBE_MAGMA),
                    "fifth-tier monster control has webs but neither sixth-tier elemental upgrade");
            MonsterWaveSpawner.checkAndApplyUpgrades(6);
            h.assertTrue(ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_SLIME_CONVERSION)
                    &&!ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_CUBE_MAGMA),
                    "registered monster sixth-tier upgrade grants actual Slime Conversion, not piglin Cube Magma");
            int count=researches.size();MonsterWaveSpawner.checkAndApplyUpgrades(6);
            h.assertTrue(researches.size()==count,"repeated monster upgrade service adds no duplicate research rows");
            PiglinWaveSpawner.checkAndApplyUpgrades(6);
            h.assertTrue(ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_SLIME_CONVERSION)
                    &&ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_CUBE_MAGMA),
                    "real piglin sixth tier still grants Cube Magma without erasing monster upgrade");
        } finally {
            researches.clear();researches.addAll(oldResearch);cheats.clear();cheats.addAll(oldCheats);ResearchServerEvents.saveResearch();
        }
        h.succeed();
    }
}
