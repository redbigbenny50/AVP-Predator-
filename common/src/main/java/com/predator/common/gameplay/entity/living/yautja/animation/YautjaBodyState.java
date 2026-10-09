package com.predator.common.gameplay.entity.living.yautja.animation;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaMovement;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Which locomotion clip the body should be playing.
 * <h2>Derived on the client, not synced — except the one bit that cannot be</h2> Position delta, {@code isSwimming} and
 * {@code onGround} are all on every tracking client already. Climbing is the exception: nothing observable
 * distinguishes "ascending a wall" from "being pushed upward", so {@code isClimbing} is a synced flag. Everything else
 * stays derived.
 * <h2>⚠⚠ Measured speed, NOT isAggressive</h2> The first version picked the run clip from {@code isAggressive()}, which
 * vanilla's {@code MeleeAttackGoal} sets. That goal is gone now that movement is GOAP, nothing sets the flag, and every
 * yautja would have walked forever. How fast it is actually travelling is true under any AI.
 */
public enum YautjaBodyState {

    /** {@code idle}, 7.24s loop. */
    IDLE,

    /** {@code walk} — a strut, played back to match the ground speed rather than at its authored cadence. */
    WALK,

    /** {@code run} — the pursuit burst. */
    RUN,

    /** {@code swim}, 2.88s loop, with the body pitched to its travel direction by the animator. */
    SWIM,

    /** {@code evade.roll}, played once when it rolls clear of a blast or a swing. */
    DODGE,

    /** {@code roar}, played once when the mask breaks. */
    ROAR,

    /** {@code crawl}, 0.96s loop — squeezing through a gap too low to stand in. */
    CRAWL,

    /** {@code climb.normal} — scaling something with no one to catch. */
    CLIMB_SLOW,

    /** {@code climb.quick} — going up after prey. */
    CLIMB_FAST,

    /** {@code climb.idleleft} / {@code climb.idleright} — holding on, not climbing. */
    CLIMB_IDLE,

    /** {@code jump}, held through the arc. */
    JUMP,

    /** {@code land}, held briefly on touchdown. */
    LAND;

    /**
     * Ticks of airtime before it counts as a jump rather than a step off a kerb. The standing leap is 14 ticks in the
     * air, so this is comfortably clear of it while ignoring the one- and two-tick hops of ordinary walking.
     */
    private static final int AIRBORNE_TICKS_FOR_JUMP = 4;

    /** How long the landing pose holds after touchdown. Matches the 0.5s clip. */
    private static final int LANDING_TICKS = 10;

    /**
     * {@return the clip this yautja should be playing right now}
     * <p>
     * Priority order is deliberate: climbing and swimming are whole-body modes that override everything, then the
     * airborne states, then ground locomotion. Standing still beats speed, so a yautja that has closed the distance and
     * is swinging is in {@link #IDLE} rather than running on the spot.
     */
    /** Blocks per tick of rise below which a climber is holding rather than climbing. Climbing moves ~0.1+/tick. */
    private static final double CLIMB_IDLE_THRESHOLD = 0.02D;

    public static YautjaBodyState select(Yautja yautja) {
        // ⚠ Above swimming and locomotion, below climbing: a yautja that is crawling is committed to that posture
        // and its hitbox has already shrunk to match, so nothing else may claim the body.
        if (yautja.isCrawling() && !yautja.isClimbing()) {
            return CRAWL;
        }

        // ⚠ ABOVE EVERYTHING. A roar is a scripted beat and must not be cut short by the walk resuming; the state
        // machine holds the body for the clip's duration and hands it back afterwards.
        // ⚠ Above the roar: a dodge is reactive and must interrupt anything, including a roar it is mid-way through.
        if (yautja.isDodging()) {
            return DODGE;
        }

        if (yautja.isRoaring()) {
            return ROAR;
        }

        if (yautja.isClimbing()) {
            // ⚠⚠ ON THE WALL IS NOT THE SAME AS CLIMBING IT. [stated] "when it turns around while climbing to attack
            // something with distance it still does the climbing animation not the climb idle animation. so it looks
            // like its climbing thin air." It is judged on whether the yautja actually ROSE this tick, read from its
            // position rather than its velocity: positions are synced to clients and velocities of mobs are not, and
            // this runs client-side to pick the clip.
            if (Math.abs(yautja.getY() - yautja.yo) < CLIMB_IDLE_THRESHOLD && !yautja.isClimbVaulting()) {
                return CLIMB_IDLE;
            }

            // Same rule as the climb speed itself — see YautjaClimb.climbSpeed.
            // ⚠ A vault is always the fast clip. It is a lunge over a lip, and the slow reach-and-pull cycle
            // reads as the yautja drifting upward through the block rather than hauling itself over it.
            return yautja.isClimbVaulting() || yautja.getTarget() != null ? CLIMB_FAST : CLIMB_SLOW;
        }

        // Set server-side in updateSwimming from isUnderWater, so wading a shallow river still walks.
        if (yautja.isSwimming()) {
            return SWIM;
        }

        if (yautja.getAirborneTicks() >= AIRBORNE_TICKS_FOR_JUMP) {
            return JUMP;
        }

        if (yautja.getTicksSinceLanding() < LANDING_TICKS) {
            return LAND;
        }

        // Horizontal only, so a fall never reads as a sprint.
        var dx = yautja.getX() - yautja.xo;
        var dz = yautja.getZ() - yautja.zo;
        var blocksPerTick = Math.sqrt(dx * dx + dz * dz);

        if (blocksPerTick <= YautjaMovement.movingThresholdBlocksPerTick()) {
            return IDLE;
        }

        var threshold = YautjaMovement.runThresholdBlocksPerTick(
            yautja.getAttributeValue(Attributes.MOVEMENT_SPEED)
        );

        return blocksPerTick >= threshold ? RUN : WALK;
    }
}
