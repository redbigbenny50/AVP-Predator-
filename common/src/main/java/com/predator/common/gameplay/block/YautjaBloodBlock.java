package com.predator.common.gameplay.block;

import com.mojang.serialization.MapCodec;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * A splash of yautja blood — the wounded Hunter's trail.
 * <ul>
 * <li>[stated] "neon green like a glowstick and it glows" — drawn at full brightness (emissive rendering, the magma
 * block's switch) but with a light level of 0: [stated] "treat it like a glow layer so its bright but it doesnt give
 * off light itself".</li>
 * <li>[stated] "like cherry blossom flowers on the ground in thickness" — a flat, petal-thin layer with no collision,
 * on floors, walls or ceilings (the trail climbs cliffs), so it is a face-attached block like a button.</li>
 * <li>[stated] three textures that "will rotate when placed too so its random and rotated random" — {@link #VARIANT}
 * picks the texture and the facing turns it.</li>
 * <li>[stated] "can be bottled" — an empty glass bottle collects it.</li>
 * <li>It dries up {@link #LIFETIME_TICKS} after it is spilled (5 minutes, the time the Hunter waits).</li>
 * </ul>
 * It drops nothing when broken.
 */
public class YautjaBloodBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final MapCodec<YautjaBloodBlock> CODEC = simpleCodec(YautjaBloodBlock::new);

    /** Which of the three textures. */
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 1, 3);

    /** 5 minutes. */
    public static final int LIFETIME_TICKS = 6000;

    private static final VoxelShape FLOOR = Block.box(0.0, 0.0, 0.0, 16.0, 0.25, 16.0);

    private static final VoxelShape CEILING = Block.box(0.0, 15.75, 0.0, 16.0, 16.0, 16.0);

    private static final VoxelShape NORTH_WALL = Block.box(0.0, 0.0, 15.75, 16.0, 16.0, 16.0);

    private static final VoxelShape SOUTH_WALL = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 0.25);

    private static final VoxelShape WEST_WALL = Block.box(15.75, 0.0, 0.0, 16.0, 16.0, 16.0);

    private static final VoxelShape EAST_WALL = Block.box(0.0, 0.0, 0.0, 0.25, 16.0, 16.0);

    public YautjaBloodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.FLOOR).setValue(FACING, Direction.NORTH).setValue(VARIANT, 1));
    }

    @Override
    protected @NotNull MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, VARIANT);
    }

    /**
     * {@return a splash for this spot} — on the floor if it has one, otherwise on a wall beside it, otherwise null. A
     * random texture and, on a floor, a random turn.
     */
    public static BlockState splashFor(Level level, BlockPos pos, RandomSource random) {
        var block = com.predator.common.registry.init.PredatorBlocks.YAUTJA_BLOOD.get();
        var variant = 1 + random.nextInt(3);
        var floorState = block.defaultBlockState()
            .setValue(FACE, AttachFace.FLOOR)
            .setValue(FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
            .setValue(VARIANT, variant);

        if (floorState.canSurvive(level, pos)) {
            return floorState;
        }

        for (var side : Direction.Plane.HORIZONTAL.shuffledCopy(random)) {
            // FACING on a wall is the way it faces OUT, away from the block it is stuck to.
            var wallState = block.defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, side).setValue(VARIANT, variant);

            if (wallState.canSurvive(level, pos)) {
                return wallState;
            }
        }

        return null;
    }

    @Override
    protected @NotNull VoxelShape getShape(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> switch (state.getValue(FACING)) {
                case NORTH -> NORTH_WALL;
                case SOUTH -> SOUTH_WALL;
                case WEST -> WEST_WALL;
                default -> EAST_WALL;
            };
        };
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected void onPlace(
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull BlockState oldState,
        boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, LIFETIME_TICKS);
        }
    }

    /** It has dried up. */
    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        level.removeBlock(pos, false);
    }

    /** An empty bottle collects it. */
    @Override
    protected @NotNull ItemInteractionResult useItemOn(
        @NotNull ItemStack stack,
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull Player player,
        @NotNull InteractionHand hand,
        @NotNull BlockHitResult hit
    ) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(PredatorItems.YAUTJA_BLOOD_BOTTLE.get())));
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
