package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.debug.PredatorPathDiagnostics;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaThreatAssessment;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The fall-back leap: one hop backwards to buy shooting room, then hold there long enough for the ranged goals to use
 * it.
 * <h2>Why this exists</h2> [stated] "the predator already uses the caster if its outnumbered it just doesnt move to use
 * it." {@code YautjaPlasmaCasterGoal} refuses anything inside {@code PlasmaCaster.DISENGAGE_RANGE} (7 blocks) as a
 * melee problem, while the GOAP graph drives MOVE_TO_TARGET — so a swarmed yautja stands in the crowd holding a weapon
 * it will not fire. This leap puts it past that line. [stated] "it wouldnt flee or try to escape it would be trying to
 * give itself distance. and a jump distance backwards would be the quickest way."
 * <h2>⚠ It never turns its back</h2> The leap is backwards along the threat's bearing and the yautja keeps looking at
 * the target throughout, so it reads as a fighting withdrawal rather than a rout.
 */
public class YautjaFallBackGoal extends Goal {

    /** Horizontal push of the leap, blocks per tick. Tuned to land about 10-12 blocks out. */
    public static double LEAP_POWER = 1.15D;

    public static double LEAP_LIFT = 0.42D;

    /** Only leap if the threat is this close — further than this and the ranged goals already work. */
    public static double TRIGGER_RANGE = 6.0D;

    /** Ticks MOVE_TO_TARGET is suppressed after landing, so the caster gets its shot off. */
    public static int HOLD_TICKS = 50;

    /** Ticks before this goal may fire again. */
    public static int COOLDOWN_TICKS = 100;

    /** Shorter retry when terrain blocked every bearing — it may be clear again in a moment. */
    public static int BLOCKED_RETRY_TICKS = 20;

    /** Bearings tried, in degrees off straight-back, before giving up. */
    private static final float[] BEARING_SPREAD = { 0.0F, 25.0F, -25.0F, 50.0F, -50.0F };

    /*
     * ⭐ Oct 7 - SWARMED BY A GROUP AT RANGE. [tester, via him] a yautja swarmed by marines "doesnt seem to jump back
     * ... or try to distance themselves from the group they just stand there". Two reasons in the code: the leap only
     * fired with its own target inside TRIGGER_RANGE (6 blocks) - riflemen shoot from 8-12 - and Elite and above never
     * gave ground for numbers at all. Now: - being outnumbered (3+ fighting it within CROWD_RADIUS) is a trigger on its
     * own, as long as one of them is within SWARM_TRIGGER_RANGE; no need for the target to be close; - it gives ground
     * away from the MIDDLE OF THE GROUP, not just from its target; - [stated]
     * "i would say they dont leap but the do try to distance themselves by backpeddling" - tiers that do not fall back
     * when outnumbered (Elite, Elder, Clan Leader) BACKPEDAL instead: walk backwards facing the group until the nearest
     * of them is BACKPEDAL_CLEAR_RANGE away or the hold runs out. Losing health fast still makes every tier leap, as
     * before.
     */

    /** Outnumbered counts as a trigger once any of the crowd is this close. */
    public static double SWARM_TRIGGER_RANGE = 10.0D;

    /** A backpedal ends once the nearest of the crowd is at least this far away. */
    public static double BACKPEDAL_CLEAR_RANGE = 12.0D;

    /** Backpedal input, as vanilla's strafe "forwards" value (negative = backwards). */
    public static float BACKPEDAL_INPUT = -0.75F;

    private final Yautja yautja;

    private int holdUntil;

    private @Nullable LivingEntity threat;

    /** Where it is backing away from: the middle of the crowd, or the threat itself. */
    private @Nullable Vec3 awayFrom;

    private boolean backpedalling;

