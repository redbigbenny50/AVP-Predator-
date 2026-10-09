package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import com.predator.common.gameplay.entity.living.yautja.path.YautjaPathing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Oct 8 - WHEN A YAUTJA CANNOT CLIMB TO ITS PREY, IT BRINGS THE FLOOR DOWN.
 * <p>
 * Tester video: a player on a pillar with an overhanging platform; the yautja hopped at the lip again and again until
 * shot, fell, and went straight back to the same face. [stated] "he should have stopped attempting to jump up after it
 * broke and fired his plasma caster through the floor at into the player. Or throw a sticky grenade at the underside of
 * the platform" - and the weapon is "whichever is off cool down".
 * <p>
 * Two failed climbs (a roof it cannot pass, or a hop at a lip) toward the same target within 30 s starts a siege for 20
 * s: climbing stops for that long, and while the target is above it and out of sight, the plasma caster and the grenade
 * goal aim at the block directly under the target's feet instead of at the target. Each weapon keeps its own cooldown,
 * so whichever is ready fires. A siege bolt burns through up to two blocks and carries on; a sticky grenade sticks to
 * the underside and blows the floor out.
 * <p>
 * Same limits as the rest of the yautja's block breaking: only with mobGriefing on, and only blocks it is allowed to
 * break (YautjaPathing.canBreak - the hardness window and the yautja_unbreakable blacklist, so no metal or avp_human
 * industrial blocks). If the floor under the target is something it may not break, there is no siege. Server thread
 * only; state dies with the yautja.
 */
public final class YautjaSiege {

    /** Failed climbs toward the same target that start a siege. */
    private static final int FAILURES_TO_SIEGE = 2;

    /** Failures further apart than this do not add up. */
    private static final long FAILURE_WINDOW_TICKS = 30L * 20L;

    /** How long a siege - and the climbing pause that comes with it - lasts. */
    public static final int SIEGE_TICKS = 20 * 20;

    /** Furthest below the target the floor is looked for. */
    private static final int MAX_FLOOR_SCAN = 32;

    private static final Map<Yautja, State> STATES = new WeakHashMap<>();

    private YautjaSiege() {}

    /**
     * Records a failed climb toward the current target; on the second within the window it starts a siege and stops
     * climbing for its duration.
     */
    /**
     * Oct 8 - {@return whether a climb toward this target has failed recently, or a siege is running against it} Used
     * to send the yautja to a grenade spot instead of trying the same climb again.
     */
    public static boolean hasRecentClimbFailure(Yautja yautja, LivingEntity target) {
        var state = STATES.get(yautja);

        if (state == null) {
            return false;
        }

        var now = yautja.level().getGameTime();

        if (target.getUUID().equals(state.siegeTarget) && now < state.siegeUntil) {
            return true;
        }

        return target.getUUID().equals(state.failedTarget)
            && state.failures > 0
            && now - state.firstFailureTick <= FAILURE_WINDOW_TICKS;
    }

    public static void noteClimbFailure(Yautja yautja) {
        var target = yautja.getTarget();

        if (target == null || yautja.level().isClientSide()) {
            return;
        }

        var now = yautja.level().getGameTime();
        var state = STATES.computeIfAbsent(yautja, $ -> new State());

        if (!target.getUUID().equals(state.failedTarget) || now - state.firstFailureTick > FAILURE_WINDOW_TICKS) {
            state.failedTarget = target.getUUID();
            state.failures = 0;
            state.firstFailureTick = now;
        }

        state.failures++;

        if (state.failures >= FAILURES_TO_SIEGE) {
            state.siegeTarget = target.getUUID();
            state.siegeUntil = now + SIEGE_TICKS;
            state.failures = 0;
            yautja.setClimbAttachCooldown(SIEGE_TICKS);
        }
    }

    /**
     * {@return the target being sieged right now, or null} - only while the siege runs, the target is the yautja's
     * current target, alive, well above it, out of its sight, within caster range, and standing on a floor it may
     * break.
     */
    public static @Nullable LivingEntity siegeTarget(Yautja yautja) {
        var state = STATES.get(yautja);
        var target = yautja.getTarget();

        if (state == null || state.siegeTarget == null || target == null || !target.isAlive()) {
            return null;
        }

        if (yautja.level().getGameTime() >= state.siegeUntil || !state.siegeTarget.equals(target.getUUID())) {
            state.siegeTarget = null;
            return null;
        }

        if (
            target.getY() - yautja.getY() < PlasmaCaster.ABOVE_HEIGHT
                || yautja.distanceToSqr(target) > PlasmaCaster.MAX_RANGE * PlasmaCaster.MAX_RANGE
                || yautja.hasLineOfSight(target)
        ) {
            return null;
        }

        return floorUnder(yautja, target) != null ? target : null;
    }

    /** {@return the point to aim at - just inside the underside of the floor block under the target - or null} */
    public static @Nullable Vec3 aimPoint(Yautja yautja) {
        var target = siegeTarget(yautja);

        if (target == null) {
            return null;
        }

        var floor = floorUnder(yautja, target);

        return floor == null ? null : new Vec3(floor.getX() + 0.5, floor.getY() + 0.1, floor.getZ() + 0.5);
    }

    /** {@return whether a siege bolt may burn through the block at pos} */
    public static boolean mayBreak(net.minecraft.world.level.Level level, BlockPos pos) {
        var state = level.getBlockState(pos);

        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && !state.isAir() && YautjaPathing.canBreak(state);
    }

    /** The first solid block under the target's feet, if the yautja may break it. */
    private static @Nullable BlockPos floorUnder(Yautja yautja, LivingEntity target) {
        var level = yautja.level();
        var start = BlockPos.containing(target.getX(), target.getY() - 0.01, target.getZ());
        var lowest = Math.max(start.getY() - MAX_FLOOR_SCAN, (int) Math.floor(yautja.getY()) + 1);

        for (var y = start.getY(); y >= lowest; y--) {
            var pos = new BlockPos(start.getX(), y, start.getZ());
            var state = level.getBlockState(pos);

            if (state.getCollisionShape(level, pos).isEmpty()) {
                continue;
            }

            return mayBreak(level, pos) ? pos : null;
        }

        return null;
    }

    private static final class State {

        private UUID failedTarget;

        private int failures;

        private long firstFailureTick;

        private UUID siegeTarget;

        private long siegeUntil;
    }
}
