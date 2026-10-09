package com.predator.common.gameplay.block;

import com.mojang.serialization.MapCodec;
import com.predator.Predator;
import com.predator.common.gameplay.block.entity.GauntletBlockEntity;
import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import com.predator.common.gameplay.menu.GauntletContents;
import com.predator.common.network.packet.S2COpenGauntletDisarmPayload;
import com.predator.common.registry.init.PredatorBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A gauntlet set down on the ground. Holds the exact ItemStack — contents, cloak device, arming state, countdown — and
 * gives it back untouched when picked up.
 * <h2>Interaction, as ruled</h2>
 * <ul>
 * <li>Unarmed: right-click picks it up into the main inventory (never the offhand).</li>
 * <li>Counting: right-click opens the disarm screen; shift + right-click picks it up still counting.</li>
 * <li>Mining it (hardness 40 — ~10 s with iron): the contents spill, the gauntlet itself is destroyed, no blast.
 * Blast-resistant and immovable, so mining is the only way through.</li>
 * </ul>
 */
public class GauntletBlock extends BaseEntityBlock {

    public static final MapCodec<GauntletBlock> CODEC = simpleCodec(GauntletBlock::new);

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 4, 13);

    public GauntletBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new GauntletBlockEntity(pos, state);
    }

    @Override
    protected @NotNull VoxelShape getShape(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull Player player,
        @NotNull BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof GauntletBlockEntity entity) || entity.getGauntlet().isEmpty()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        var gauntlet = entity.getGauntlet();

        if (GauntletSelfDestruct.isCounting(gauntlet)) {
            if (!player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    Predator.MOD.networking()
                        .sendToClient(
                            serverPlayer,
                            new S2COpenGauntletDisarmPayload(
                                pos.asLong(),
                                GauntletSelfDestruct.code(gauntlet),
                                GauntletSelfDestruct.deadline(gauntlet)
                            )
                        );
                }

                return InteractionResult.CONSUME;
            }
        }

        // Pick up: unarmed on any right-click, counting only with shift. Into the main inventory, never the offhand.
        var taken = entity.takeGauntlet();

        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }

        level.removeBlock(pos, false);

        return InteractionResult.CONSUME;
    }

    /**
     * Mined, blown up, whatever: if the stack is still here, it is destroyed — contents out, gauntlet gone, no blast.
     */
    @Override
    protected void onRemove(
        @NotNull BlockState state,
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull BlockState newState,
        boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GauntletBlockEntity entity) {
            var gauntlet = entity.getGauntlet();

            if (!gauntlet.isEmpty() && level instanceof ServerLevel serverLevel) {
                GauntletSelfDestruct.forget(serverLevel, gauntlet);

                var contents = new GauntletContents(gauntlet);

                for (var slot = 0; slot < GauntletContents.SIZE; slot++) {
                    var stack = contents.getItem(slot);

                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    }
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean dropFromExplosion(@NotNull Explosion explosion) {
        return false;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @NotNull Level level,
        @NotNull BlockState state,
        @NotNull BlockEntityType<T> type
    ) {
        return level.isClientSide
            ? null
            : createTickerHelper(type, PredatorBlockEntityTypes.GAUNTLET.get(), GauntletBlockEntity::serverTick);
    }
}
