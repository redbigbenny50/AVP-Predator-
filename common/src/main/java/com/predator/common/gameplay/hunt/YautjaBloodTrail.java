package com.predator.common.gameplay.hunt;

import com.predator.common.gameplay.block.YautjaBloodBlock;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The trail of glowing blood a wounded Hunter leaves when it flees — [stated] "the trail runs from where it fled to
 * where it is, following a walking path where possible, otherwise placed intelligently a few blocks apart (up
 * walls/cliffs) so the player can see it."
 * <ol>
 * <li><b>Walking route.</b> The vanilla ground pathfinder plans a route from where it stands to where it is going. A
 * splash goes on every {@link #PATH_SPACING}th step of it.</li>
 * <li><b>Where no route reaches.</b> The rest of the way is sampled in a straight line every {@link #LINE_SPACING}
 * blocks, each splash dropped on the surface below that point. Where the ground jumps by more than a block between two
 * samples — a cliff or a wall — the face of the climb gets splashes up its side every two blocks, so the trail visibly
 * goes UP.</li>
 * <li><b>The end.</b> A small pool where it waits, so the end of the trail reads as an end.</li>
 * </ol>
 * Each splash is one {@link YautjaBloodBlock}: a random texture at a random turn, on the floor or, failing that, a
 * wall. Nothing is ever placed into water or over a block that is not freely replaceable.
 */
public final class YautjaBloodTrail {

    private static final int PATH_SPACING = 2;

    private static final double LINE_SPACING = 3.0;

    /** Blocks of the wall between splashes going up a climb. */
    private static final int CLIMB_SPACING = 2;

    /** The pool at the end. */
    private static final int POOL_SPLASHES = 4;

    private YautjaBloodTrail() {}

    public static void lay(ServerLevel level, Yautja hunter, Vec3 destination) {
        var target = BlockPos.containing(destination);
        var reached = hunter.blockPosition();

        // ⚠ The vanilla navigation, not the yautja's BLib navigator: this is planning only, nothing moves along it.
        var path = hunter.getNavigation().createPath(target, 1);

        if (path != null) {
            for (var i = 0; i < path.getNodeCount(); i += PATH_SPACING) {
                var node = path.getNodePos(i);
                splash(level, node);
                reached = node;
            }
        }

        // Whatever the route did not cover, in a line from where it stopped.
        if (path == null || !path.canReach()) {
            line(level, Vec3.atBottomCenterOf(reached), destination);
        }

        pool(level, target);
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 to) {
        var distance = from.distanceTo(to);
        var steps = Math.max(1, (int) Math.ceil(distance / LINE_SPACING));
        BlockPos previous = null;

        for (var step = 1; step <= steps; step++) {
            var point = from.lerp(to, (double) step / steps);
            var column = BlockPos.containing(point);
            var ground = surfaceNear(level, column, (int) Math.floor(point.y));

            if (ground == null) {
                continue;
            }

            if (previous != null && Math.abs(ground.getY() - previous.getY()) > 1) {
                climb(level, previous, ground);
            }

            splash(level, ground);
            previous = ground;
        }
    }

    /**
     * Up (or down) the face between two samples: splashes on the side of the higher column, facing the lower one, every
     * {@link #CLIMB_SPACING} blocks of height.
     */
    private static void climb(ServerLevel level, BlockPos a, BlockPos b) {
        var low = a.getY() < b.getY() ? a : b;
        var high = low == a ? b : a;
        var toward = Direction.getNearest(high.getX() - low.getX(), 0, high.getZ() - low.getZ());

        if (toward.getAxis().isVertical()) {
            return;
        }

        // The air column in front of the climb, on the low side, one step toward it.
        var face = new BlockPos(high.getX(), low.getY(), high.getZ()).relative(toward.getOpposite());

        for (var y = low.getY(); y < high.getY(); y += CLIMB_SPACING) {
            splash(level, face.atY(y));
        }
    }

    private static void pool(ServerLevel level, BlockPos centre) {
        var random = level.getRandom();
        splash(level, centre);

        for (var i = 0; i < POOL_SPLASHES; i++) {
            splash(level, centre.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1));
        }
    }

    /** {@return the first open spot standing on ground in this column, near the given height, or null} */
    private static BlockPos surfaceNear(ServerLevel level, BlockPos column, int nearY) {
        for (var dy = 0; dy <= 6; dy++) {
            for (var sign : new int[] { 1, -1 }) {
                var feet = column.atY(nearY + dy * sign);

                if (
                    level.getBlockState(feet).canBeReplaced() && level.getFluidState(feet).isEmpty()
                        && Block.isFaceFull(level.getBlockState(feet.below()).getCollisionShape(level, feet.below()), Direction.UP)
                ) {
                    return feet;
                }
            }
        }

        var top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());

        return Mth.abs(top - nearY) <= 12 ? column.atY(top) : null;
    }

    private static void splash(ServerLevel level, BlockPos pos) {
        var current = level.getBlockState(pos);

        if (
            !current.canBeReplaced() || !level.getFluidState(pos).isEmpty() || current.is(
                com.predator.common.registry.init.PredatorBlocks.YAUTJA_BLOOD.get()
            )
        ) {
            return;
        }

        var state = YautjaBloodBlock.splashFor(level, pos, level.getRandom());

        if (state != null) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }
}