    public YautjaFallBackGoal(Yautja yautja) {
        this.yautja = yautja;
        // ⚠ MOVE only. Taking LOOK would fight the combat goals' facing, and taking JUMP would block the dodge roll.
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (yautja.level().isClientSide || yautja.tickCount < yautja.getNextFallBackTick() || !yautja.onGround()) {
            return false;
        }

        var target = yautja.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        var crowd = YautjaThreatAssessment.crowd(yautja);
        var outnumbered = crowd.size() >= YautjaThreatAssessment.CROWD_COUNT;
        var bleeding = YautjaThreatAssessment.isBleedingFast(yautja);
        var targetClose = yautja.distanceToSqr(target) <= TRIGGER_RANGE * TRIGGER_RANGE;
        var swarmed = outnumbered && nearestDistanceSqr(crowd) <= SWARM_TRIGGER_RANGE * SWARM_TRIGGER_RANGE;

        // Losing health fast: any tier leaps, with the threat close (unchanged).
        // Outnumbered: the young leap, the hardened backpedal (Oct 7), from a group anywhere near - not only melee.
        boolean leap;

        if (bleeding && targetClose) {
            leap = true;
        } else if (swarmed) {
            leap = yautja.getTier().fallsBackWhenOutnumbered();
        } else {
            return false;
        }

        // [stated] "retarget to the weakest enemy so it can clear adds before refocusing on the bigger target."
        if (outnumbered) {
            var weakest = YautjaThreatAssessment.weakest(crowd);

            if (weakest != null && weakest != target && weakest.getHealth() < target.getHealth()) {
                yautja.setTarget(weakest);
                target = weakest;
            }
        }

        threat = target;
        awayFrom = swarmed ? centreOf(crowd) : target.position();
        backpedalling = !leap;
        return true;
    }

    private double nearestDistanceSqr(java.util.List<LivingEntity> crowd) {
        var nearest = Double.MAX_VALUE;

        for (var member : crowd) {
            nearest = Math.min(nearest, yautja.distanceToSqr(member));
        }

        return nearest;
    }

    private static Vec3 centreOf(java.util.List<LivingEntity> crowd) {
        var x = 0.0D;
        var y = 0.0D;
        var z = 0.0D;

        for (var member : crowd) {
            x += member.getX();
            y += member.getY();
            z += member.getZ();
        }

        var count = Math.max(1, crowd.size());

        return new Vec3(x / count, y / count, z / count);
    }

    @Override
    public boolean canContinueToUse() {
        if (threat == null || !threat.isAlive() || yautja.tickCount >= holdUntil) {
            return false;
        }

        // Oct 7 - a backpedal stops once the group is far enough off; refreshed every tick (shared crowd cache).
        if (backpedalling) {
            var crowd = YautjaThreatAssessment.crowd(yautja);

            return !crowd.isEmpty()
                && nearestDistanceSqr(crowd) < BACKPEDAL_CLEAR_RANGE * BACKPEDAL_CLEAR_RANGE;
        }

        // [stated] the window "ends early" once the distance is bought: past the caster's cut-off the ranged goals
        // work on their own and there is no reason to keep the yautja from closing again.
        return yautja.distanceToSqr(threat) < TRIGGER_RANGE * TRIGGER_RANGE * 4.0D;
    }

    @Override
    public void start() {
        if (threat == null) {
            return;
        }

        var bearing = chooseBearing(threat);

        if (backpedalling) {
            // Oct 7 - no leap: it walks backwards, facing the group, for up to HOLD_TICKS. The bearing check above
            // still decides whether there is room behind it at all.
            if (bearing == null) {
                holdUntil = yautja.tickCount;
                yautja.setFallBackCooldown(BLOCKED_RETRY_TICKS);
                PredatorPathDiagnostics.onFallBackBlocked(yautja);
                return;
            }

            yautja.getNavigation().stop();

            // BLib's own navigator would otherwise keep steering it along its last path, overwriting the strafe.
            if (yautja instanceof com.blib.api.common.pathfinding.v1.navigator.PathNavigatorUser navigatorUser) {
                navigatorUser.getPathNavigator().stop();
            }

            holdUntil = yautja.tickCount + HOLD_TICKS;
            yautja.setFallBackCooldown(COOLDOWN_TICKS);
            yautja.setRangedHoldUntil(holdUntil);
            return;
        }

        if (bearing == null) {
            // ⚠ Nowhere safe to land. No leap and NO cooldown burnt, so it retries — but a short one stops it probing
            // five bearings every tick against a wall it will never clear.
            holdUntil = yautja.tickCount;
            yautja.setFallBackCooldown(BLOCKED_RETRY_TICKS);
            PredatorPathDiagnostics.onFallBackBlocked(yautja);
            return;
        }

        yautja.setDeltaMovement(bearing.x * LEAP_POWER, LEAP_LIFT, bearing.z * LEAP_POWER);
        yautja.hasImpulse = true;
        yautja.getLookControl().setLookAt(threat, 60.0F, 60.0F);
        yautja.level().playSound(null, yautja.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.7F, 0.7F);
        com.predator.common.gameplay.entity.living.yautja.YautjaSounds.longJump(yautja);

        holdUntil = yautja.tickCount + HOLD_TICKS;
        yautja.setFallBackCooldown(COOLDOWN_TICKS);
        yautja.setRangedHoldUntil(holdUntil);
    }

