package com.predator.common.gameplay.entity.living.yautja.goal;

import com.blib.api.common.inventory.v1.BLibInventory;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.util.ItemGoalUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Throwing the shuriken or the smart disc at prey it cannot walk to.
 * <h2>His spec</h2> "if theres no path upwards like say they climb a tree and cut the trunk to get up the pred can
 * still damage them."
 * <h2>⚠⚠ NOTHING HAS THROWN EITHER WEAPON SINCE THE GOAP CONVERSION</h2> {@code UseItemGoal} was the only caller of
 * {@code ItemGoalUtil.shootShuriken} and {@code shootSmartDisc}, and it was dropped from the goal selector when
 * movement moved to GOAP. The class is still in the tree and still compiles, so nothing complained — the yautja simply
 * carried a disc it never used. This goal replaces that path deliberately rather than restoring a goal that also wanted
 * to drive movement.
 * <h2>Unreachable, not merely distant</h2> ⚠ The trigger is that walking has FAILED, not that the target is far away. A
 * yautja that can still path to you should close and use its blades — that is the honor code, and a hunter that stood
 * off and threw discs at a reachable target would read as cowardice. Two signals count as unreachable:
 * <ul>
 * <li>the navigator is reporting consecutive path failures, which is the tree-with-no-trunk case exactly, and</li>
 * <li>the target is well above and the yautja is not climbing — it wants to go up and has not found a way.</li>
 * </ul>
 * <h2>No goal flags</h2> Like the caster and the darts, this claims nothing. GOAP owns movement; a throw must not
 * interrupt it.
 */
public class YautjaThrowGoal extends Goal {

    /** Ticks between throws. Long enough that being treed is a problem to solve, not an execution. */
    private static final int COOLDOWN_TICKS = 60;

    /** The tick the thrown weapon leaves the hand, or -1 when no throw is winding up. */
    private int releaseAtTick = -1;

    /** Which throw is winding up: the disc, or a shuriken. */
    private boolean releasingDisc;

    /** Blocks. Beyond this it will not bother. */
    private static final double MAX_RANGE = 24.0;

    /**
     * Blocks above the yautja before "up there" counts as a reason on its own.
     * <p>
     * ⚠ Matches {@code YautjaClimb.MIN_TARGET_HEIGHT}. The two are the same judgement — "that is above me, not up a
     * step" — and if they drifted apart a yautja could decide to climb and to throw at the same target height, or
     * neither.
     */
    private static final double MIN_TARGET_HEIGHT = 3.0;

    private final Yautja yautja;

    private int nextThrowTick;

