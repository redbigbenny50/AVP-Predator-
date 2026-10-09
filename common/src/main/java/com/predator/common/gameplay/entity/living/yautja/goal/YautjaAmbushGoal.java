package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaMines;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The AMBUSH — pass 3. [agreed] "When its target is out of reach or out of sight for a while ... the yautja works out
 * where the target has to come out, places a mine on the wall or floor beside that exit, then backs off somewhere it
 * can watch the exit and waits" — and [stated] "proximity mines(placed on walls or outside doors if the prey hides and
 * to set traps)".
 * <ol>
 * <li>WAIT: the target has been out of sight {@link #HIDDEN_TICKS} and CANNOT be reached. ⚠ "Cannot be reached" is the
 * pathfinder's own verdict (createPath, canReach) — the yautja can break stone, so a target behind breakable walls is
 * simply dug out; the ambush is for the ones it cannot get to: metal, avp_human industrial blocks, a sealed base.</li>
 * <li>GO: the path's END NODE is the closest reachable point to the target — the way out it has to use. The yautja
 * walks there.</li>
 * <li>PLANT: a mine on the floor at that point (YautjaMines — cap, clean-up and mobGriefing all apply).</li>
 * <li>WATCH: it falls back {@link #VANTAGE_DISTANCE} blocks along the way it came — ground it already walked — and
 * holds there, the chase suppressed, until the target shows itself or {@link #WAIT_TICKS} pass. Its cloak is the
 * existing cloak goal's call, unchanged.</li>
 * </ol>
 */
public class YautjaAmbushGoal extends Goal {

    public static int HIDDEN_TICKS = 100;

    public static int WAIT_TICKS = 20 * 60;

    public static int COOLDOWN_TICKS = 20 * 45;

    public static double MAX_RANGE = 32.0D;

    public static double VANTAGE_DISTANCE = 8.0D;

    /** Giving up on reaching the exit after this. */
    private static final int GO_TIMEOUT_TICKS = 20 * 20;

    private enum Phase {
        IDLE,
        GOING,
        WATCHING
    }

    private final Yautja yautja;

    private Phase phase = Phase.IDLE;

    private int hiddenTicks;

    private int nextAmbushTick;

    private int phaseEndTick;

    private @Nullable BlockPos exit;

    private @Nullable Vec3 approachedFrom;

    private @Nullable Vec3 vantage;

    public YautjaAmbushGoal(Yautja yautja) {
        this.yautja = yautja;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** {@return whether an ambush is under way} — other placing goals stand down meanwhile. */
    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    @Override
    public void tick() {
        if (!(yautja.level() instanceof ServerLevel level)) {
            return;
        }

        var target = yautja.getTarget();

        if (target == null || !target.isAlive() || !YautjaPredicates.isValidTarget(yautja, target)) {
            reset();

            return;
        }

        switch (phase) {
            case GOING -> tickGoing(level, target);
            case WATCHING -> tickWatching(target);
            default -> tickIdle(level, target);
        }
    }

    private void tickIdle(ServerLevel level, LivingEntity target) {
        if (yautja.hasLineOfSight(target)) {
            hiddenTicks = 0;

            return;
        }

        hiddenTicks++;

        if (
            hiddenTicks < HIDDEN_TICKS
                || yautja.tickCount < nextAmbushTick
                || yautja.distanceToSqr(target) > MAX_RANGE * MAX_RANGE
                || !YautjaMines.canPlace(level, yautja)
        ) {
            return;
        }

        var path = yautja.getNavigation().createPath(target, 0);

        // ⚠ Reachable: no ambush — it will simply go in (breaking what it can).
        if (path == null || path.canReach() || path.getEndNode() == null) {
            return;
        }

        exit = path.getEndNode().asBlockPos();
        approachedFrom = yautja.position();
        phase = Phase.GOING;
        phaseEndTick = yautja.tickCount + GO_TIMEOUT_TICKS;
        nextAmbushTick = yautja.tickCount + COOLDOWN_TICKS;
    }

    private void tickGoing(ServerLevel level, LivingEntity target) {
        if (exit == null || yautja.tickCount > phaseEndTick) {
            reset();

            return;
        }

        yautja.setRangedHoldUntil(yautja.tickCount + 10);

        if (yautja.blockPosition().distSqr(exit) > 4.0D) {
            if (yautja.tickCount % 10 == 0) {
                yautja.getNavigation().moveTo(exit.getX() + 0.5D, exit.getY(), exit.getZ() + 0.5D, 1.0D);
            }

            return;
        }

        // At the way out: plant it, falling back to where it stands if the exact spot will not hold a mine.
        if (!YautjaMines.place(level, yautja, exit, Direction.UP)) {
            YautjaMines.placeAtFeet(level, yautja);
        }

        var back = approachedFrom == null ? Vec3.ZERO : approachedFrom.subtract(yautja.position());

        vantage = back.lengthSqr() < 1.0E-4D
            ? yautja.position()
            : yautja.position().add(back.normalize().scale(Math.min(VANTAGE_DISTANCE, back.length())));
        phase = Phase.WATCHING;
        phaseEndTick = yautja.tickCount + WAIT_TICKS;
    }

    private void tickWatching(LivingEntity target) {
        // It came out — the ambush has done its job; the fight resumes.
        if (yautja.tickCount > phaseEndTick || yautja.hasLineOfSight(target)) {
            reset();

            return;
        }

        yautja.setRangedHoldUntil(yautja.tickCount + 10);

        if (exit != null) {
            yautja.getLookControl().setLookAt(exit.getX() + 0.5D, exit.getY() + 0.5D, exit.getZ() + 0.5D);
        }

        if (vantage != null && yautja.position().distanceToSqr(vantage) > 2.25D && yautja.tickCount % 10 == 0) {
            yautja.getNavigation().moveTo(vantage.x, vantage.y, vantage.z, 1.0D);
        }
    }

    private void reset() {
        if (phase != Phase.IDLE) {
            yautja.setRangedHoldUntil(yautja.tickCount);
        }

        phase = Phase.IDLE;
        hiddenTicks = 0;
        exit = null;
        approachedFrom = null;
        vantage = null;
    }
}
