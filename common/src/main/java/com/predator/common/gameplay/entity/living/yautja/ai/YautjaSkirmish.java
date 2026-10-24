package com.predator.common.gameplay.entity.living.yautja.ai;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How a yautja closes on its prey while it has a RANGED weapon in hand: it weaves in, side to side, with the odd hop,
 * instead of walking straight at it or planting its feet.
 * <p>
 * [stated] Oct 16, from a tester: "if I catch them before they get too close and start shooting, they really only use
 * ranged attacks while standing still" and "I think if they strafe or jump around when using range it would be
 * perfect". He chose: strafe and move around while trying to close the distance.
 * <h2>The pattern</h2> The yautja walks to a point {@link #ADVANCE} blocks closer to its prey and {@link #SIDESTEP}
 * blocks off to one side, then picks the next point on the OTHER side - a zig-zag that still gains ground every leg.
 * Every few seconds it hops sideways mid-leg. Once it is within {@link #MIN_SKIRMISH_DISTANCE} it stops weaving and the
 * ordinary chase takes it into melee, where it switches to its melee weapon as before.
 * <h2>Why a held point, not one recomputed every tick</h2> A destination that moves every tick makes the pathfinder
 * re-plan every tick. Each point is kept until it is reached, until {@link #MAX_LEG_TICKS} pass, or until it cannot be
 * reached - so a weaving yautja plans one short path per leg, about one every two seconds.
 * <p>
 * Used only by {@link YautjaCombat#MOVE_TO_TARGET}. Server side.
 */
public final class YautjaSkirmish {

    /** Blocks. Closer than this the weave stops and the straight chase finishes the job. */
    public static double MIN_SKIRMISH_DISTANCE = 6.0D;

    /** Blocks each leg gains toward the prey. */
    public static double ADVANCE = 4.0D;

    /** Blocks each leg swings out to the side. */
    public static double SIDESTEP = 3.5D;

    /** Ticks. A leg not finished by then is abandoned for the next one, so a stuck yautja still changes sides. */
    public static int MAX_LEG_TICKS = 50;

    /**
     * Movement speed while weaving, as a multiplier on its walk: between the walk (1.0) and the full chase (1.4), so
     * the weave reads as deliberate and the bow still gets shots off before it arrives.
     */
    public static double SKIRMISH_SPEED_MODIFIER = 1.2D;

    /** Ticks between sideways hops: at least this, plus up to {@link #HOP_JITTER_TICKS} more. */
    public static int MIN_HOP_TICKS = 40;

    public static int HOP_JITTER_TICKS = 50;

    /** The sideways shove a hop adds to vanilla's own jump. */
    public static double HOP_SIDE_PUSH = 0.32D;

    /** Blocks. A leg point whose ground is more than this far above or below the yautja is dropped for the next one. */
    private static final double MAX_LEG_HEIGHT_CHANGE = 4.0D;

    /** ⚠ Weak keys: a yautja that dies or unloads mid-skirmish is forgotten, never kept alive by this map. */
    private static final Map<Yautja, State> STATES = new WeakHashMap<>();

    private static final class State {

        /** +1 or -1: which side the current leg swings to. */
        int side = 1;

        @Nullable
        Vec3 leg;

        int legStartedTick;

        int nextHopTick;
    }

    private YautjaSkirmish() {
        throw new UnsupportedOperationException();
    }

    /** {@return whether the yautja should weave toward this prey rather than chase straight at it} */
    public static boolean shouldSkirmish(Yautja yautja, LivingEntity prey) {
        return yautja.isRangedWeaponInHand()
            && yautja.distanceTo(prey) > MIN_SKIRMISH_DISTANCE
            // ⚠ The SENSING cache, not hasLineOfSight(): this runs every tick of every chase, and the cache holds one
            // raycast per target per tick instead of a fresh one per call.
            && yautja.getSensing().hasLineOfSight(prey)
            && !yautja.isInWater();
    }

    /**
     * {@return the point the yautja should be walking to this tick}
     * <p>
     * Keeps the current leg until it is reached ({@code legReached}) or runs too long; then swings to the other side.
     */
    public static Vec3 legTarget(Yautja yautja, LivingEntity prey, boolean legReached) {
        var state = STATES.computeIfAbsent(yautja, y -> new State());

        if (
            state.leg == null || legReached || yautja.tickCount - state.legStartedTick > MAX_LEG_TICKS
                || state.leg.distanceToSqr(prey.position()) > yautja.distanceToSqr(prey) + 4.0D
        ) {
            if (state.leg != null) {
                state.side = -state.side;
            }

            state.leg = pickLeg(yautja, prey, state.side);
            state.legStartedTick = yautja.tickCount;
        }

        return state.leg;
    }

    /** The current leg could not be pathed to: swing to the other side next tick. */
    public static void legBlocked(Yautja yautja) {
        var state = STATES.get(yautja);

        if (state != null) {
            state.side = -state.side;
            state.leg = null;
        }
    }

    /**
     * Hops sideways now and then while weaving. Only from the ground and out of water, and the push goes the way the
     * current leg is already swinging, so the hop carries the weave on rather than fighting it.
     */
    public static void maybeHop(Yautja yautja, LivingEntity prey) {
        var state = STATES.get(yautja);

        if (state == null || !yautja.onGround() || yautja.isInWater() || yautja.tickCount < state.nextHopTick) {
            return;
        }

        state.nextHopTick = yautja.tickCount + MIN_HOP_TICKS + yautja.getRandom().nextInt(HOP_JITTER_TICKS + 1);

        var direction = sideways(yautja, prey).scale(state.side);
        var side = direction.scale(HOP_SIDE_PUSH);

        yautja.getJumpControl().jump();
        yautja.setDeltaMovement(yautja.getDeltaMovement().add(side.x, 0.0D, side.z));

        // Oct 16 - [stated] the weave's side hops play the side-dodge clips too: dodge.left or dodge.right for the side
        // it is actually hopping to, against its own facing. ⚠ Animation only - it spends no dodge cooldown, so a hop
        // never costs it the sidestep it may need a moment later.
        yautja.startEvade(com.predator.common.gameplay.entity.living.yautja.YautjaDodge.sideOf(yautja, direction));
    }

    private static Vec3 pickLeg(Yautja yautja, LivingEntity prey, int side) {
        var toPrey = new Vec3(prey.getX() - yautja.getX(), 0.0D, prey.getZ() - yautja.getZ());
        var distance = toPrey.length();

        if (distance < 1.0E-3D) {
            return prey.position();
        }

        // ⚠ Never advance past the point where the weave hands over to the straight chase.
        var advance = Math.min(ADVANCE, Math.max(0.0D, distance - MIN_SKIRMISH_DISTANCE + 1.0D));
        var forward = toPrey.scale(1.0D / distance);
        var lateral = new Vec3(-forward.z, 0.0D, forward.x).scale(side * SIDESTEP);
        var x = yautja.getX() + forward.x * advance + lateral.x;
        var z = yautja.getZ() + forward.z * advance + lateral.z;

        // Stand the point on the ground. ⚠ In a cave or under a canopy the surface heightmap is far above it, so fall
        // back to the yautja's own height and let the pathfinder find the floor.
        var ground = yautja.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        var y = Math.abs(ground - yautja.getY()) <= MAX_LEG_HEIGHT_CHANGE ? ground : yautja.getY();

        return new Vec3(x, y, z);
    }

    /** {@return the unit vector square to the line from the yautja to its prey, on the level} */
    private static Vec3 sideways(Yautja yautja, LivingEntity prey) {
        var toPrey = new Vec3(prey.getX() - yautja.getX(), 0.0D, prey.getZ() - yautja.getZ());
        var length = toPrey.length();

        return length < 1.0E-3D ? Vec3.ZERO : new Vec3(-toPrey.z / length, 0.0D, toPrey.x / length);
    }
}
