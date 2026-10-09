package com.predator.common.gameplay.block;

import com.mojang.serialization.MapCodec;
import com.predator.common.gameplay.block.entity.SkinnedCorpseBlockEntity;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A skinned body left behind by a yautja. A container: right-click opens it, mining it (hand or tool) spills everything
 * inside, and both make a fleshy sound. [stated] "this can count as a container. when mined either by hand or tool it
 * drops all stored items and it makes a fleshy sound. it makes the sound when opening it too."
 * <h2>Pose</h2> [stated] "hanging if possible if not then its on the ground."
 * <ul>
 * <li>{@link #HANGING}: strung upside down from the block above — feet at the ceiling, the body hanging down into the
 * block below. Only placed where the block above has a solid underside and two blocks of open air below it.</li>
 * <li>Otherwise lying on the ground, centred on this block.</li>
 * </ul>
 * The renderer reads both properties; the block itself is one block and nothing more.
 * <h2>⚠ The orientation property is NOT called "facing"</h2> BLib's block renderer auto-rotates any block carrying
 * vanilla's facing property, assuming a model that faces north. This model hangs and lies down, so the renderer owns
 * every rotation — same reasoning as the trip mine's {@code mount}.
 * <h2>Nothing about it is solid</h2> No collision at all, so a body hanging in a doorway never traps anyone. It still
 * has a selection shape, so it can be clicked and mined. Immovable by pistons, which would otherwise duplicate or
 * delete the contents.
 */
public class SkinnedCorpseBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

    public static final MapCodec<SkinnedCorpseBlock> CODEC = simpleCodec(SkinnedCorpseBlock::new);

    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");

    public static final DirectionProperty ORIENTATION = DirectionProperty.create(
        "orientation",
        Direction.Plane.HORIZONTAL
    );

    /**
     * Floating on the water's surface — [stated] Oct 4: "for corpses in water can we make them float? water logged on
     * at the waters surface". Only ever placed in a water source with open air above, and always {@link #WATERLOGGED}.
     * The renderer lifts the lying pose to the surface; a floating corpse never falls (it is on water, not on a floor).
     */
    public static final BooleanProperty FLOATING = BooleanProperty.create("floating");

    public static final BooleanProperty WATERLOGGED = net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

    /** Floating: the click box sits at the surface, where the body is drawn. */
    private static final VoxelShape FLOATING_SHAPE = Block.box(0, 11, 0, 16, 15, 16);

    private static final VoxelShape HANGING_SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    private static final VoxelShape GROUND_SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public SkinnedCorpseBlock(Properties properties) {
        super(properties);
        registerDefaultState(
            stateDefinition.any()
                .setValue(HANGING, false)
                .setValue(ORIENTATION, Direction.NORTH)
                .setValue(FLOATING, false)
                .setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANGING, ORIENTATION, FLOATING, WATERLOGGED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SkinnedCorpseBlockEntity(pos, state);
    }

    @Override
    protected @NotNull VoxelShape getShape(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        if (state.getValue(FLOATING)) {
            return FLOATING_SHAPE;
        }

        return state.getValue(HANGING) ? HANGING_SHAPE : GROUND_SHAPE;
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        return Shapes.empty();
    }

    @Override
    protected @NotNull net.minecraft.world.level.material.FluidState getFluidState(@NotNull BlockState state) {
        return state.getValue(WATERLOGGED)
            ? net.minecraft.world.level.material.Fluids.WATER.getSource(false)
            : super.getFluidState(state);
    }

    /**
     * ⚠ The water under a floating corpse cannot be bucketed out: it would leave the body floating on nothing. The
     * water put into a dry corpse with a bucket can be taken back as normal.
     */
    @Override
    public net.minecraft.world.item.@NotNull ItemStack pickupBlock(
        @Nullable Player player,
        @NotNull LevelAccessor level,
        @NotNull BlockPos pos,
        @NotNull BlockState state
    ) {
        if (state.getValue(FLOATING)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        return net.minecraft.world.level.block.SimpleWaterloggedBlock.super.pickupBlock(player, level, pos, state);
    }

    /** {@return whether a corpse at this position could hang from the block above} */
    public static boolean canHangAt(BlockGetter level, BlockPos pos) {
        var above = pos.above();

        return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull Player player,
        @NotNull BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof SkinnedCorpseBlockEntity corpse) {
            player.openMenu(corpse);
            level.playSound(null, pos, PredatorSoundEvents.CORPSE_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * The ceiling it hangs from is gone: it drops to the lying pose instead of floating. Nothing is lost — only the
     * pose changes.
     */
    @Override
    protected @NotNull BlockState updateShape(
        @NotNull BlockState state,
        @NotNull Direction direction,
        @NotNull BlockState neighborState,
        @NotNull LevelAccessor level,
        @NotNull BlockPos pos,
        @NotNull BlockPos neighborPos
    ) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(
                pos,
                net.minecraft.world.level.material.Fluids.WATER,
                net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level)
            );
        }

        // Floating: it rests on the water, not on a floor, so what is below it never matters.
        if (state.getValue(FLOATING)) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }

        if (direction == Direction.UP && state.getValue(HANGING) && !canHangAt(level, pos)) {
            // ⚠ It was hanging two blocks up: dropping to the lying pose in place would leave it lying on air. The
            // tick below drops it to the ground.
            level.scheduleTick(pos, this, 2);
            return state.setValue(HANGING, false);
        }

        if (direction == Direction.DOWN && !state.getValue(HANGING) && !hasFloor(level, pos)) {
            level.scheduleTick(pos, this, 2);
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private static boolean hasFloor(BlockGetter level, BlockPos pos) {
        var below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** How far a corpse with nothing under it falls before it is given up as lost in the void. */
    private static final int MAX_FALL = 64;

    /**
     * Lying on nothing — its ceiling or its floor gone: it falls to the first ground below, contents and all, so a
     * corpse never floats. Nothing below at all (the void): it comes apart and its contents spill where it was.
     */
    @Override
    protected void tick(
        @NotNull BlockState state,
        net.minecraft.server.level.@NotNull ServerLevel level,
        @NotNull BlockPos pos,
        net.minecraft.util.@NotNull RandomSource random
    ) {
        if (state.getValue(HANGING) || state.getValue(FLOATING) || hasFloor(level, pos)) {
            return;
        }

        BlockPos landing = null;
        var landsFloating = false;

        for (var depth = 1; depth <= MAX_FALL; depth++) {
            var candidate = pos.below(depth);

            if (!level.isInWorldBounds(candidate)) {
                break;
            }

            var candidateState = level.getBlockState(candidate);

            // Falls onto water: it floats there instead of sinking or coming apart.
            if (
                candidateState.getFluidState().isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)
                    && candidateState.canBeReplaced()
            ) {
                landing = candidate;
                landsFloating = true;
                break;
            }

            if (!candidateState.canBeReplaced() || !candidateState.getFluidState().isEmpty()) {
                break;
            }

            if (hasFloor(level, candidate)) {
                landing = candidate;
                break;
            }
        }

        if (landing == null || !(level.getBlockEntity(pos) instanceof SkinnedCorpseBlockEntity corpse)) {
            level.destroyBlock(pos, false);
            return;
        }

        // ⚠ Contents taken out FIRST, so removing the old block spills nothing; then the new one is filled.
        var contents = corpse.takeAll();

        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(landing, landsFloating ? state.setValue(FLOATING, true).setValue(WATERLOGGED, true) : state, Block.UPDATE_ALL);

        if (level.getBlockEntity(landing) instanceof SkinnedCorpseBlockEntity moved) {
            var centre = net.minecraft.world.phys.Vec3.atCenterOf(landing);

            for (var leftover : moved.fill(contents)) {
                Containers.dropItemStack(level, centre.x, centre.y, centre.z, leftover);
            }
        } else {
            for (var stack : contents) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    /**
     * Mined, blown up, burnt out from under it: the contents spill. The body itself drops 12 rotten flesh and 6 bones
     * through its loot table.
     */
    @Override
    protected void onRemove(
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull BlockState newState,
        boolean movedByPiston
    ) {
        // ⚠ No sound here: a player breaking it already gets the BREAK sound from SkinnedCorpseSoundType through
        // vanilla's block-break event, and playing it here as well doubled it.
        Containers.dropContentsOnDestroy(state, newState, level, pos);

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
