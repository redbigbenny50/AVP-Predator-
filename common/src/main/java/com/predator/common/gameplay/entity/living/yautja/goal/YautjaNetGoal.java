package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaThreatAssessment;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.NetProjectile;
import com.predator.common.gameplay.net.PredatorNet;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Firing the gauntlet net at prey worth taking alive.
 * <h2>⚠ It nets to CAPTURE, not to win a fight</h2> The net makes a mob immobile and does no damage, so a yautja that
 * used it on something it was already beating would be throwing away its own kill. The gate is therefore prey that is
 * GETTING AWAY — fleeing, or too far to close on quickly — which is the only situation where immobilising something is
 * worth an item.
 * <h2>⚠ Flagless</h2> Like the caster, dart and throw goals: GOAP owns movement, and a net must never interrupt it.
 */
public class YautjaNetGoal extends Goal {

    /** Ticks between shots. Long — a net is a tool, not a weapon, and spamming it reads as a stun-lock. */
    private static final int COOLDOWN_TICKS = 200;

    private static final double MIN_RANGE = 6.0;

    private static final double MAX_RANGE = 20.0;

    private final Yautja yautja;

    private int nextShotTick;

    public YautjaNetGoal(Yautja yautja) {
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
        if (yautja.level().isClientSide || yautja.tickCount < nextShotTick) {
            return;
        }

        // 🚨🚨 THESE GUARDS LIVE HERE, NOT IN canUse. canContinueToUse returns true for good, and a goal only asks
        // canUse when it STARTS — which is the tick it spawns. The battleaxe guard used to sit in canUse and so never
        // ran again after that first tick: a yautja that picked the axe up later kept firing its launcher anyway.

        // TWO HANDS ON THE AXE — [stated] "it would stop it using gauntlet weapons so would need to put it away to
        // fire".
        if (yautja.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.battleaxe.BattleaxeItem) {
            return;
        }

        // NO AMMUNITION, NO SHOT. Never spent — only required. See Yautja.hasAmmo.
        if (!yautja.hasAmmo(com.predator.common.registry.init.item.PredatorItems.NET.get())) {
            return;
        }

        // 🚨🚨 THE NET IS A TACTIC, NOT A WEAPON TO FIRE AT EVERY CHANCE. [stated] "its meant as a control tactic for
        // number and to give the yautja recovery time. it seems to fire it each chance it gets." It is thrown only:
        // - when OUTNUMBERED — the same 3-or-more-within-12-blocks the evasion leap uses, so the two agree on what
        // "outnumbered" means; or
        // - when it NEEDS TO RECOVER and has something to recover WITH — netting a foe buys the time to heal, and
        // with nothing to heal with there is no time worth buying.
        if (!YautjaThreatAssessment.isOutnumbered(yautja) && !(yautja.needsRecovery() && yautja.hasHealingItem())) {
            return;
        }

        var target = yautja.getTarget();

        if (
            target == null
                || !target.isAlive()
                || !YautjaPredicates.isValidTarget(yautja, target)
                || !PredatorNet.canBeNetted(target)
                || !yautja.hasLineOfSight(target)
        ) {
            return;
        }

        var distanceSqr = yautja.distanceToSqr(target);

        if (distanceSqr < MIN_RANGE * MIN_RANGE || distanceSqr > MAX_RANGE * MAX_RANGE) {
            return;
        }

        nextShotTick = yautja.tickCount + COOLDOWN_TICKS;

        // ⚠⚠ THE GAUNTLET IS WHAT FIRES A NET ON LAND, so it gets the wrist clips. His note: the wrist aim and
        // fire are for the gauntlet used on land, and the net is currently the only thing it launches there.
        // The aim is stamped BEFORE the projectile so the arm is up as the net leaves it, not after.
        yautja.playAttackAnimation(YautjaAttackAnimation.WRIST_FIRE);

        var net = new NetProjectile(yautja.level(), yautja);

        net.shootFromRotation(yautja, yautja.getXRot(), yautja.getYRot(), 0.0F, 1.4F, 1.0F);
        yautja.level().addFreshEntity(net);
    }
}
