package com.dreamseekers.worldtree;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;
/** Original static ten-box sculpture: no EntityBlock, tick, use, entity spawn or loot behavior. */
public final class MeltdreamCrownBlock extends Block {
    private static final VoxelShape SHAPE=Shapes.or(Block.box(5,0,5,11,11,11),
        Block.box(6,11,6,10,14,10),
        Block.box(7,14,7,9,16,9),
        Block.box(1,0,6,4,7,9),
        Block.box(2,7,7,3,9,8),
        Block.box(12,0,8,15,9,11),
        Block.box(13,9,9,14,11,10),
        Block.box(6,0,1,9,8,4),
        Block.box(7,8,2,8,10,3),
        Block.box(7,0,12,10,6,15));
    public MeltdreamCrownBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK)
        .strength(1.5f).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s->10)
        .pushReaction(PushReaction.BLOCK).noLootTable());}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
}
