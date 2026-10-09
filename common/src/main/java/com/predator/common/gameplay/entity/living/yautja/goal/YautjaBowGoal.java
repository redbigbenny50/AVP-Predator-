package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaSensors;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltArrowProjectile;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shooting a bow, for whichever of the two the yautja is holding.
 * <p>
 * Modelled on {@link YautjaDartGoal} — same range gates, same "not at contact range" rule — but it drives the three
 * clips he supplied: raise, loose, lower.
 * <h2>⚠ The animation is the point of this goal</h2> [stated] "i have no idea how it looks using the whip", and the
 * same went for the bows. The clips are dispatched around the shot rather than fired blind: READY as soon as a target
 * is in range and holds, SHOOT on each loose, PUTAWAY once there is nothing to shoot at.
 */
public class YautjaBowGoal extends Goal {

    /** Ticks between arrows. Slower than the dart: a bow is drawn, not flicked. */
    private static final int COOLDOWN_TICKS = 45;

    /** Ticks the raise takes before the first arrow can leave — matches attack.bow.ready's 0.5 s. */
    private static final int READY_TICKS = 10;

    /** How long it holds the raised bow, aiming, before the first shot. Three quarters of a second. */
    private static final int AIM_TICKS = 15;

    /**
     * Ticks from the start of attack.bow.shoot to the loose. The clip snaps at 0.25 s, which is tick 5. ⚠ Read from the
     * clip's own keyframes — change the clip and this must follow.
     */
    private static final int RELEASE_TICKS = 5;

    private static final double MAX_RANGE = 28.0;

    /**
     * How long each refresh of the range hold lasts. Short on purpose: it is renewed every tick while there is a shot,
     * so it only needs to outlive one tick, and a short hold lets the approach resume promptly when the shot is gone.
     */
    private static final int RANGE_HOLD_TICKS = 3;

    private static final float VELOCITY = 2.6F;

    private static final float INACCURACY = 1.4F;

    private final Yautja yautja;

    private int nextShotTick;

    private int readyAtTick;

    private boolean raised;

    /** The tick the drawn arrow is loosed on, or -1 when nothing is being drawn. */
    private int releaseAtTick = -1;

