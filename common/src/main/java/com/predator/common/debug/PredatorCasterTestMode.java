package com.predator.common.debug;

import com.blib.api.common.goap.v1.GOAPUser;
import com.predator.common.gameplay.entity.living.yautja.Yautja;

/**
 * Pins a yautja in place and makes it fire the plasma caster, so the weapon can be watched rather than chased.
 * <h2>Why this exists</h2> The caster is honor-gated: it only comes out when the yautja is outnumbered, facing a heavy
 * ranged weapon, or up against something far bigger than it. Every one of those conditions is awkward to stage on
 * purpose, and a yautja that qualifies is also busy running at you — so the weapon is nearly impossible to observe in
 * isolation. This turns both problems off at once.
 * <h2>⚠⚠ It disables the GOAP AGENT, not the goals</h2> That distinction is what makes it work. Movement and melee live
 * in the GOAP graph; the caster and the cloak are vanilla goals on the {@code goalSelector}. Switching the agent off
 * freezes the walking and the swinging while leaving the caster running exactly as it normally would — so what you
 * watch is the real weapon, not a special test path through it.
 * <h2>⚠ Everything else about the caster is untouched</h2> The charge time, the particles, the cloak window, the aim
 * arc, the bolt and the damage all behave normally. The only rules suspended are {@code shouldDeploy} and the minimum
 * range, because those are the two that stop it firing at a lone tester standing in front of it.
 */
public final class PredatorCasterTestMode {

    private static boolean enabled;

    private PredatorCasterTestMode() {
        throw new UnsupportedOperationException();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Freezes or releases a yautja's movement. Called from its server tick.
     * <p>
     * ⚠ Re-enabling on every tick while OFF would fight anything else that legitimately disables the agent — the cocoon
     * lock in avp_alien does exactly that to a captured host — so it only ever writes the value when it is the one that
     * changed it.
     */
    public static void tick(Yautja yautja) {
        if (yautja.level().isClientSide) {
            return;
        }

        var frozen = yautja.isCasterTestFrozen();

        if (enabled == frozen) {
            return;
        }

        var agent = ((GOAPUser<Yautja>) yautja).blib$getGOAPAgentOrNull();

        if (agent == null) {
            return;
        }

        agent.setEnabled(!enabled);
        yautja.setCasterTestFrozen(enabled);

        if (enabled) {
            // Stop dead rather than drifting on whatever momentum the last move action left behind.
            yautja.setDeltaMovement(0.0, yautja.getDeltaMovement().y, 0.0);
        }
    }
}
