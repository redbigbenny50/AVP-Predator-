package com.predator.common.gameplay.entity.living.yautja.path;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hauls a yautja out of water over a lip it cannot step onto.
 * <p>
 * [stated] Oct 5: "this yautja got stuck and was spinning in place ... then he wasnt able to jump out of the water. i
 * think they should be able to either climb out or jump higher out of the water." The screenshot: a pit of shallow
 * water with sand walls about two blocks above its feet.
 * </p>
 * <p>
 * ⚠⚠ WHY IT WAS A TRAP - THREE THINGS, ALL CHECKED IN CODE. (1) The wall climb refused to start in water and let go the
 * moment it touched water ({@code YautjaClimb}). (2) Vanilla's own exit hop in {@code LivingEntity.travel} only fires
 * when the space 0.6 above the feet is free beside the body, i.e. a lip of about one block. (3) {@code Yautja.travel}
 * replaces vanilla's underwater movement, so a submerged yautja never got even that.
 * </p>
 * <p>
 * ⭐ THE SPLIT. A lip up to {@link #MAX_LIP_HEIGHT} above the feet is a MANTLE - this class: a short, held lift and a
 * push over the edge, like vaulting out of a pool. Anything taller is a wall, and the climb now starts from water
 * whenever the head is above the surface (the change in YautjaClimb). Together nothing it can stand in is a cell.
 * </p>
 * <p>
 * ⚠ The lift is HELD for several ticks rather than one impulse. Water drag takes a fifth of the vertical speed every
 * tick, so a single kick big enough to clear two blocks from water would launch it absurdly high from a shallow puddle.
 * A held 0.35 per tick rises a steady block every three ticks and stops the moment the feet are over the lip.
 * </p>
 */
public final class YautjaWaterExit {

    /** Tallest lip, measured from the feet, that is vaulted rather than climbed. Higher walls are the climb's job. */
    private static final int MAX_LIP_HEIGHT = 2;

    /** Upward speed held during the lift, blocks per tick. */
    private static final double LIFT_SPEED = 0.35;

    /** Forward push while rising - enough to stay against the wall. */
    private static final double HUG_SPEED = 0.08;

    /** Forward push once the feet clear the lip - carries the body onto the land. */
    private static final double OVER_SPEED = 0.3;

    /** Hard cap on one mantle, so a lip that turns out not to be climbable can never pin it in the air. */
    private static final int MAX_MANTLE_TICKS = 14;

    /** Rest between attempts, so a failed vault is not retried every tick. */
    private static final int COOLDOWN_TICKS = 20;

    /** Space a 2.48-tall yautja needs on top of the lip: three blocks of headroom. */
    private static final int HEADROOM = 3;

    private static final Map<Yautja, Mantle> ACTIVE = new WeakHashMap<>();

    private static final Map<Yautja, Integer> NEXT_ATTEMPT_TICK = new WeakHashMap<>();

    private YautjaWaterExit() {
        throw new UnsupportedOperationException();
    }

    /** Server tick. Cheap when dry: one water check. */
    public static void tick(Yautja yautja) {
        if (yautja.level().isClientSide) {
            return;
        }

        var mantle = ACTIVE.get(yautja);

        if (mantle != null) {
            continueMantle(yautja, mantle);
            return;
        }

        // ⚠ Oct 5 review: NoAI is how avp_alien holds a captured host webbed in a chamber (HostParking.embed). A webbed
        // yautja in a flooded chamber must not vault itself out - only a release frees it. Passenger covers the carry.
        if (!yautja.isInWater() || yautja.isClimbing() || yautja.isPassenger() || yautja.isNoAi()) {
            return;
        }

        // ⚠ Oct 5 review: chasing something that is itself in the water is not being trapped. Without this a pursuit
        // along a shoreline vaulted out every time the yautja brushed the bank, and the pursuit walked it straight back
        // in.
        var target = yautja.getTarget();

        if (target != null && target.isAlive() && target.isInWater()) {
            return;
        }

        if (yautja.tickCount < NEXT_ATTEMPT_TICK.getOrDefault(yautja, 0)) {
            return;
        }

        // ⚠ Two reasons to try: walking into the wall, or wanting to reach a target that is out of the water. The
        // second
        // matters once BLib's partial-path rest stops the move action from pushing into the wall over and over.
        var direction = facingDirection(yautja);
        var lipTop = yautja.horizontalCollision ? findLipTop(yautja, direction) : null;

        if (lipTop == null) {
            var toward = directionTowardTarget(yautja);

            if (toward != null) {
                direction = toward;
                lipTop = findLipTop(yautja, direction);
            }
        }

        if (lipTop == null) {
            return;
        }

        NEXT_ATTEMPT_TICK.put(yautja, yautja.tickCount + COOLDOWN_TICKS);
        var yaw = direction.toYRot();
        yautja.setYRot(yaw);
        yautja.yBodyRot = yaw;
        ACTIVE.put(yautja, new Mantle(direction, lipTop, yautja.tickCount + MAX_MANTLE_TICKS));
        continueMantle(yautja, ACTIVE.get(yautja));
    }

    /** Whether a mantle is driving this yautja's velocity right now. */
    public static boolean isMantling(Yautja yautja) {
        return ACTIVE.containsKey(yautja);
    }

    private static void continueMantle(Yautja yautja, Mantle mantle) {
        if (yautja.tickCount > mantle.endTick || yautja.isClimbing() || yautja.isPassenger() || yautja.isNoAi()) {
            ACTIVE.remove(yautja);
            return;
        }

        var dx = mantle.direction.getStepX();
        var dz = mantle.direction.getStepZ();

        if (yautja.getY() < mantle.lipTop + 0.05) {
            yautja.setDeltaMovement(dx * HUG_SPEED, LIFT_SPEED, dz * HUG_SPEED);
        } else {
            // Over the edge: one push onto the land and the mantle is done. Gravity and the normal walk take it from
            // here.
            yautja.setDeltaMovement(dx * OVER_SPEED, 0.1, dz * OVER_SPEED);
            ACTIVE.remove(yautja);
        }

        yautja.hasImpulse = true;
        yautja.fallDistance = 0.0F;
    }

    /**
     * {@return the Y of the standable surface on the column in front, or null when there is no lip low enough to vault}
     * A lip is a solid block topped by {@value #HEADROOM} blocks the body fits in, with its top no more than
     * {@value #MAX_LIP_HEIGHT} blocks above the feet. A top below the feet is not a lip - that is open water ahead.
     */
    private static @Nullable Integer findLipTop(Yautja yautja, Direction direction) {
        var level = yautja.level();
        var feet = yautja.blockPosition();
        var column = feet.relative(direction);

        for (var height = 1; height <= MAX_LIP_HEIGHT; height++) {
            var support = column.above(height - 1);
            var supportState = level.getBlockState(support);

            if (!supportState.isFaceSturdy(level, support, Direction.UP)) {
                continue;
            }

            // ⚠ The landing must be dry and open; the column the body rises through only has to be free of BLOCKS -
            // it is full of the very water being climbed out of.
            if (hasHeadroom(yautja, support.above(), true) && hasHeadroom(yautja, feet.above(height), false)) {
                return support.getY() + 1;
            }
        }

        return null;
    }

    private static boolean hasHeadroom(Yautja yautja, BlockPos base, boolean mustBeDry) {
        var level = yautja.level();

        for (var i = 0; i < HEADROOM; i++) {
            var pos = base.above(i);

            if (
                !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || (mustBeDry && !level.getFluidState(pos).isEmpty())
            ) {
                return false;
            }
        }

        return true;
    }

    private static Direction facingDirection(Yautja yautja) {
        return Direction.fromYRot(yautja.getYRot());
    }

    private static @Nullable Direction directionTowardTarget(Yautja yautja) {
        var target = yautja.getTarget();

        if (target == null) {
            return null;
        }

        var delta = new Vec3(target.getX() - yautja.getX(), 0.0, target.getZ() - yautja.getZ());

        if (delta.lengthSqr() < 1.0E-4) {
            return null;
        }

        return Direction.getNearest(delta.x, 0.0, delta.z);
    }

    private record Mantle(
        Direction direction,
        int lipTop,
        int endTick
    ) {}
}
