package com.predator.common.gameplay.entity.living.yautja.ai;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Oct 8 - WHERE TO START CLIMBING a structure the prey is standing on (a pillar with a platform, a tower, a cliff).
 * <h2>THE TESTER REPORT</h2> "once they approach the stone pillar [they are] not being able to piece together that it
 * was a surface that would lead to getting closer to the player ... stand underneath the platform ... unable to
 * recognize its further connected to the ensuing pillar". The chase aimed at a spot beside the prey ON the platform,
 * which the yautja cannot path to, so it went to the nearest point - under the overhang - and stood there.
 * <h2>WHAT THIS FINDS</h2> A spot on the yautja's own level, beside a solid face that RUNS ALL THE WAY UP to the prey's
 * level, with a clear climbing lane in front of it (nothing overhead from the feet to above the top - so no overhang to
 * get stuck under) and room to mantle onto the top. The nearest such spot to the yautja wins, with a pull toward the
 * prey so it picks the side the prey is on. Walking there puts it against the wall with the target above, which is
 * exactly when YautjaClimb grabs on - and the lane it chose is the one that reaches the top.
 * <h2>COST</h2> One search per yautja per {@value #CACHE_TICKS} ticks, only while its prey is well above it; reused
 * while the prey has not moved more than a few blocks. Columns are rejected on their cheapest test first.
 */
public final class ClimbStart {

    /** How far around the prey's column to look, in blocks. */
    private static final int SEARCH_RADIUS = 12;

    /** How far above or below the yautja's feet a start spot may be. */
    private static final int LEVEL_TOLERANCE = 3;

    /** Highest climb considered, in blocks. */
    private static final int MAX_CLIMB = 48;

    /** Clear blocks needed above the top of the face to mantle onto it (the yautja is about three blocks tall). */
    private static final int MANTLE_ROOM = 3;

    private static final int CACHE_TICKS = 40;

    private record Cached(
        @Nullable Vec3 start,
        BlockPos preyPos,
        int tick
    ) {}

    /** Server thread only; entries die with the yautja. */
    private static final Map<Yautja, Cached> CACHE = new WeakHashMap<>();

    private ClimbStart() {}

    /** {@return the spot to walk to before climbing toward the prey, or null when nothing climbable reaches it} */
    public static @Nullable Vec3 find(Yautja yautja, LivingEntity prey) {
        var preyPos = prey.blockPosition();
        var cached = CACHE.get(yautja);

        if (
            cached != null
                && yautja.tickCount - cached.tick() < CACHE_TICKS
                && cached.preyPos().distManhattan(preyPos) <= 3
        ) {
            return cached.start();
        }

        var start = search(yautja, prey);
        CACHE.put(yautja, new Cached(start, preyPos, yautja.tickCount));
        return start;
    }

    private static @Nullable Vec3 search(Yautja yautja, LivingEntity prey) {
        var level = yautja.level();
        var feetY = yautja.getBlockY();
        var preyY = prey.getBlockY();
        var centre = prey.blockPosition();
        Vec3 best = null;
        var bestScore = Double.MAX_VALUE;

        for (var dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (var dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (var dy = -LEVEL_TOLERANCE; dy <= LEVEL_TOLERANCE; dy++) {
                    var spot = new BlockPos(centre.getX() + dx, feetY + dy, centre.getZ() + dz);

                    if (!isStandable(level, spot)) {
                        continue;
                    }

                    for (var side : Direction.Plane.HORIZONTAL) {
                        if (!leadsUpToPrey(level, spot, side, preyY)) {
                            continue;
                        }

                        var here = Vec3.atBottomCenterOf(spot);
                        var score = here.distanceTo(yautja.position()) + 0.5 * here.distanceTo(prey.position());

                        if (score < bestScore) {
                            bestScore = score;
                            best = here;
                        }

                        break;
                    }
                }
            }
        }

        return best;
    }

    /** Solid floor and room for the standing yautja. */
    static boolean isStandable(Level level, BlockPos feet) {
        var floor = feet.below();

        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }

        for (var height = 0; height < 3; height++) {
            var pos = feet.above(height);

            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * {@return whether the face on this side of the spot runs up to the prey's level, with an open lane in front of it
     * and room to mantle at the top}
     */
    private static boolean leadsUpToPrey(Level level, BlockPos feet, Direction side, int preyY) {
        var wallBase = feet.relative(side);

        // Grippable at chest height first - the cheapest rejection.
        var chest = wallBase.above(1);

        if (!level.getBlockState(chest).isFaceSturdy(level, chest, side.getOpposite())) {
            return false;
        }

        // Follow the face up until it ends.
        var top = -1;

        for (var height = 1; height <= MAX_CLIMB; height++) {
            var pos = wallBase.above(height);

            if (!level.getBlockState(pos).isFaceSturdy(level, pos, side.getOpposite())) {
                top = height - 1;
                break;
            }
        }

        // It must reach at least the floor the prey stands on.
        if (top < 0 || wallBase.getY() + top < preyY - 1) {
            return false;
        }

        // The lane in front of the face is clear all the way up: no overhang above the climber.
        for (var height = 0; height <= top + 2; height++) {
            var pos = feet.above(height);

            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }

        // Room to mantle onto the top.
        for (var height = 1; height <= MANTLE_ROOM; height++) {
            var pos = wallBase.above(top + height);

            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
