package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaMines;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Occasional trap-laying while hunting — pass 3. [agreed] "General trap-setting while hunting ... I'd keep this rare",
 * and [stated] "a hunting type yautja what it can do is set some additional mines for traps".
 * <p>
 * While it has a target at middle distance and is on the ground, now and then it drops a mine where it stands — on its
 * own trail, where prey that follows it will walk. RARE: a roll only every {@link #CHECK_TICKS}, and a long cooldown
 * after each one ({@link #COOLDOWN_TICKS}; the Hunter lays them more than twice as often, with its larger mine cap).
 * Never while an ambush or the mine-and-whip combo is under way (the ambush stands it down); the two-mine cap, clean-up
 * and mobGriefing all apply (YautjaMines).
 */
public class YautjaTrapGoal extends Goal {

    public static int CHECK_TICKS = 20 * 5;

    public static float CHANCE = 0.2F;

    public static int COOLDOWN_TICKS = 20 * 90;

    public static int HUNTER_COOLDOWN_TICKS = 20 * 40;

    private static final double MIN_RANGE = 10.0D;

    private static final double MAX_RANGE = 40.0D;

    private final Yautja yautja;

    private final YautjaAmbushGoal ambush;

    private int nextTrapTick;

    public YautjaTrapGoal(Yautja yautja, YautjaAmbushGoal ambush) {
        this.yautja = yautja;
        this.ambush = ambush;
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
        if (!(yautja.level() instanceof ServerLevel level) || yautja.tickCount < nextTrapTick || yautja.tickCount % CHECK_TICKS != 0) {
            return;
        }

        var target = yautja.getTarget();

        if (
            target == null || !target.isAlive() || !YautjaPredicates.isValidTarget(yautja, target) || ambush.isActive() || !yautja
                .onGround()
        ) {
            return;
        }

        var distanceSqr = yautja.distanceToSqr(target);

        if (distanceSqr < MIN_RANGE * MIN_RANGE || distanceSqr > MAX_RANGE * MAX_RANGE || yautja.getRandom().nextFloat() >= CHANCE) {
            return;
        }

        if (YautjaMines.placeAtFeet(level, yautja)) {
            nextTrapTick = yautja.tickCount + (yautja.isHunter() ? HUNTER_COOLDOWN_TICKS : COOLDOWN_TICKS);
        }
    }
}