    public YautjaThrowGoal(Yautja yautja) {
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

        // ⚠ A throw winding up: keep facing and keep range, then let go on the release frame — at a live target still
        // in sight, and only if the throwable is still in hand (the lock or a swap could have changed it).
        if (releaseAtTick >= 0) {
            var mark = yautja.getTarget();

            if (mark != null && mark.isAlive()) {
                yautja.getLookControl().setLookAt(mark, 30.0F, 30.0F);
                yautja.setRangedHoldUntil(yautja.tickCount + 3);
            }

            if (yautja.tickCount >= releaseAtTick) {
                releaseAtTick = -1;

                var inHand = yautja.getMainHandItem();

                if (mark != null && mark.isAlive() && yautja.hasLineOfSight(mark)) {
                    if (releasingDisc && inHand.is(PredatorItems.SMART_DISC.get())) {
                        ItemGoalUtil.shootSmartDisc(yautja);
                    } else if (!releasingDisc && inHand.is(PredatorItems.SHURIKEN.get())) {
                        ItemGoalUtil.shootShuriken(yautja);
                    }
                }
            }

            return;
        }

        if (yautja.tickCount < nextThrowTick) {
            return;
        }

        var target = yautja.getTarget();

        if (
            target == null
                || !target.isAlive()
                || !YautjaPredicates.isValidTarget(yautja, target)
                || yautja.distanceToSqr(target) > MAX_RANGE * MAX_RANGE
                || !yautja.hasLineOfSight(target)
        ) {
            return;
        }

        if (!isUnreachable()) {
            return;
        }

        // ⚠⚠ DRAW ONE IF NOTHING THROWABLE IS IN HAND. The spawn roll gives a blade or nothing half the time,
        // and matchWeaponTo actively swaps a thrown weapon OUT for a sword when the prey carries one. So a
        // yautja that could not reach its prey was frequently holding an axe and carrying a disc it never
        // touched. The rack exists precisely so it can change weapons; this is the case that needs it most.
        if (!isThrowable(yautja.getMainHandItem())) {
            if (!drawThrowable()) {
                return;
            }
        }

        var held = yautja.getMainHandItem();

        // 🚨 BOTH THROWS LEAVE ON THEIR RELEASE FRAME, not frame 0 — the disc or star used to be gone before the arm
        // had
        // gone back. Turned to face first, as every attack now is.
        if (held.is(PredatorItems.SMART_DISC.get())) {
            nextThrowTick = yautja.tickCount + COOLDOWN_TICKS;
            com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat.faceTarget(yautja, target);
            yautja.playAttackAnimation(YautjaAttackAnimation.THROW_SMARTDISC);
            releasingDisc = true;
            releaseAtTick = yautja.tickCount + YautjaAttackAnimation.THROW_SMARTDISC.impactTicks();

            return;
        }

        if (held.is(PredatorItems.SHURIKEN.get())) {
            nextThrowTick = yautja.tickCount + COOLDOWN_TICKS;
            com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat.faceTarget(yautja, target);
            yautja.playAttackAnimation(YautjaAttackAnimation.THROW_SHURIKEN);
            releasingDisc = false;
            releaseAtTick = yautja.tickCount + YautjaAttackAnimation.THROW_SHURIKEN.impactTicks();
        }
    }

    private static boolean isThrowable(ItemStack stack) {
        return stack.is(PredatorItems.SMART_DISC.get()) || stack.is(PredatorItems.SHURIKEN.get());
    }

    /**
     * Swaps a thrown weapon out of the rack and into the hand.
     * <p>
     * ⚠ Stows what it is holding FIRST and proves that landed, exactly as {@code Yautja.matchWeaponTo} does. A stow
     * that silently failed on a full rack would destroy the weapon, which is a bug I have already shipped once.
     */
    private boolean drawThrowable() {
        // ⚠⚠ NOT WHILE THE HAND IS LOCKED. This draws a shuriken or disc from the rack straight into the hand, around
        // matchWeaponTo entirely — so without this a tester's chosen weapon was swapped for a throwable the first time
        // the yautja wanted to throw.
        if (yautja.isWeaponLocked()) {
            return false;
        }

        var inventory = yautja.getInventory();
        var wanted = inventory.hasItem(PredatorItems.SMART_DISC.get())
            ? PredatorItems.SMART_DISC.get()
            : inventory.hasItem(PredatorItems.SHURIKEN.get()) ? PredatorItems.SHURIKEN.get() : null;

        if (wanted == null) {
            return false;
        }

        if (!(inventory.removeItem(wanted) instanceof BLibInventory.RemoveResult.Success)) {
            return false;
        }

        var stowed = yautja.getMainHandItem().copy();

        if (!stowed.isEmpty() && !(inventory.addItemStack(stowed) instanceof BLibInventory.AddResult.Success)) {
            inventory.addItemStack(new ItemStack(wanted));

            return false;
        }

        yautja.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(wanted));

        return true;
    }

    /**
     * {@return whether walking to the target has stopped being an option}
     * <p>
     * ⚠ Reads the navigator's own failure count rather than re-deriving reachability. BLib already tried to plan a
     * route and failed; a second opinion here could disagree with the thing actually steering the yautja.
     */
    private boolean isUnreachable() {
        var target = yautja.getTarget();

        if (target == null) {
            return false;
        }

        if (yautja.getPathNavigator().getState().getConsecutiveFailures() > 0) {
            return true;
        }

        return target.getY() - yautja.getY() >= MIN_TARGET_HEIGHT && !yautja.isClimbing();
    }
}
