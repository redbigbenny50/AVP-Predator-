package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaMines;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.whip.WhipGrapple;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The chain whip: hook the target and drag it in — over a trip mine when one can be laid.
 * <h2>His rulings</h2>
 * <ul>
 * <li>[stated] "chained whip(to grapple and pull enemies to it or to pull enemies over trip mines)".</li>
 * <li>[stated] "the grapple should have a cooldown so the yautja doesnt spam it" — {@link #COOLDOWN_TICKS}.</li>
 * <li>[agreed] THE COMBO: when it may lay a mine, it drops one at its own feet, LEAPS BACK, and only then hooks the
 * target in — dragged straight across the mine.</li>
 * <li>[stated] "the yautja drops the mine at its feet but it should back up or jump back 4 blocks. it doesnt want to be
 * too close to the explosion even if its immune." A 4-block leap away from the target, then the hook. The yank stops
 * the prey about 2 blocks short of the yautja — about 2 from the mine, inside its 3-block trigger — while the yautja
 * waits 4 blocks clear. (It is immune to its own mine regardless: trigger and blast.)</li>
 * </ul>
 * ⚠ Same gauntlet rules as the dart and the net: a gauntlet shot, so never with the battleaxe in hand (two hands);
 * needs a chain whip in the rack, never spends it; checked every tick in tick(), not canUse (canUse runs once). ⚠ The
 * yautja only ever YANKS. A missed hook (a block) or a catch too heavy to move is simply let go — see
 * WhipGrapple.tickMob.
 */
public class YautjaWhipGoal extends Goal {

    /** [stated] a cooldown so it does not spam it. Ten seconds between hooks. */
    public static int COOLDOWN_TICKS = 200;

    /** Closer than this it is already in reach of its weapon — no point dragging. */
    private static final double MIN_RANGE = 5.0D;

    /** Further than this and the pull is a long, slow drag across open ground. The hook itself reaches 32. */
    private static final double MAX_RANGE = 20.0D;

    /** How close a flyer or something above it can be and still be hooked. */
    private static final double OVERHEAD_MIN_RANGE = 2.0D;

    /** A hook that has neither bitten nor let go by now is abandoned. */
    private static final int MAX_GRAPPLE_TICKS = 60;

    private final Yautja yautja;

    private int nextShotTick;

    private int grappleStartTick;

    /** The mine is down and it is in the air; the hook goes out once it lands. -1 when not leaping. */
    private int leapStartTick = -1;

    /** [stated] four blocks back from its own mine. */
    private static final double COMBO_LEAP_DISTANCE = 4.0D;

    /**
     * Carries 4.0 blocks: 3.84 in the air plus ~0.2 of slide on landing (worked out from vanilla's drag, gravity and
     * ground friction, the same way the fall-back's own leap was sized).
     */
    public static double COMBO_LEAP_POWER = 0.51D;

    private static final double COMBO_LEAP_LIFT = 0.42D;

    /** Gives up waiting to land after this — the hook goes out anyway if it still can. */
    private static final int MAX_LEAP_TICKS = 30;

    /** The chase is held off this long after the hook goes out, so it does not walk straight back onto its mine. */
    private static final int HOLD_AFTER_HOOK_TICKS = 40;

    public YautjaWhipGoal(Yautja yautja) {
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

    @Override
    public void tick() {
        if (!(yautja.level() instanceof ServerLevel level)) {
            return;
        }

        // A hook already out: drive it. Yank what it bit; let go of anything else, or when it has taken too long.
        if (WhipGrapple.isGrappling(yautja)) {
            if (yautja.tickCount - grappleStartTick > MAX_GRAPPLE_TICKS) {
                WhipGrapple.release(yautja);
            } else {
                WhipGrapple.tickMob(yautja);
            }

            return;
        }

        // Mid-combo: the mine is down and it is leaping back. Hook the prey in once it has landed.
        if (leapStartTick >= 0) {
            tickLeap(level);

            return;
        }

        if (yautja.tickCount < nextShotTick) {
            return;
        }

        if (yautja.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.battleaxe.BattleaxeItem) {
            return;
        }

        if (!yautja.hasAmmo(PredatorItems.CHAIN_WHIP.get())) {
            return;
        }

        var target = yautja.getTarget();

        if (target == null || !target.isAlive() || !YautjaPredicates.isValidTarget(yautja, target) || !yautja.hasLineOfSight(target)) {
            return;
        }

        var distanceSqr = yautja.distanceToSqr(target);
        var overhead = com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster.isWhipTarget(yautja, target);

        // ⚠ A flyer or something above it may be close: nothing else reaches it, so the near limit does not apply.
        var minRange = overhead ? OVERHEAD_MIN_RANGE : MIN_RANGE;

        if (distanceSqr < minRange * minRange || distanceSqr > MAX_RANGE * MAX_RANGE) {
            return;
        }

        nextShotTick = yautja.tickCount + COOLDOWN_TICKS;
        yautja.setWhipReadyTick(nextShotTick);

        // [stated] up there "it will grapple you instead of shooting you and when it does you get pulled to it" —
        // straight to the hook, no mine-and-leap combo.
        if (overhead) {
            hook(level, target);

            return;
        }

        // THE COMBO. On the ground, allowed another mine, AND somewhere safe to land 4 blocks back: mine, leap, hook.
        // ⚠ The landing is checked BEFORE the mine goes down — no mine is ever laid that it cannot then get clear of.
        if (yautja.onGround() && YautjaMines.canPlace(level, yautja)) {
            var bearing = YautjaFallBackGoal.clearBearingAway(yautja, target, COMBO_LEAP_DISTANCE);

            if (bearing != null && YautjaMines.placeAtFeet(level, yautja)) {
                yautja.setDeltaMovement(bearing.x * COMBO_LEAP_POWER, COMBO_LEAP_LIFT, bearing.z * COMBO_LEAP_POWER);
                yautja.hasImpulse = true;
                yautja.getLookControl().setLookAt(target, 60.0F, 60.0F);
                // Hold the chase off, or it would walk straight back over its own mine.
                yautja.setRangedHoldUntil(yautja.tickCount + MAX_LEAP_TICKS + HOLD_AFTER_HOOK_TICKS);
                leapStartTick = yautja.tickCount;

                return;
            }
        }

        hook(level, target);
    }

    /** Waits to land, then hooks — the prey is dragged across the mine it just left. */
    private void tickLeap(ServerLevel level) {
        var target = yautja.getTarget();

        if (target != null) {
            yautja.getLookControl().setLookAt(target, 60.0F, 60.0F);
        }

        var airborne = yautja.tickCount - leapStartTick;

        // ⚠ A few ticks first: on the tick it jumps it is still standing on the ground it is leaving.
        if (airborne < 3 || (!yautja.onGround() && airborne < MAX_LEAP_TICKS)) {
            return;
        }

        leapStartTick = -1;

        if (
            target != null
                && target.isAlive()
                && YautjaPredicates.isValidTarget(yautja, target)
                && yautja.hasLineOfSight(target)
                && yautja.distanceToSqr(target) <= (MAX_RANGE + COMBO_LEAP_DISTANCE) * (MAX_RANGE + COMBO_LEAP_DISTANCE)
        ) {
            hook(level, target);
        }

        yautja.setRangedHoldUntil(yautja.tickCount + HOLD_AFTER_HOOK_TICKS);
    }

    /**
     * Where to throw the hook. A flyer is LED, like the caster's shots: its motion this tick (read from its position —
     * a player's server-side delta movement is not their real velocity) times the hook's flight time. Anything on the
     * ground is aimed at directly, as before.
     */
    private net.minecraft.world.phys.Vec3 leadFor(net.minecraft.world.entity.LivingEntity target) {
        var point = new net.minecraft.world.phys.Vec3(target.getX(), target.getY(0.5D), target.getZ());

        if (!com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster.isOutOfReach(target)) {
            return point;
        }

        var motion = new net.minecraft.world.phys.Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
        var ticks = point.distanceTo(yautja.getEyePosition()) / com.predator.common.gameplay.whip.WhipTuning.HOOK_SPEED;

        return point.add(motion.scale(ticks));
    }

    private void hook(ServerLevel level, net.minecraft.world.entity.LivingEntity target) {
        YautjaCombat.faceTarget(yautja, target);
        yautja.playAttackAnimation(YautjaAttackAnimation.WRIST_FIRE);

        if (WhipGrapple.fireAt(level, yautja, target, leadFor(target))) {
            grappleStartTick = yautja.tickCount;
        }
    }
}
