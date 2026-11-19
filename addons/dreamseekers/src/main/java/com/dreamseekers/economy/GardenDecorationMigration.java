package com.dreamseekers.economy;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Caller supplies only authoritative legacy template positions; this function never writes the world. */
public final class GardenDecorationMigration {
    private GardenDecorationMigration(){}
    public static BlockState translate(BlockState original, Block decoration) {
        return original.is(Blocks.WHEAT)?decoration.defaultBlockState()
                .setValue(DecorativeGardenWheatBlock.LEGACY_AGE,original.getValue(BlockStateProperties.AGE_7)):original;
    }
}
