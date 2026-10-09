package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.CombiStickProjectile;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Throwing the combi stick at prey it cannot close on.
 * <h2>⚠⚠ IT COSTS THE YAUTJA NOTHING, AND THAT IS DELIBERATE</h2> The projectile is built from a FRESH stack and the
 * yautja's hand is never touched — exactly what vanilla's Drowned does with its trident
 * ({@code new ItemStack(Items.TRIDENT)}, hand slot untouched, verified in bytecode). So a yautja stays visibly armed
 * while throwing, and cannot disarm itself into a helpless melee.
 * <p>
 * ⚠ The thrown spear is NOT catchable by the player — {@code CombiStickProjectile.tryPickup} requires the picker to be
 * the owner. Otherwise standing still near a spear-throwing yautja would be a farm.
 * <h2>Separate from the melee combo</h2> ⚠ His ruling: "spear throw is a different attack". It is not a combo step, so
 * a yautja mid-swipe never accidentally launches its weapon.
 */
public class YautjaSpearThrowGoal extends Goal {

    /** Long. A thrown spear is a punctuation mark, not a rate of fire. */
    private static final int COOLDOWN_TICKS = 160;

    /** ⚠ Beyond melee reach — inside it the yautja should be using the swipe/stab combo instead. */
    private static final double MIN_RANGE = 5.0;

    /** The tick the spear is let go on, or -1 when no throw is winding up. */
    private int releaseAtTick = -1;

    private static final double MAX_RANGE = 22.0;

    private static final float THROW_SPEED = 2.2F;

    private final Yautja yautja;

    private int nextThrowTick;

    public YautjaSpearThrowGoal(Yautja yautja) {
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

        // ⚠ A throw in its wind-up: keep facing the target and keep it at range, then let go on the release frame.
        if (releaseAtTick >= 0) {
            var mark = yautja.getTarget();

            if (mark != null && mark.isAlive()) {
                yautja.getLookControl().setLookAt(mark, 30.0F, 30.0F);

                // ⚠ HOLD RANGE through the wind-up, as the bow does — otherwise the approach runs it into melee
                // mid-throw and the clip is cut off by the first swing.
                yautja.setRangedHoldUntil(yautja.tickCount + 3);
            }

            if (yautja.tickCount >= releaseAtTick) {
                releaseAtTick = -1;

                if (mark != null && mark.isAlive() && yautja.hasLineOfSight(mark)) {
                    release(mark);
                }
            }

            return;
        }

        if (yautja.tickCount < nextThrowTick) {
            return;
        }

        // ⚠ Only while actually holding an EXTENDED stick. A collapsed one has no blades and throwing a baton would
        // look like the model failed to load.
        var held = yautja.getMainHandItem();

        if (!(held.getItem() instanceof CombiStickItem) || !CombiStickItem.stateOf(held).canAttack()) {
            return;
        }

        var target = yautja.getTarget();

        if (
            target == null
                || !target.isAlive()
                || !YautjaPredicates.isValidTarget(yautja, target)
                || !yautja.hasLineOfSight(target)
        ) {
            return;
        }

        var distanceSqr = yautja.distanceToSqr(target);

        if (distanceSqr < MIN_RANGE * MIN_RANGE || distanceSqr > MAX_RANGE * MAX_RANGE) {
            return;
        }

        nextThrowTick = yautja.tickCount + COOLDOWN_TICKS;

        // 🚨 TURN FIRST, THEN THROW. [stated] "i spawn the skeleton behind the yautja holding a combistick. it throws
        // the combi stick into the wall its facing". The body is snapped round before the wind-up starts, so the arm
        // cocks toward the target instead of toward the wall.
        com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat.faceTarget(yautja, target);
        yautja.playAttackAnimation(YautjaAttackAnimation.SPEAR_THROW);

        // 🚨 THE SPEAR LEAVES ON THE CLIP'S RELEASE FRAME, NOT ITS FIRST. [stated] "i dont think its doing the throw
        // animation or at least one i can tell." It used to spawn on frame 0, before the arm had even gone back, so
        // the spear was gone before the throw began. Now at half speed the arm cocks overhead and lets go 30 ticks
        // in — YautjaAttackAnimation.SPEAR_THROW.impactTicks().
        releaseAtTick = yautja.tickCount + YautjaAttackAnimation.SPEAR_THROW.impactTicks();
    }

    /** Lets go of the spear, aimed at where the target is NOW. */
    private void release(net.minecraft.world.entity.LivingEntity mark) {
        // ⚠ FRESH STACK. Using the held one would strip the yautja of its weapon mid-fight.
        var spear = new CombiStickProjectile(
            yautja.level(),
            yautja,
            new ItemStack(PredatorItems.COMBI_STICK.get())
        );

        // ⚠ AIMED AT THE TARGET, not along the yautja's facing. shootFromRotation used to send it wherever the body
        // pointed, which after a turn mid-wind-up could be well away from what it was throwing at.
        var dx = mark.getX() - spear.getX();
        var dy = mark.getY(0.5) - spear.getY();
        var dz = mark.getZ() - spear.getZ();

        spear.shoot(dx, dy, dz, THROW_SPEED, 1.0F);
        yautja.level().addFreshEntity(spear);
    }
}
