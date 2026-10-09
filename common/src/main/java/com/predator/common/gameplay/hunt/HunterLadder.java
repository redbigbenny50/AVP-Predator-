package com.predator.common.gameplay.hunt;

import com.predator.common.config.YautjaHonorConfig;
import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import net.minecraft.util.RandomSource;

/**
 * Which tier of Hunter comes for a player — his Oct 4 ruling.
 * <ul>
 * <li>The first hunt is always a Youngblood.</li>
 * <li>Honor unlocks the higher tiers ({@code tier_unlock_honor}). A tier joins the roll at {@code ladder_step} percent,
 * taken from the tier just below it.</li>
 * <li>Every hunt WON moves {@code ladder_step} percent up every rung, top rung first, so each tier hands some of its
 * share to the one above. [stated] "eventually the young bloods will stop spawning and it will be only blooded and up
 * then eventualy elite and up etc."</li>
 * <li>Losing moves nothing.</li>
 * </ul>
 * The chances live on the player's {@link HuntLedger.Entry} and always sum to 100.
 */
public final class HunterLadder {

    /** Ladder rung to tier. Unblooded is never a Hunter. */
    private static final YautjaTier[] RUNGS = {
        YautjaTier.YOUNGBLOOD,
        YautjaTier.BLOODED,
        YautjaTier.ELITE,
        YautjaTier.ELDER,
        YautjaTier.CLAN_LEADER
    };

    private HunterLadder() {}

    /** {@return the ladder rung of a tier, or 0 for anything not on it} */
    public static int rungOf(YautjaTier tier) {
        for (var rung = 0; rung < RUNGS.length; rung++) {
            if (RUNGS[rung] == tier) {
                return rung;
            }
        }

        return 0;
    }

    /** Adds every tier the player's honor has reached to the roll. */
    static void unlock(HuntLedger.Entry entry, int honor) {
        while (entry.unlockedRungs < RUNGS.length && honor >= YautjaHonorConfig.tierUnlock(entry.unlockedRungs)) {
            var newRung = entry.unlockedRungs;
            var moved = Math.min(YautjaHonorConfig.ladderStep(), entry.ladder[newRung - 1]);

            entry.ladder[newRung - 1] -= moved;
            entry.ladder[newRung] += moved;
            entry.unlockedRungs++;
        }
    }

    /**
     * A hunt was won: each unlocked rung hands {@code ladder_step} to the one above. Worked top-down, so a middle tier
     * gives and receives in the same win — it holds steady while the shares climb past it, as in the table he approved.
     */
    static void climb(HuntLedger.Entry entry) {
        var step = YautjaHonorConfig.ladderStep();

        for (var rung = entry.unlockedRungs - 1; rung >= 1; rung--) {
            var moved = Math.min(step, entry.ladder[rung - 1]);

            entry.ladder[rung - 1] -= moved;
            entry.ladder[rung] += moved;
        }
    }

    /** {@return the tier for this hunt} — Youngblood on the first, otherwise a roll on the ladder. */
    static YautjaTier roll(HuntLedger.Entry entry, int honor, RandomSource random) {
        unlock(entry, honor);

        if (entry.huntsFaced == 0) {
            return YautjaTier.YOUNGBLOOD;
        }

        var total = 0;

        for (var share : entry.ladder) {
            total += share;
        }

        if (total <= 0) {
            return YautjaTier.YOUNGBLOOD;
        }

        var pick = random.nextInt(total);

        for (var rung = 0; rung < RUNGS.length; rung++) {
            pick -= entry.ladder[rung];

            if (pick < 0) {
                return RUNGS[rung];
            }
        }

        return RUNGS[entry.unlockedRungs - 1];
    }

    /** {@return the ladder as "YB 70% / BL 20% / EL 10%", unlocked rungs only} — for the status command. */
    public static String describe(HuntLedger.Entry entry) {
        var names = new String[] { "Youngblood", "Blooded", "Elite", "Elder", "Clan Leader" };
        var text = new StringBuilder();

        for (var rung = 0; rung < entry.unlockedRungs; rung++) {
            if (rung > 0) {
                text.append(" / ");
            }

            text.append(names[rung]).append(' ').append(entry.ladder[rung]).append('%');
        }

        return text.toString();
    }
}