    @Override
    public void tick() {
        if (threat != null) {
            // Facing the threat the whole way back.
            yautja.getLookControl().setLookAt(threat, 60.0F, 60.0F);
        }

        if (backpedalling && yautja.tickCount < holdUntil) {
            // Body faces the group so "backwards" is away from it; vanilla strafe input is relative to the body.
            if (awayFrom != null) {
                var dx = awayFrom.x - yautja.getX();
                var dz = awayFrom.z - yautja.getZ();
                var yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
                yautja.setYRot(yaw);
                yautja.setYBodyRot(yaw);
            }

            yautja.getMoveControl().strafe(BACKPEDAL_INPUT, 0.0F);
        }
    }

    @Override
    public void stop() {
        if (backpedalling) {
            // Clear the strafe input it was feeding, or the yautja keeps drifting backwards after the goal ends.
            yautja.getMoveControl().strafe(0.0F, 0.0F);
            yautja.setZza(0.0F);
            yautja.setXxa(0.0F);
        }

        threat = null;
        awayFrom = null;
        backpedalling = false;
        yautja.setRangedHoldUntil(yautja.tickCount);
    }

    /** {@return a unit direction away from the threat with a clear landing, or null} */
    private @Nullable Vec3 chooseBearing(LivingEntity target) {
        // Oct 7 - away from the middle of the group when swarmed, otherwise from the threat, as before.
        return awayFrom != null
            ? clearBearingAwayFrom(yautja, awayFrom, FALL_BACK_DISTANCE)
            : clearBearingAway(yautja, target, FALL_BACK_DISTANCE);
    }

    /** How far the fall-back's own leap carries — the distance its landing is checked at. */
    private static final double FALL_BACK_DISTANCE = 10.0D;

    /**
     * {@return a unit direction AWAY from {@code threat} whose whole arc out to {@code distance} is clear and whose
     * landing is safe, or null} Shared by the fall-back (10 blocks) and the chain whip's mine combo (4 blocks), so both
     * leaps refuse the same walls, lava and pits.
     */
    public static @Nullable Vec3 clearBearingAway(Yautja yautja, LivingEntity threat, double distance) {
        return clearBearingAwayFrom(yautja, threat.position(), distance);
    }

    /** Oct 7 - the same probe, away from any point (the middle of a crowd). */
    public static @Nullable Vec3 clearBearingAwayFrom(Yautja yautja, Vec3 from, double distance) {
        var away = yautja.position().subtract(from);
        var flat = new Vec3(away.x, 0.0D, away.z);

        if (flat.lengthSqr() < 1.0E-4D) {
            flat = Vec3.directionFromRotation(0.0F, yautja.getYRot() + 180.0F);
        }

        flat = flat.normalize();

        for (var spread : BEARING_SPREAD) {
            var candidate = spread == 0.0F ? flat : flat.yRot((float) Math.toRadians(spread));

            if (isLandingClear(yautja, candidate, distance)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * ⚠ Probes the whole arc, not just the landing square: a leap that clips a wall at head height dumps the yautja at
     * the attacker's feet, which is worse than not leaping at all. Also refuses lava and a drop that would hurt.
     */
    private static boolean isLandingClear(Yautja yautja, Vec3 direction, double distance) {
        var level = yautja.level();

        for (var step = 2; step <= distance; step += 2) {
            var probe = yautja.position().add(direction.scale(step));
            var head = BlockPos.containing(probe.x, probe.y + yautja.getEyeHeight(), probe.z);
            var feet = BlockPos.containing(probe.x, probe.y, probe.z);

            if (!level.getBlockState(head).isAir() || !level.getBlockState(feet).canBeReplaced()) {
                return false;
            }
        }

        var landing = yautja.position().add(direction.scale(distance));
        var groundSearch = BlockPos.containing(landing.x, landing.y, landing.z);

        for (var drop = 0; drop <= 6; drop++) {
            var below = groundSearch.below(drop);
            var state = level.getBlockState(below);

            if (state.is(Blocks.LAVA)) {
                return false;
            }

            if (!state.canBeReplaced()) {
                return true;
            }
        }

        // Nothing solid within six blocks below the landing: that is a pit, not a position.
        return false;
    }
}
