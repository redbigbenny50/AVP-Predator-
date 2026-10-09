package com.predator.common.gameplay.block;

import com.mojang.serialization.MapCodec;
import com.predator.common.gameplay.block.entity.TripMineBlockEntity;
import com.predator.common.registry.init.PredatorBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A proximity mine that attaches to ANY surface — floor, ceiling, or any wall.
 * <h2>⚠⚠ THE PROPERTY IS "mount", DELIBERATELY NOT VANILLA'S "facing"</h2> {@link #MOUNT} is the direction the mine
 * points OUT from the surface it is stuck to: UP on a floor (the pose the geo is authored in), DOWN under a ceiling,
 * NORTH on the south face of a wall block, and so on. BLib's block renderer auto-rotates any block that carries
 * vanilla's {@code facing} property — and {@code Property.equals} matches by class AND NAME, so even a freshly created
 * {@code DirectionProperty.create("facing")} would trip it. That auto-rotation assumes a model authored facing NORTH
 * and turns UP by 90 degrees about X, which would lay a floor-authored mine on its side. Naming the property
 * differently keeps BLib's hands off; {@code TripMineRenderer} does the rotation itself, with the same table as
 * {@link #rotateFromUp}, so the hitbox and the picture cannot disagree.
 * <h2>Placement rules</h2> Sticks to the face you click, needs a sturdy face behind it, and pops off (drops itself)
 * when that support block is removed — the same rules as a button or a lever.
 */
public class TripMineBlock extends BaseEntityBlock {

    public static final MapCodec<TripMineBlock> CODEC = simpleCodec(TripMineBlock::new);

    public static final DirectionProperty MOUNT = DirectionProperty.create("mount");

    /** The floor pose: 8 wide, 2.2 tall, 7 deep, sat on the bottom face. Every other mount is this box rotated. */
    private static final VoxelShape FLOOR_SHAPE = Block.box(4, 0, 4, 12, 2.2, 11);

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (var mount : Direction.values()) {
            SHAPES.put(mount, rotateShape(FLOOR_SHAPE, mount));
        }
    }

    public TripMineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOUNT, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(MOUNT);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        var mount = context.getClickedFace();
        var state = defaultBlockState().setValue(MOUNT, mount);

        if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
            return state;
        }

        // ⚠ Clicked a face with nothing sturdy behind it (a slab edge, a fence): try the other faces, floor first,
        // so a click on an awkward block still places SOMEWHERE sensible instead of failing silently.
        for (var fallback : Direction.values()) {
            if (fallback == mount) {
                continue;
            }

            var candidate = defaultBlockState().setValue(MOUNT, fallback);

            if (candidate.canSurvive(context.getLevel(), context.getClickedPos())) {
                return candidate;
            }
        }

        return null;
    }

    @Override
    protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        var mount = state.getValue(MOUNT);
        var supportPos = pos.relative(mount.getOpposite());

        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, mount);
    }

    /** ⚠ Support gone → the mine drops as an item, like a lever. Explodes on nothing; that is a deliberate choice. */
    @Override
    protected @NotNull BlockState updateShape(
        @NotNull BlockState state,
        @NotNull Direction direction,
        @NotNull BlockState neighborState,
        @NotNull LevelAccessor level,
        @NotNull BlockPos pos,
        @NotNull BlockPos neighborPos
    ) {
        if (direction == state.getValue(MOUNT).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(MOUNT, rotation.rotate(state.getValue(MOUNT)));
    }

    @Override
    protected @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(MOUNT)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new TripMineBlockEntity(blockPos, blockState);
    }

    /** Whoever lays it owns it: they neither arm it nor take its blast. See {@code TripMineBlockEntity}. */
    @Override
    public void setPlacedBy(
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull BlockState state,
        @Nullable LivingEntity placer,
        @NotNull ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.getBlockEntity(pos) instanceof TripMineBlockEntity mine) {
            mine.setOwner(placer);
        }
    }

    @Override
    protected @NotNull VoxelShape getShape(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        return SHAPES.get(state.getValue(MOUNT));
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @NotNull Level level,
        @NotNull BlockState blockState,
        @NotNull BlockEntityType<T> blockEntityType
    ) {
        return TripMineBlock.createServerTicker(level, blockEntityType);
    }

    @Nullable
    protected static <T extends BlockEntity> BlockEntityTicker<T> createServerTicker(
        Level level,
        BlockEntityType<T> blockEntityType
    ) {
        return level.isClientSide
            ? null
            : createTickerHelper(
                blockEntityType,
                (BlockEntityType<? extends TripMineBlockEntity>) PredatorBlockEntityTypes.TRIP_MINE.get(),
                TripMineBlockEntity::serverTick
            );
    }

    @Override
    public boolean dropFromExplosion(@NotNull Explosion explosion) {
        return false;
    }

    /**
     * Rotates a point (block-local, 0..1) about the block centre so that the floor pose's UP ends up pointing along
     * {@code mount}. ⚠⚠ THIS IS THE ONE TABLE. {@code TripMineRenderer} applies the identical rotations with
     * {@code Axis}; if one changes, change both. Right-handed: {@code Rx(+90)} takes +Y to +Z.
     */
    public static double[] rotateFromUp(Direction mount, double x, double y, double z) {
        x -= 0.5;
        y -= 0.5;
        z -= 0.5;

        double rx;
        double ry;
        double rz;

        switch (mount) {
            case DOWN -> {
                rx = x;
                ry = -y;
                rz = -z;
            } // Rx(180)
            case NORTH -> {
                rx = x;
                ry = z;
                rz = -y;
            } // Rx(-90): +Y -> -Z
            case SOUTH -> {
                rx = x;
                ry = -z;
                rz = y;
            } // Rx(+90): +Y -> +Z
            case EAST -> {
                rx = y;
                ry = -x;
                rz = z;
            } // Rz(-90): +Y -> +X
            case WEST -> {
                rx = -y;
                ry = x;
                rz = z;
            } // Rz(+90): +Y -> -X
            default -> {
                rx = x;
                ry = y;
                rz = z;
            } // UP: authored pose
        }

        return new double[] { rx + 0.5, ry + 0.5, rz + 0.5 };
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction mount) {
        if (mount == Direction.UP) {
            return shape;
        }

        var rotated = Shapes.empty();

        for (AABB box : shape.toAabbs()) {
            var a = rotateFromUp(mount, box.minX, box.minY, box.minZ);
            var b = rotateFromUp(mount, box.maxX, box.maxY, box.maxZ);

            rotated = Shapes.or(
                rotated,
                Shapes.box(
                    Math.min(a[0], b[0]),
                    Math.min(a[1], b[1]),
                    Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]),
                    Math.max(a[1], b[1]),
                    Math.max(a[2], b[2])
                )
            );
        }

        return rotated.optimize();
    }
}
