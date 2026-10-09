package com.predator.common.gameplay.entity.living.yautja.goal;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.function.BooleanSupplier;

/**
 * Runs another goal only while a condition holds, and stops it the moment the condition fails.
 * <p>
 * Built for the Hunter's bare-handed fight — [stated] "if the player has no weapons it will fight them bare handed
 * putting away all its weapons including wrist blades". Every weapon goal answers {@code canUse() = true} and makes its
 * own decisions inside {@code tick()}, so there is no single place in them to say "not now". Wrapping them here gates
 * all of them with one condition, without touching each goal: when the condition turns false the goal selector stops
 * the inner goal through its normal {@code stop()}, so a deployed caster folds away and a drawn bow is lowered exactly
 * as they would be any other time.
 */
public final class YautjaGatedGoal extends Goal implements com.blib.api.common.perf.v1.BLibPerf.Named {

    private final Goal inner;

    private final BooleanSupplier allowed;

    private final Runnable onClosed;

    /** Oct 6 - /blib perf reports the gate under the goal it guards, not as one lump "YautjaGatedGoal". */
    private final String perfName;

    public YautjaGatedGoal(Goal inner, BooleanSupplier allowed) {
        this(inner, allowed, () -> {});
    }

    /**
     * ⚠ {@code onClosed} puts away whatever the inner goal left out. The weapon goals hold their state on the yautja
     * itself (a deployed caster, a drawn bow, a hook in flight) and have no stop() of their own, so without this a gate
     * closing mid-shot would leave the caster up and the bow drawn for the whole bare-handed fight.
     */
    public YautjaGatedGoal(Goal inner, BooleanSupplier allowed, Runnable onClosed) {
        this.inner = inner;
        this.allowed = allowed;
        this.onClosed = onClosed;
        setFlags(inner.getFlags());

        var innerName = inner.getClass().getName();
        this.perfName = "Gated " + innerName.substring(innerName.lastIndexOf('.') + 1);
    }

    @Override
    public String perfName() {
        return perfName;
    }

    @Override
    public boolean canUse() {
        return allowed.getAsBoolean() && inner.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return allowed.getAsBoolean() && inner.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return inner.isInterruptable();
    }

    @Override
    public void start() {
        inner.start();
    }

    @Override
    public void stop() {
        inner.stop();

        if (!allowed.getAsBoolean()) {
            onClosed.run();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return inner.requiresUpdateEveryTick();
    }

    @Override
    public void tick() {
        inner.tick();
    }

    @Override
    public String toString() {
        return "Gated[" + inner + "]";
    }
}
