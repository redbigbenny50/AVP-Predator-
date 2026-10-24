package com.predator.common.gameplay.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * A full block with a different face on each side that turns to face whoever places it, like a furnace. The
 * {@code north} face in its model is the FRONT.
 * <p>
 * Rotation and mirroring come from HorizontalDirectionalBlock (checked in the 1.21.1 jar), so a structure placed
 * rotated turns these with it - which the village structures will rely on.
 */
public class PredatorFacingBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<PredatorFacingBlock> CODEC = simpleCodec(PredatorFacingBlock::new);

    public PredatorFacingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The front points back at the placer. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
