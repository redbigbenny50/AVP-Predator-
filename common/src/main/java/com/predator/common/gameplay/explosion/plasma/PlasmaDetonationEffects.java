package com.predator.common.gameplay.explosion.plasma;

import com.blib.api.common.explosion.v1.Explosion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * What the gauntlet's plasma detonation does to one sampled block. A deliberately simpler cousin of avp_human's nuclear
 * effects: no fallout palette, no ash, no glass. Inside the sphere everything is air; where the crater floor meets
 * intact ground, fire is seeded; at the rim a thin band scorches to blackstone so the edge reads as burnt rather than
 * cut. [stated] "the crater isnt as deep, fire in the crater, no fall off no radiation." What the rim scorches each
 * block into is data-pack JSON since Oct 2 - see {@link PlasmaConversions}.
 */
public final class PlasmaDetonationEffects {

    /** Normalized ellipsoid distance beyond which a block is only scorched, not removed. */
    public static double RIM_BAND = 0.93;

    /** Chance that a crater-floor block (air with intact ground beneath) becomes fire instead. */
    public static float FLOOR_FIRE_CHANCE = 0.18F;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private boolean geometryResolved;

    private int centerX;

    private int centerY;

    private int centerZ;

    private int radiusX;

    private int radiusZ;

    private int radiusYUp;

    private int radiusYDown;

    private double yScaleFactor;

    public void apply(Explosion explosion, BlockPos pos) {
        var level = explosion.level();

        resolveGeometry(explosion);

        var x = pos.getX() - centerX;
        var y = pos.getY() - centerY;
        var z = pos.getZ() - centerZ;
        var horizontal = (double) (x * x) / (radiusX * radiusX) + (double) (z * z) / (radiusZ * radiusZ);
        var verticalRadius = y < 0 ? radiusYDown : radiusYUp;
        var distance = horizontal + (double) (y * y) / (verticalRadius * verticalRadius) * yScaleFactor;
        var state = level.getBlockState(pos);

        if (state.isAir()) {
            return;
        }

        if (distance > RIM_BAND) {
            // [stated] Oct 2: WHAT a scorched block becomes comes from data-pack JSON (PlasmaConversions) - unlisted
            // blocks still go to blackstone. WHICH blocks scorch (solid ones, half of them) stays here, unchanged.
            if (state.isSolidRender(level, pos) && level.random.nextFloat() < 0.5F) {
                level.setBlock(pos, PlasmaConversions.convert(state, level.random).defaultBlockState(), FLAGS);
            }

            return;
        }

        // ⚠ FIRE ON THE CRATER FLOOR. The block below is outside the sphere only at the floor, and fire needs something
        // solid to sit on; the same test picks the floor out for free.
        var below = pos.below();

        if (
            !insideSphere(below) && level.getBlockState(below).isSolidRender(level, below)
                && level.random.nextFloat() < FLOOR_FIRE_CHANCE
        ) {
            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), FLAGS);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            wakeAdjacentFluid(level, pos);
        }

        if (level.random.nextInt(48) == 0) {
            spawnParticles(level, pos);
        }
    }

    /**
     * ⚠⚠ THE CARVE USES NO NEIGHBOUR UPDATES, SO WATER NEVER LEARNS THE HOLE EXISTS. {@link #FLAGS} deliberately omits
     * {@code UPDATE_NEIGHBORS} — a 48-block-wide carve that updated every neighbour would stall the server — but that
     * also left an underwater crater as a permanent air pocket with an ocean sitting on top of it. Ticking just the
     * fluids that touch a newly emptied block is enough: vanilla's flow then cascades on its own, filling the crater
     * over the following seconds, and nothing is ticked that was not already at the boundary.
     */
    private static void wakeAdjacentFluid(ServerLevel level, BlockPos pos) {
        for (var direction : Direction.values()) {
            var neighbor = pos.relative(direction);
            var fluid = level.getFluidState(neighbor);

            if (!fluid.isEmpty()) {
                level.scheduleTick(neighbor, fluid.getType(), fluid.getType().getTickDelay(level));
            }
        }
    }

    private boolean insideSphere(BlockPos pos) {
        var x = pos.getX() - centerX;
        var y = pos.getY() - centerY;
        var z = pos.getZ() - centerZ;
        var verticalRadius = y < 0 ? radiusYDown : radiusYUp;

        return (double) (x * x) / (radiusX * radiusX) + (double) (z * z) / (radiusZ * radiusZ)
            + (double) (y * y) / (verticalRadius * verticalRadius) * yScaleFactor <= RIM_BAND;
    }

    private static void spawnParticles(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.02);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.6, 0.6, 0.6, 0.05);
    }

    private void resolveGeometry(Explosion explosion) {
        if (geometryResolved) {
            return;
        }

        var config = explosion.config();
        var centerPos = config.centerBlockPosition();

        centerX = centerPos.getX();
        centerY = centerPos.getY();
        centerZ = centerPos.getZ();
        radiusX = Math.max(1, config.largestRadius(Direction.Axis.X));
        radiusZ = Math.max(1, config.largestRadius(Direction.Axis.Z));
        radiusYDown = Math.max(1, config.radius(Direction.DOWN));
        radiusYUp = Math.max(1, config.radius(Direction.UP));
        yScaleFactor = (radiusX + radiusZ) / 2.0 / Math.max(radiusYUp, radiusYDown);
        geometryResolved = true;
    }
}
