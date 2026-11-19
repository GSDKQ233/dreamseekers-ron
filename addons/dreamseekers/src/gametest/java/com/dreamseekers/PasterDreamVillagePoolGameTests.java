// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Checks the actual registry after the original third-party startup event, without calling it again. */
@GameTestHolder("api64_paster_village")
@PrefixGameTestTemplate(false)
public final class PasterDreamVillagePoolGameTests {
    private static final ResourceLocation COTTAGE = new ResourceLocation("pasterdream", "desert_cottage_0");

    @SuppressWarnings("unchecked")
    @GameTest(template = "dreamseekers_test_arena", timeoutTicks = 20)
    public static void originalStartupRegistersOneCottageWithMatchingRawAndExpandedWeight(GameTestHelper h)
            throws ReflectiveOperationException {
        var pool = h.getLevel().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                .get(new ResourceLocation("minecraft", "village/desert/houses"));
        h.assertTrue(pool != null, "actual desert village registry pool exists");
        var expanded = (List<StructurePoolElement>) ObfuscationReflectionHelper
                .findField(StructureTemplatePool.class, "f_210560_").get(pool);
        var raw = (List<Pair<StructurePoolElement, Integer>>) ObfuscationReflectionHelper
                .findField(StructureTemplatePool.class, "f_210559_").get(pool);
        int copies = 0, rawEntries = 0, rawWeight = 0;
        for (var element : expanded) {
            if (isCottage(element)) {
                copies++;
                h.assertTrue(element.getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING,
                        "native cottage retains TERRAIN_MATCHING projection");
            }
        }
        for (var entry : raw) {
            if (isCottage(entry.getFirst())) {
                rawEntries++;
                rawWeight += entry.getSecond();
            }
        }
        h.assertTrue(copies == 200, "expanded cottage weight is exactly 200, actual=" + copies);
        h.assertTrue(rawEntries == 1 && rawWeight == 200,
                "one raw cottage entry weight 200, entries=" + rawEntries + ", weight=" + rawWeight);
        h.assertTrue(expanded.size() == raw.stream().mapToInt(Pair::getSecond).sum(),
                "whole pool raw and expanded weights remain consistent");
        h.succeed();
    }

    private static boolean isCottage(StructurePoolElement element) throws IllegalAccessException {
        if (!(element instanceof SinglePoolElement)) return false;
        var template = (Either<?, ?>) ObfuscationReflectionHelper
                .findField(SinglePoolElement.class, "f_210411_").get(element);
        return template.left().filter(COTTAGE::equals).isPresent();
    }
}
