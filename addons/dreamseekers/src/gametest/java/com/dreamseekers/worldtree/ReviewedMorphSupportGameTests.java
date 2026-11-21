// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.worldtree;

import com.solegendary.reignofnether.building.BuildingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;

/** Every retained-block prefix covers every attainable projected HP, including fractional damage. */
@GameTestHolder("dreamseekers_branches")
@PrefixGameTestTemplate(false)
public final class ReviewedMorphSupportGameTests {
    @GameTest(templateNamespace="dreamseekers_branches",template="branch_test_arena",batch="branches",timeoutTicks=30)
    public static void damagedV5MorphNeverLeavesLanternWithoutItsOwnSupport(GameTestHelper h){
        for(var target:new WorldTreeModel[]{WorldTreeModel.SHADOW_V5,WorldTreeModel.WIND_V5}){
            for(var rotation:Rotation.values()){
                var absolute=BuildingUtils.getAbsoluteBlockData(target.blocks(),h.getLevel(),h.absolutePos(BlockPos.ZERO),rotation);
                absolute.removeIf(b->b.getBlockState().isAir());
                absolute.sort(WorldTreeMorph.damageOrder(target));
                var retained=new HashSet<BlockPos>();int hanging=0;
                for(var block:absolute){
                    var at=block.getBlockPos();var state=block.getBlockState();
                    if(state.is(Blocks.LANTERN)){
                        boolean onCeiling=state.getValue(LanternBlock.HANGING);
                        h.assertTrue(retained.contains(onCeiling?at.above():at.below()),
                                target+" / "+rotation+" can lose the support of a retained lantern at "+at);
                        if(onCeiling)hanging++;
                    }
                    retained.add(at);
                }
                h.assertTrue(retained.size()==target.count&&hanging==(target==WorldTreeModel.SHADOW_V5?3:1),
                        "checked every solid, including every actual hanging light");
            }
        }
        h.succeed();
    }
}
