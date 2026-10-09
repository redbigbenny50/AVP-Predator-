package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.debug.PredatorCasterTestMode;
import com.predator.common.gameplay.cloak.PredatorCloakManager;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.caster.CasterState;
import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltProjectile;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the shoulder plasma caster.
 * <h2>Why it holds no goal flags</h2> ⚠ This goal claims neither {@code MOVE} nor {@code LOOK} nor {@code TARGET}, and
 * that is the whole feature. His rule was "in a case where they are fighting close quarters or melee the caster could
 * also be shooting other targets in the same go" — so the caster must never be able to interrupt the melee goal, steal
 * the walk, or turn the head. It sits alongside everything else and only ever writes caster state.
 * <p>
 * It also keeps its own target, separate from {@code Mob#getTarget}. The yautja can be stabbing one thing while the
 * caster burns another.
 * <h2>The cloak cycle</h2> "would decloak to charge and fire it then cloak again after. This gives the party its
 * attacking a chance to attack it back." That is implemented by holding open the same free reveal window a melee hit
 * opens — no overload, no 60s cooldown, and it lapses on its own about two seconds after the caster stows, which
 * re-cloaks the hunter without any code here having to ask.
 */
public class YautjaPlasmaCasterGoal extends Goal {

    /**
     * How long each refresh holds the cloak down for. Longer than the refresh interval below, so the window never
     * flickers shut between refreshes, and short enough that stowing the caster re-cloaks promptly.
     */
    private static final int REVEAL_HOLD_TICKS = 40;

    private static final int REVEAL_REFRESH_INTERVAL = 20;

    private final Yautja yautja;

    @Nullable
    private LivingEntity casterTarget;

    /** The target's motion, measured from its own position tick to tick, for leading a flyer. */
    private net.minecraft.world.phys.Vec3 targetVelocity = net.minecraft.world.phys.Vec3.ZERO;

    private @org.jetbrains.annotations.Nullable net.minecraft.world.phys.Vec3 lastTargetPosition;

    private @org.jetbrains.annotations.Nullable LivingEntity trackedTarget;

    /** Ticks spent in the current {@link CasterState}. */
    private int stateTicks;

    /**
     * Where the firing cycle is. Negative is barrel recovery after a shot, zero upward is charging, and reaching
     * {@link PlasmaCaster#CHARGE_TICKS} fires.
     */
    private int cycleTicks;

    private int scanCooldown;

    private int revealCooldown;

    public YautjaPlasmaCasterGoal(Yautja yautja) {
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
        if (yautja.level().isClientSide) {
            return;
        }

        var state = yautja.getCasterState();

        stateTicks++;
        trackTargetMotion();

        if (state.isDeployed()) {
            holdCloakOpen();
            aimAtTarget();
        }

        switch (state) {
            case STOWED -> tickStowed();
            case DEPLOYING -> tickDeploying();
            case READY -> tickReady();
            case FIRING -> tickFiring();
            case DISARMING -> tickDisarming();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // States
    // ---------------------------------------------------------------------------------------------------------------

    private void tickStowed() {
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }

        scanCooldown = PlasmaCaster.SCAN_INTERVAL_TICKS;

        // The honor rule first, the target second. A yautja that can see a lone armed marine it is already beating
        // does not answer with the caster, so there is no point picking a target before the fight qualifies.
        // ⚠ Test mode suspends the honor gate ONLY. Everything downstream — the charge, the particles, the cloak
        // window, the arc, the bolt — runs exactly as it normally does, so what a tester watches is the real weapon.
        // Oct 8: a siege (prey above it that it failed to climb to) deploys the caster on its own.
        var sieging = com.predator.common.gameplay.entity.living.yautja.YautjaSiege.siegeTarget(yautja) != null;

        if (!sieging && !PredatorCasterTestMode.isEnabled() && !PlasmaCaster.shouldDeploy(yautja)) {
            return;
        }

        var target = selectTarget();

        if (target == null) {
            return;
        }

        casterTarget = target;
        enter(CasterState.DEPLOYING);

        yautja.level()
            .playSound(null, yautja.blockPosition(), PredatorSoundEvents.CASTER_DEPLOY.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
    }

    private void tickDeploying() {
        if (stateTicks < PlasmaCaster.DEPLOY_TICKS) {
            return;
        }

        cycleTicks = 0;
        enter(CasterState.READY);
    }

    private void tickReady() {
        if (scanCooldown > 0) {
            scanCooldown--;
        } else {
            scanCooldown = PlasmaCaster.SCAN_INTERVAL_TICKS;

            if (!isStillWorthFiring()) {
                enter(CasterState.DISARMING);
                return;
            }

            // Retarget even while a shot is charging. The caster is tracking, not committed.
            var refreshed = selectTarget();

            if (refreshed != null) {
                casterTarget = refreshed;
            }
        }

        if (casterTarget == null) {
            enter(CasterState.DISARMING);
            return;
        }

        cycleTicks++;

        if (cycleTicks <= 0) {
            // Barrel still cooling from the last bolt. No particles: the charge tell must mean a shot is coming.
            return;
        }

        if (yautja.level() instanceof ServerLevel serverLevel) {
            var progress = Math.min(1.0F, (float) cycleTicks / PlasmaCaster.CHARGE_TICKS);
            PlasmaCaster.spawnChargeParticles(serverLevel, PlasmaCaster.muzzlePosition(yautja), progress);
        }

        if (cycleTicks == 1) {
            yautja.level()
                .playSound(null, yautja.blockPosition(), PredatorSoundEvents.CASTER_CHARGE.get(), SoundSource.HOSTILE, 0.9F, 1.0F);
        }

        if (cycleTicks >= PlasmaCaster.CHARGE_TICKS) {
            enter(CasterState.FIRING);
        }
    }

    private void tickFiring() {
        // The bolt leaves on the first tick of the state, which is the tick the client starts caster.shoot.
        if (stateTicks == 1) {
            fire();
        }

        if (stateTicks >= PlasmaCaster.FIRE_TICKS) {
            cycleTicks = -PlasmaCaster.RECOVERY_TICKS;
            enter(CasterState.READY);
        }
    }

    private void tickDisarming() {
        if (stateTicks < PlasmaCaster.DISARM_TICKS) {
            return;
        }

        casterTarget = null;
        cycleTicks = 0;
        enter(CasterState.STOWED);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Velocity from position, smoothed over a few ticks. ⚠ Not getDeltaMovement(): a player's motion happens on their
     * own client, so the server's delta is not their real speed — their position change is.
     */
    private void trackTargetMotion() {
        if (casterTarget == null || casterTarget != trackedTarget) {
            trackedTarget = casterTarget;
            lastTargetPosition = casterTarget == null ? null : casterTarget.position();
            targetVelocity = net.minecraft.world.phys.Vec3.ZERO;
            return;
        }

        var now = casterTarget.position();

        if (lastTargetPosition != null) {
            targetVelocity = targetVelocity.scale(0.5).add(now.subtract(lastTargetPosition).scale(0.5));
        }

        lastTargetPosition = now;
    }

    /**
     * Where to send the bolt. A target on the ground is shot where it stands, as always. A flyer out of reach is LED —
     * [stated] "very accurate shots": the bolt flies straight at a fixed speed, so it aims where the target will be
     * when the bolt arrives, refined twice so the lead itself is allowed for.
     */
    private net.minecraft.world.phys.Vec3 aimPoint(net.minecraft.world.phys.Vec3 muzzle) {
        // Oct 8: a siege aims at the floor under the prey, not the prey.
        if (com.predator.common.gameplay.entity.living.yautja.YautjaSiege.siegeTarget(yautja) == casterTarget) {
            var siegePoint = com.predator.common.gameplay.entity.living.yautja.YautjaSiege.aimPoint(yautja);

            if (siegePoint != null) {
                return siegePoint;
            }
        }

        var point = PlasmaCaster.aimPointOn(casterTarget);

        if (!PlasmaCaster.isOutOfReach(casterTarget)) {
            return point;
        }

        var led = point;

        for (var pass = 0; pass < 2; pass++) {
            var ticks = led.distanceTo(muzzle) / PlasmaCaster.BOLT_SPEED;
            led = point.add(targetVelocity.scale(ticks));
        }

        return led;
    }

    private void fire() {
        if (casterTarget == null || !(yautja.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var muzzle = PlasmaCaster.muzzlePosition(yautja);
        var direction = aimPoint(muzzle).subtract(muzzle);

        var bolt = new PlasmaBoltProjectile(serverLevel, yautja);

        bolt.setPos(muzzle.x, muzzle.y, muzzle.z);

        // Oct 8: a siege bolt burns through up to two blocks of floor to reach the prey standing on it.
        if (com.predator.common.gameplay.entity.living.yautja.YautjaSiege.siegeTarget(yautja) == casterTarget) {
            bolt.setSiegeBreaks(2);
        }

        bolt.launch(direction, PlasmaCaster.BOLT_SPEED);

        serverLevel.addFreshEntity(bolt);

        PlasmaCaster.spawnMuzzleFlash(serverLevel, muzzle);

        serverLevel.playSound(
            null,
            yautja.blockPosition(),
            PredatorSoundEvents.CASTER_FIRE.get(),
            SoundSource.HOSTILE,
            1.0F,
            1.0F
        );
    }

    /**
     * Holds the free reveal window open for as long as the caster is up.
     * <p>
     * ⚠ Refreshed rather than set once, because the window is a deadline and a long engagement would outlive a single
     * one. Deliberately the same mechanism a melee hit uses — it costs no overload, so a yautja that fights this way
     * all afternoon is never left with a burnt-out cloak.
     */
    private void holdCloakOpen() {
        if (revealCooldown > 0) {
            revealCooldown--;
            return;
        }

        revealCooldown = REVEAL_REFRESH_INTERVAL;
        PredatorCloakManager.revealFor(yautja, REVEAL_HOLD_TICKS);
    }

    private void aimAtTarget() {
        if (casterTarget == null) {
            return;
        }

        yautja.setCasterAim(
            PlasmaCaster.aimYawTo(yautja, casterTarget),
            PlasmaCaster.aimPitchTo(yautja, casterTarget)
        );
    }

    private void enter(CasterState state) {
        yautja.setCasterState(state);
        stateTicks = 0;

        // [stated] the initiation sound "played in reverse when you put it away".
        if (state == CasterState.DISARMING) {
            yautja.level()
                .playSound(null, yautja.blockPosition(), PredatorSoundEvents.CASTER_RETRACT.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Targeting
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * {@return whether the fight still calls for the caster}
     * <p>
     * Two ways out, both his: the odds have evened up, or the enemy has closed the distance and it is time for the
     * wrist blades. The second is checked against the caster's own target rather than the melee target, since those are
     * allowed to differ.
     */
    private boolean isStillWorthFiring() {
        // Oct 8: a siege keeps the caster out even without a crowd or a flyer to justify it.
        if (com.predator.common.gameplay.entity.living.yautja.YautjaSiege.siegeTarget(yautja) != null) {
            return true;
        }

        if (!PredatorCasterTestMode.isEnabled() && !PlasmaCaster.shouldDeploy(yautja)) {
            return false;
        }

        // Something further out is still worth shooting even if the current target has walked into melee range.
        return selectTarget() != null;
    }

    /**
     * Picks what the caster shoots, independently of what the yautja is fighting.
     * <p>
     * Preference order: whoever is carrying the weapon that justified bringing the caster out, then the biggest thing
     * present, then the nearest. Anything inside {@link PlasmaCaster#DISENGAGE_RANGE} is excluded — that is a melee
     * problem, not a caster one.
     */
    @Nullable
    private LivingEntity selectTarget() {
        // Oct 8: prey it could not climb to is shot through the floor (YautjaSiege) - out of sight, so the ordinary
        // sight and arc rules below would never pick it.
        var siege = com.predator.common.gameplay.entity.living.yautja.YautjaSiege.siegeTarget(yautja);

        if (siege != null) {
            return siege;
        }

        var candidates = yautja.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                yautja.getBoundingBox().inflate(PlasmaCaster.MAX_RANGE),
                candidate -> candidate != yautja && YautjaPredicates.isValidTarget(yautja, candidate)
            );

        LivingEntity best = null;
        var bestScore = Double.NEGATIVE_INFINITY;

        for (var candidate : candidates) {
            var distanceSqr = candidate.distanceToSqr(yautja);

            if (distanceSqr > PlasmaCaster.MAX_RANGE * PlasmaCaster.MAX_RANGE) {
                continue;
            }

            // ⚠ The minimum range is the other thing that stops it firing at a lone tester: a player standing in
            // front of it is inside melee reach, which normally means stow the caster and close. Suspended too.
            // [stated] in whip range a flyer, or anything above it, is grappled "instead of shooting".
            if (PlasmaCaster.isWhipTarget(yautja, candidate)) {
                continue;
            }

            var airborne = PlasmaCaster.isOutOfReach(candidate);

            // ⚠ Not for a flyer: one hanging overhead is close, but nothing in melee reaches it.
            if (
                !PredatorCasterTestMode.isEnabled()
                    && !airborne
                    && distanceSqr < PlasmaCaster.DISENGAGE_RANGE * PlasmaCaster.DISENGAGE_RANGE
            ) {
                continue;
            }

            if (!yautja.hasLineOfSight(candidate) || !PlasmaCaster.isWithinArc(yautja, candidate)) {
                continue;
            }

            var score = -Math.sqrt(distanceSqr) + candidate.getMaxHealth();

            if (PlasmaCaster.isCarryingHeavyWeapon(candidate) || PlasmaCaster.isSentryTurret(candidate)) {
                score += 1000.0;
            }

            // Out of reach is the first thing the caster answers.
            if (airborne) {
                score += 2000.0;
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }
}
