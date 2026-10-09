package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.cloak.PredatorCloak;
import com.predator.common.gameplay.cloak.PredatorCloakManager;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.registry.init.PredatorGameRules;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a wild yautja cloaked, under the same rules the player's device follows.
 * <p>
 * A goal rather than a GOAP action because the yautja runs vanilla goal AI — see {@code Yautja#registerGoals}. It sits
 * at a low priority and never blocks: it only decides whether the field should be up, and everything else about the
 * cloak (damage absorption, melee reveal, water shorting it out, the overload) is enforced centrally by
 * {@link PredatorCloakManager}, exactly as it is for players.
 * <h2>The caster</h2> A deployed plasma caster holds a reveal window open for as long as it is up, and this goal
 * honours that through {@code PredatorCloakManager.isRevealed} — the hunter stays visible while it shoots and re-cloaks
 * by itself about two seconds after the caster stows.
 * <h2>The wet rule</h2> ⚠ A yautja whose cloak has been shorted out by water will NOT try to re-engage until it is dry
 * and the rain has stopped. That is a precondition, not a timer, and it is the whole reason this needs care: without it
 * a yautja standing in a river would burn the field out, wait the overload, re-engage, short out again, and loop —
 * hammering the network with state changes for as long as it stayed wet.
 */
public class YautjaCloakGoal extends Goal {

    /** Refreshed every tick while the rule is off, so the reveal never lapses back into a cloak. */
    private static final int CLOAK_DISABLED_REVEAL_TICKS = 40;

    /** How long after being dry again before it re-engages. Stops it flickering back on the instant it leaves water. */
    private static final int DRY_SETTLE_TICKS = 40;

    private final Yautja yautja;

    private int dryTicks;

    public YautjaCloakGoal(Yautja yautja) {
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

        PredatorCloakManager.tickNonPlayer(yautja);

        // Wet: let the manager's own water handling short the field out, and hold off re-engaging.
        if (yautja.isInWaterOrRain()) {
            dryTicks = 0;
            return;
        }

        if (dryTicks < DRY_SETTLE_TICKS) {
            dryTicks++;
            return;
        }

        // ⚠ The gamerule is checked BEFORE the "already cloaked" test, and deliberately so: switching it off has to
        // strip the field from yautja that are ALREADY hidden, not merely stop new ones engaging. Otherwise the
        // rule appears to do nothing until every cloaked predator happens to be revealed by something else.
        if (!yautja.level().getGameRules().getBoolean(PredatorGameRules.PREDATOR_CLOAKING)) {
            if (PredatorCloak.isCloaked(yautja)) {
                PredatorCloakManager.revealFor(yautja, CLOAK_DISABLED_REVEAL_TICKS);
            }

            return;
        }

        if (PredatorCloak.isCloaked(yautja) || PredatorCloakManager.isOnCooldown(yautja)) {
            return;
        }

        // ⚠⚠ A DELIBERATE reveal is not the same as a broken one, and this goal used to treat them identically.
        // Anything that opens a reveal window — a landed melee blow, or the plasma caster being deployed — was
        // undone on the very next tick, because the checks above only ask "is the field down and off cooldown".
        // The honor-bound two-second melee reveal therefore never actually happened on a mob. It does now.
        if (PredatorCloakManager.isRevealed(yautja)) {
            return;
        }

        // ⚠ A SECOND, INDEPENDENT GUARD, and not redundant. revealFor() no-ops when the wearer has no cloak runtime
        // yet — a yautja that has never cloaked — so isRevealed() above would answer false and this goal would
        // engage the field in the middle of a two-second charge, which is the exact opposite of his rule that it
        // decloaks to fire. Deployed state is a fact about the caster and needs no runtime to be true.
        if (yautja.isCasterDeployed()) {
            return;
        }

        PredatorCloakManager.engage(yautja);
    }
}