    public YautjaBowGoal(Yautja yautja) {
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

        var held = yautja.getMainHandItem();
        // ⚠ [stated] "if the veritanium bow is chosen it also needs arrows" — never spent, but required, like every
        // other yautja ammunition (Yautja.hasAmmo). The plasma bow needs nothing.
        var isBow = held.is(PredatorItems.VERITANIUM_BOW.get())
            && (yautja.hasAmmo(net.minecraft.world.item.Items.ARROW) || yautja.hasAmmo(PredatorItems.VERITANIUM_ARROW.get()))
            || held.is(PredatorItems.PLASMA_BOW.get());
        var target = yautja.getTarget();
        var inRange = target != null
            && target.isAlive()
            && YautjaPredicates.isValidTarget(yautja, target)
            && yautja.distanceToSqr(target) <= MAX_RANGE * MAX_RANGE
            && yautja.distanceToSqr(target) > YautjaSensors.meleeReachSqr(yautja)
            && yautja.hasLineOfSight(target);

        // A draw that reaches its release frame is loosed — at a live target still in sight. ⚠ Checked BEFORE the
        // lowering below, so a draw that is due fires on the same tick the shot would otherwise be lost.
        if (releaseAtTick >= 0 && yautja.tickCount >= releaseAtTick) {
            releaseAtTick = -1;

            if (isBow && target != null && target.isAlive() && yautja.hasLineOfSight(target)) {
                loose(held, target);
            }
        }

        if (!isBow || !inRange) {
            // ⚠ Lower it ONCE, not every tick — the command is idempotent but the state flag keeps the intent clear.
            if (raised) {
                raised = false;

                // ⚠ A LOWERED BOW CANCELS THE DRAW. Otherwise the pending release would loose an arrow from a bow
                // already on its way down.
                releaseAtTick = -1;
                yautja.getAnimationDispatcher().bowPutaway();
            }

            return;
        }

        // 🚨🚨 HOLD RANGE WHILE THERE IS A SHOT. [stated] "im trying to get the predator to use the bows ... but it
        // just
        // melee attacks what i throw at it." The approach is a GOAP action, MOVE_TO_TARGET, and the only thing that
        // stops it is IS_HOLDING_RANGE — which nothing but the fall-back leap ever set. So a yautja with a bow closed
        // in at chase speed, and the half-second raise meant it was inside melee reach before the first arrow could
        // leave; at melee reach this goal refuses by design, so it swung instead. Refreshed every tick while a shot
        // exists, and allowed to lapse the moment it does not — out of range, no line of sight, or prey inside reach —
        // so the ordinary approach and melee take over exactly when they should.
        yautja.setRangedHoldUntil(yautja.tickCount + RANGE_HOLD_TICKS);

        // ⚠ Face the target: with the approach suppressed, nothing else turns it toward what it is shooting.
        yautja.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (!raised) {
            raised = true;
            // ⚠ Raise, then HOLD the aim — [stated] "maybe we can have a small delay when it shoots a bow so we can see
            // it aim and fire."
            readyAtTick = yautja.tickCount + READY_TICKS + AIM_TICKS;
            yautja.getAnimationDispatcher().bowReady();

            return;
        }

        // ⚠ The raise, THEN the aim hold, have to finish before the first arrow, and no shot may start while one is
        // still being drawn.
        if (yautja.tickCount < readyAtTick || yautja.tickCount < nextShotTick || releaseAtTick >= 0) {
            return;
        }

        nextShotTick = yautja.tickCount + COOLDOWN_TICKS;
        yautja.getAnimationDispatcher().bowShoot();

        // 🚨 THE ARROW LEAVES ON THE CLIP'S RELEASE FRAME, NOT ITS FIRST. [stated] "it seems to shoot it so fast i
        // cant tell". attack.bow.shoot holds the drawn pose for 0.25 s and then SNAPS — the right arm jerks from -36 to
        // -13.5 degrees and the left recoils — between 0.25 and 0.29 s. That snap is the loose. The arrow used to
        // spawn on frame 0, before the draw-hold had even played, so it read as instant.
        releaseAtTick = yautja.tickCount + RELEASE_TICKS;
    }

    /** Looses the arrow or bolt, aimed at where the target is NOW — it may have moved during the draw. */
    private void loose(ItemStack held, LivingEntity target) {
        var plasma = held.is(PredatorItems.PLASMA_BOW.get());
        var projectile = plasma ? new PlasmaBoltArrowProjectile(yautja.level(), yautja) : veritaniumArrow();
        var dx = target.getX() - yautja.getX();
        var dy = target.getY(0.5) - projectile.getY();
        var dz = target.getZ() - yautja.getZ();

        projectile.shoot(dx, dy, dz, VELOCITY, INACCURACY);
        yautja.level().addFreshEntity(projectile);

        // ⚠ A yautja's plasma bow uses his fire clip, the same as a player's. It used to play the vanilla arrow.
        yautja.level()
            .playSound(
                null,
                yautja.blockPosition(),
                plasma ? PredatorSoundEvents.PLASMA_BOW_FIRE.get() : SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE,
                1.0F,
                1.0F
            );
    }

    /** ⚠ A real arrow, so the veritanium bow's 1.3x and the tier's thrown multiplier both apply through the item. */
    private AbstractArrow veritaniumArrow() {
        // Veritanium arrows when it carries them (the 50/50 at spawn), vanilla ones otherwise.
        AbstractArrow arrow = yautja.hasAmmo(PredatorItems.VERITANIUM_ARROW.get())
            ? new com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity(
                yautja.level(),
                yautja,
                new ItemStack(PredatorItems.VERITANIUM_ARROW.get()),
                yautja.getMainHandItem()
            )
            : new Arrow(yautja.level(), yautja, new ItemStack(Items.ARROW), yautja.getMainHandItem());

        arrow.setBaseDamage(
            com.predator.common.gameplay.entity.living.yautja.YautjaTier.scaleThrown(
                yautja,
                (float) (arrow.getBaseDamage() * com.predator.common.gameplay.item.bow.VeritaniumBowItem.DAMAGE_MULTIPLIER)
            )
        );

        return arrow;
    }
}
