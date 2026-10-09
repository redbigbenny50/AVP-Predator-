package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaThreatAssessment;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.item.battleaxe.BattleaxeItem;
import com.predator.common.gameplay.item.battleaxe.BattleaxeSlam;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The yautja's battleaxe ground slam: a SITUATIONAL crowd answer, not part of the combo.
 * <h2>His ruling</h2> [stated] "normally the yautja can slam once every 90s if its against 1 to 2 enemies but if
 * outnumbered 3 or more it can do it more often at 30 seconds".
 * <p>
 * ⚠ The cooldown is decided by the enemies IN REACH of the slam at the moment it fires, not the wider crowd the evasion
 * code counts: a slam only hits what is within its radius, so three foes at twelve blocks are not a reason to slam more
 * often.
 */
public class YautjaBattleaxeSlamGoal extends Goal {

    /** [stated] 90 seconds against one or two. */
    public static int COOLDOWN_FEW_TICKS = 20 * 90;

    /** [stated] 30 seconds when outnumbered three or more. */
    public static int COOLDOWN_OUTNUMBERED_TICKS = 20 * 30;

    /** Foes in reach that count as "outnumbered". */
    public static int OUTNUMBERED_COUNT = 3;

    /**
     * Ticks from the start of the slam clip to the moment the axe meets the ground.
     * <p>
     * 🚨 WAS A GUESS OF 18, AND WRONG. Read from the clip: the axe is overhead at 0.42 s and SLAMS DOWN at 0.54 s. At
     * the slam's 0.65 playback that is tick 17 — see YautjaAttackAnimation.BATTLEAXE_SLAM.impactTicks().
     */
    private static int impactTicks() {
        return YautjaAttackAnimation.BATTLEAXE_SLAM.impactTicks();
    }

    /**
     * Ticks the slam clip plays for at its speed (1.42 s / 0.65). Ordinary melee is held off for this long, or the next
     * battleaxe swing — 18 ticks later — would restart the attack track and cut the slam off mid-motion.
     */
    private static final int SLAM_ACTIVE_TICKS = 44;

    private final Yautja yautja;

    private int nextSlamTick;

    private int impactTick = -1;

    public YautjaBattleaxeSlamGoal(Yautja yautja) {
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
        if (!(yautja.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Land a slam already in progress, even if the target died mid-swing — the axe is already coming down.
        if (impactTick >= 0) {
            if (yautja.tickCount >= impactTick) {
                impactTick = -1;
                BattleaxeSlam.perform(serverLevel, yautja, (float) yautja.getAttributeValue(Attributes.ATTACK_DAMAGE));
            }

            return;
        }

        if (!(yautja.getMainHandItem().getItem() instanceof BattleaxeItem) || yautja.tickCount < nextSlamTick) {
            return;
        }

        var inReach = countInReach();

        if (inReach == 0) {
            return;
        }

        yautja.playAttackAnimation(YautjaAttackAnimation.BATTLEAXE_SLAM);
        impactTick = yautja.tickCount + impactTicks();

        // [stated] the yell is "when it slams the battle axe" — as the axe goes up, so it peaks as it comes down.
        com.predator.common.gameplay.entity.living.yautja.YautjaSounds.heavyAttack(yautja);

        // ⚠ Melee waits until the slam has played out. Combat measures its cadence from lastMeleeAttackTick, so pushing
        // that forward is what makes the next swing start after the slam rather than on top of it.
        yautja.setLastMeleeAttackTick(yautja.tickCount + SLAM_ACTIVE_TICKS - 18);
        nextSlamTick = yautja.tickCount + (inReach >= OUTNUMBERED_COUNT ? COOLDOWN_OUTNUMBERED_TICKS : COOLDOWN_FEW_TICKS);
    }

    private int countInReach() {
        var radiusSqr = BattleaxeSlam.RADIUS * BattleaxeSlam.RADIUS;

        return (int) YautjaThreatAssessment.crowd(yautja)
            .stream()
            .filter(foe -> foe.distanceToSqr(yautja) <= radiusSqr)
            .count();
    }
}
