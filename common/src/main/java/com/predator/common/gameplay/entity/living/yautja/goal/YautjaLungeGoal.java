package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaSensors;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaEvadeClip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The lunge: dodge.forward as a gap-closer.
 * <p>
 * Oct 16 - [stated] "the dodge forward is a lunge attack", and he chose: "Gap-closer: lunges at a target 5-10 blocks
 * away and strikes on arrival".
 * <h2>Timed to the clip</h2> dodge.forward is a full second and moves nothing itself (its root only dips), so this
 * drives the body along its frames, as authored: coiled by {@link #DRIVE_START}, lunge pose held to {@link #DRIVE_END}
 * while the yautja is carried forward, then the arms close in and the blow lands at {@link #STRIKE} - if the prey is
 * still within reach. A lunge that misses (the prey stepped aside) simply recovers; there is no second try until the
 * cooldown is up.
 * <h2>Only with a melee weapon in hand</h2> With a bow or a disc the ranged weave (YautjaSkirmish) does the closing,
 * and lunging INTO bow range would throw away the point of having one.
 */
public class YautjaLungeGoal extends Goal {

    /** Blocks. [stated] 5-10. */
    public static double MIN_RANGE = 5.0D;

    public static double MAX_RANGE = 10.0D;

    /** Ticks between lunges, plus up to {@link #COOLDOWN_JITTER_TICKS} more, so it reads as a choice, not a rhythm. */
    public static int COOLDOWN_TICKS = 160;

    public static int COOLDOWN_JITTER_TICKS = 60;

    /** Blocks the prey may stand above or below it. A lunge is along the ground, not up a tree. */
    private static final double MAX_HEIGHT_DIFFERENCE = 2.0D;

    /** Fastest it is ever carried, blocks per tick. A 10-block lunge over 7 ticks is about 1.3. */
    private static final double MAX_SPEED = 1.8D;

    /** Seconds into dodge.forward as authored: coiled by 0.08, lunge pose held to 0.42, arms close by 0.75. */
    private static final float DRIVE_START = 0.08F;

    private static final float DRIVE_END = 0.42F;

    private static final float STRIKE = 0.5F;

    /** Reach on arrival, relative to its normal melee reach - a little slack, the prey is moving. */
    private static final double STRIKE_REACH_SCALE = 1.4D;

    private final Yautja yautja;

    private @Nullable LivingEntity prey;

    private int startTick;

    private boolean struck;

    public YautjaLungeGoal(Yautja yautja) {
        this.yautja = yautja;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (
            yautja.level().isClientSide || yautja.tickCount < yautja.getNextLungeTick() || !yautja.onGround()
                || yautja.isInWater() || yautja.isClimbing() || yautja.isDodging() || yautja.isRoaring()
                || yautja.isHoldingRange() || yautja.isRangedWeaponInHand()
        ) {
            return false;
        }

        var target = yautja.getTarget();

        if (target == null || !target.isAlive() || !yautja.getSensing().hasLineOfSight(target)) {
            return false;
        }

        var distance = yautja.distanceTo(target);

        if (distance < MIN_RANGE || distance > MAX_RANGE || Math.abs(target.getY() - yautja.getY()) > MAX_HEIGHT_DIFFERENCE) {
            return false;
        }

        // ⚠ A clear run or nothing: a lunge into a tree trunk is a yautja headbutting a tree. A blocked run costs a
        // short wait, so it does not probe the same wall every tick.
        if (!isRunClear(target)) {
            yautja.setLungeCooldown(20);
            return false;
        }

        prey = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // Committed for the whole clip, whatever the prey does: stopping would freeze it mid-lunge.
        return yautja.tickCount - startTick < YautjaEvadeClip.FORWARD.ticks();
    }

    @Override
    public void start() {
        startTick = yautja.tickCount;
        struck = false;
        yautja.getNavigation().stop();

        if (yautja instanceof com.blib.api.common.pathfinding.v1.navigator.PathNavigatorUser navigatorUser) {
            navigatorUser.getPathNavigator().stop();
        }

        yautja.startEvade(YautjaEvadeClip.FORWARD);
        // ⚠ The chase stands down for the lunge, or MOVE_TO_TARGET steers against it.
        yautja.setRangedHoldUntil(yautja.tickCount + YautjaEvadeClip.FORWARD.ticks());
        yautja.setLungeCooldown(COOLDOWN_TICKS + yautja.getRandom().nextInt(COOLDOWN_JITTER_TICKS + 1));
        com.predator.common.gameplay.entity.living.yautja.YautjaSounds.longJump(yautja);
    }

    /** ⚠ Every tick: vanilla ticks a running goal only on alternate ticks unless asked, which would halve the lunge. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (prey == null) {
            return;
        }

        var clip = YautjaEvadeClip.FORWARD;
        var elapsed = yautja.tickCount - startTick;

        yautja.getLookControl().setLookAt(prey, 60.0F, 60.0F);

        if (elapsed >= clip.tickAt(DRIVE_START) && elapsed < clip.tickAt(DRIVE_END)) {
            // Re-aimed every tick at where the prey is NOW, and only as far as striking reach: it arrives in front of
            // the prey, not through it.
            var toPrey = new Vec3(prey.getX() - yautja.getX(), 0.0D, prey.getZ() - yautja.getZ());
            var gap = toPrey.length() - Math.sqrt(YautjaSensors.meleeReachSqr(yautja)) * 0.8D;
            var ticksLeft = Math.max(1, clip.tickAt(DRIVE_END) - elapsed);

            if (gap > 0.0D && toPrey.lengthSqr() > 1.0E-4D) {
                var speed = Math.min(MAX_SPEED, gap / ticksLeft);
                var direction = toPrey.normalize();
                var motion = yautja.getDeltaMovement();

                yautja.setDeltaMovement(direction.x * speed, motion.y, direction.z * speed);
                yautja.hasImpulse = true;
            }
        }

        if (!struck && elapsed >= clip.tickAt(STRIKE)) {
            struck = true;

            if (
                prey.isAlive() && yautja.distanceToSqr(prey) <= YautjaSensors.meleeReachSqr(yautja) * STRIKE_REACH_SCALE
                    * STRIKE_REACH_SCALE
            ) {
                yautja.doHurtTarget(prey);
            }
        }
    }

    @Override
    public void stop() {
        prey = null;
        yautja.setRangedHoldUntil(yautja.tickCount);
    }

    /**
     * {@return whether the ground between it and the prey is clear to run} Every block of the way: nothing solid at
     * feet or head height, and solid ground within two blocks below (no lunging into a pit).
     */
    private boolean isRunClear(LivingEntity target) {
        var level = yautja.level();
        var toTarget = new Vec3(target.getX() - yautja.getX(), 0.0D, target.getZ() - yautja.getZ());
        var length = toTarget.length();

        if (length < 1.0E-3D) {
            return true;
        }

        var direction = toTarget.scale(1.0D / length);

        for (var step = 1; step < length - 1.0D; step++) {
            var probe = yautja.position().add(direction.scale(step));
            var feet = BlockPos.containing(probe.x, probe.y + 0.5D, probe.z);
            var head = BlockPos.containing(probe.x, probe.y + yautja.getEyeHeight(), probe.z);

            if (
                !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()
            ) {
                return false;
            }

            var ground = false;

            for (var drop = 1; drop <= 3 && !ground; drop++) {
                var below = BlockPos.containing(probe.x, probe.y + 0.5D, probe.z).below(drop);
                ground = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            }

            if (!ground) {
                return false;
            }
        }

        return true;
    }
}
