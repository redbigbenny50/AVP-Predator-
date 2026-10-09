package com.predator.common.gameplay.entity.living.yautja.path;

import com.predator.common.debug.PredatorPathDiagnostics;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The yautja's leaps: a standing jump that clears 4 blocks and a running jump that clears 7.
 * <h2>⚠⚠ Why this is a REACTION and not a pathfinding feature</h2> BLib's pathfinder has 43 features and not one of
 * them is a horizontal jump. Its movement vocabulary is {@code stepUp} / {@code stepDown} — vertical, bounded by step
 * height — plus the water set. <b>It cannot plan a route across a gap, so no amount of terrain-cost tuning will ever
 * produce a leap.</b> Forcing it by raising step height would be worse: that silently changes what counts as walkable
 * everywhere.
 * <p>
 * So this watches where the yautja is already walking and answers the gap when it arrives at one, the same shape as
 * avp_alien's short-wall jump. Routes still go AROUND water where they can — that is what the 8x water cost in
 * {@link YautjaPathing} is for — and this fires when the route runs out of dry ground anyway.
 * <h2>The impulses are simulated, not guessed</h2> Minecraft flight is {@code y += vy; vy = (vy - 0.08) * 0.98},
 * horizontal {@code vx *= 0.91} per tick. Running that forward from a {@value #JUMP_VELOCITY} upward impulse gives 14
 * ticks of air peaking at 1.84 blocks (vanilla's own jump is 0.42 and peaks at 1.25). Over that airtime,
 * {@value #STANDING_IMPULSE} carries exactly 4.00 blocks and {@value #RUNNING_IMPULSE} exactly 7.00 — his two figures.
 */
public final class YautjaJump {

    /** Upward impulse, shared by both jumps. Peaks at 1.84 blocks. */
    public static final double JUMP_VELOCITY = 0.52;

    /** Clears 4.00 blocks over the 14-tick airtime. */
    private static final double STANDING_IMPULSE = 0.491;

    /** Clears 7.00 blocks. Used when already running, which is when a hunter would commit to a long jump. */
    private static final double RUNNING_IMPULSE = 0.860;

    /** Above this ground speed the jump is a running one. Half the chase speed, so a stroll never counts. */
    private static final double RUNNING_SPEED_FRACTION = 0.5;

    /** Blocks. The longest gap it will attempt from a run. */
    private static final int MAX_RUNNING_GAP = 7;

    /** Blocks. The longest gap it will attempt from a standstill or a walk. */
    private static final int MAX_STANDING_GAP = 4;

    /**
     * ⚠⚠ TWO, NOT ONE, AND THIS IS THE FIX FOR "JUMPING AROUND". At 1 a single air column ahead — an ordinary two-deep
     * step in broken terrain, which jungle and hills are full of — read as a gap worth leaping, so the yautja hurled
     * itself several blocks at scenery it could simply have walked down. A gap you can cross by stepping into it is not
     * a gap.
     */
    private static final int MIN_GAP = 2;

    /** Ticks on the ground before it will consider another jump. */
    private static final int SETTLE_TICKS = 5;

    /** Ticks between jumps, so one stood beside a lake does not pogo. */
    private static final int COOLDOWN_TICKS = 30;

    /** How far ahead to look. One block: this reacts at the edge, it does not plan. */
    private static final double LOOKAHEAD_BLOCKS = 1.0;

    /** How far the landing may sit above or below the take-off. A leap is not a climb. */
    private static final int MAX_LANDING_STEP = 1;

    private YautjaJump() {
        throw new UnsupportedOperationException();
    }

    /**
     * Called once per server tick. Leaps when the yautja is walking into water or a drop with solid ground on the far
     * side, within the range its current speed allows.
     * <p>
     * ⚠ Deliberately silent about WHY it is heading at the gap. A stalking yautja is there because going round cost
     * more than 8x; a chasing one is following prey. Both should clear a stream rather than swim it.
     */
    public static void tick(Yautja yautja) {
        if (yautja.level().isClientSide || yautja.tickCount < yautja.getNextLeapTick()) {
            return;
        }

        if (!yautja.onGround() || yautja.isInWater() || yautja.isClimbing()) {
            return;
        }

        // ⚠ Settled on its feet, not merely touching ground this tick. Without it a landing yautja can clip the
        // ground for one tick, read the next drop as a gap and launch again — a chain of leaps across terrain
        // that only ever needed walking down.
        if (yautja.getTicksSinceLanding() < SETTLE_TICKS) {
            return;
        }

        var motion = yautja.getDeltaMovement();
        var heading = new Vec3(motion.x, 0.0, motion.z);

        if (heading.lengthSqr() < 1.0E-4) {
            return;
        }

        var direction = heading.normalize();
        var running = isRunning(yautja, heading);
        var maxGap = running ? MAX_RUNNING_GAP : MAX_STANDING_GAP;

        if (!isGapAhead(yautja, direction)) {
            return;
        }

        var landing = findLanding(yautja, direction, maxGap);

        if (landing < MIN_GAP) {
            return;
        }

        var impulse = running ? RUNNING_IMPULSE : STANDING_IMPULSE;

        yautja.setDeltaMovement(direction.x * impulse, JUMP_VELOCITY, direction.z * impulse);
        yautja.hasImpulse = true;
        yautja.setLeapCooldown(COOLDOWN_TICKS);

        // His exertion vocals: the long-jump grunt for a running leap, the climbing one for a standing hop.
        if (running) {
            com.predator.common.gameplay.entity.living.yautja.YautjaSounds.longJump(yautja);
        } else {
            com.predator.common.gameplay.entity.living.yautja.YautjaSounds.climb(yautja);
        }

        PredatorPathDiagnostics.onJump(yautja, running, landing);
    }

    /** {@return whether it is moving fast enough for this to be a running jump rather than a standing one} */
    private static boolean isRunning(Yautja yautja, Vec3 heading) {
        var chase = YautjaMovement.chaseBlocksPerTick(yautja.getAttributeValue(Attributes.MOVEMENT_SPEED));
        return heading.length() >= chase * RUNNING_SPEED_FRACTION;
    }

    /** Water, or nothing to stand on. Both are gaps; only the first is his stated case, but a ravine reads the same. */
    private static boolean isGapAhead(Yautja yautja, Vec3 direction) {
        var ahead = BlockPos.containing(
            yautja.getX() + direction.x * LOOKAHEAD_BLOCKS,
            yautja.getY(),
            yautja.getZ() + direction.z * LOOKAHEAD_BLOCKS
        );

        if (!yautja.level().getFluidState(ahead).isEmpty()) {
            return true;
        }

        return yautja.level().getBlockState(ahead).isAir()
            && yautja.level().getBlockState(ahead.below()).isAir();
    }

    /**
     * {@return how many blocks ahead the first solid landing is, or -1 if there is none in range}
     * <p>
     * ⚠ Every block in between has to be gap. A stretch that is part water and part solid is something to walk across,
     * and leaping it would launch the yautja into the solid part.
     */
    private static int findLanding(Yautja yautja, Vec3 direction, int maxGap) {
        var feet = yautja.blockPosition();

        for (var distance = MIN_GAP; distance <= maxGap; distance++) {
            var probe = BlockPos.containing(
                feet.getX() + 0.5 + direction.x * distance,
                feet.getY(),
                feet.getZ() + 0.5 + direction.z * distance
            );

            if (isGap(yautja, probe)) {
                continue;
            }

            for (var step = MAX_LANDING_STEP; step >= -MAX_LANDING_STEP; step--) {
                if (isStandable(yautja, probe.above(step))) {
                    return distance;
                }
            }

            // The first non-gap column is not somewhere it can land, so there is nothing to leap to.
            return -1;
        }

        return -1;
    }

    private static boolean isGap(Yautja yautja, BlockPos pos) {
        var level = yautja.level();

        if (!level.getFluidState(pos).isEmpty()) {
            return true;
        }

        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isAir();
    }

    /** Solid floor, and two blocks of clear air to stand in. */
    private static boolean isStandable(Yautja yautja, BlockPos pos) {
        var level = yautja.level();

        return level.getBlockState(pos.below()).isSolid()
            && level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir()
            && level.getFluidState(pos).isEmpty();
    }
}
