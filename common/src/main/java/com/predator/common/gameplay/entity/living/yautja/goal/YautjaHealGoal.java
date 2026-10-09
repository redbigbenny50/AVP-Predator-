package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaHealing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Uses a healing item the moment the rules allow — see YautjaHealing for every rule and number.
 * <p>
 * ⚠ No goal flags and always running, like the other weapon goals: healing must never block moving or fighting, and a
 * goal that only checked at START would miss the moment the roar ends (canContinueToUse is true for good, so canUse
 * runs once — the lesson from the battleaxe guard).
 */
public class YautjaHealGoal extends Goal {

    private final Yautja yautja;

    public YautjaHealGoal(Yautja yautja) {
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
        if (yautja.level() instanceof ServerLevel level && YautjaHealing.wantsToHeal(yautja)) {
            YautjaHealing.useOne(level, yautja);
        }
    }
}
