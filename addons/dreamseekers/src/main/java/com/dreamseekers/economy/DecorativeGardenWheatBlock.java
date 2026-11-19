package com.dreamseekers.economy;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import java.util.List;

/** Structural decoration, never a crop. The inert state preserves old wheat's exact match count. */
public final class DecorativeGardenWheatBlock extends Block {
    public static final IntegerProperty LEGACY_AGE=IntegerProperty.create("legacy_age",0,7);
    public DecorativeGardenWheatBlock() {
        super(BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(.2f).sound(SoundType.CROP).noLootTable());
        registerDefaultState(stateDefinition.any().setValue(LEGACY_AGE,7));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {builder.add(LEGACY_AGE);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return Block.box(0,0,0,16,13,16);
    }
    @Override public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP);
    }
    @Override public BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighbour,
            net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, net.minecraft.core.BlockPos neighbourPos) {
        return canSurvive(state,level,pos)?super.updateShape(state,direction,neighbour,level,pos,neighbourPos)
                :net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){return List.of();}
}
