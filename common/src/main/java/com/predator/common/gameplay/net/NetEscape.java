package com.predator.common.gameplay.net;

import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * How likely a netted creature is to get out, and what happens when it fails.
 * <h2>His spec (Sep 16)</h2> Small or passive: captured at once, no struggle. Medium (zombie, skeleton) 5%.
 * Medium-large (drone, warrior) 10%. Large (praetorian, ravager) 15%. Anything bigger 20%. A roll every ten seconds;
 * three failures in a row and it is captured for good. A failed roll hurts: "10% or two hearts whichever is less".
 */
public final class NetEscape {

    /** Ticks between struggles, and therefore between escape rolls. */
    public static int ROLL_INTERVAL_TICKS = 200;

    /** Failed rolls in a row before the net closes for good. */
    public static int ROLLS_BEFORE_CAPTURE = 3;

    public static float CHANCE_MEDIUM = 0.05F;

    public static float CHANCE_MEDIUM_LARGE = 0.10F;

    public static float CHANCE_LARGE = 0.15F;

    public static float CHANCE_HUGE = 0.20F;

    /** A failed roll costs this share of max health, capped by {@link #STRUGGLE_DAMAGE_CAP}. */
    public static float STRUGGLE_DAMAGE_FRACTION = 0.10F;

    /** Two hearts. */
    public static float STRUGGLE_DAMAGE_CAP = 4.0F;

    // Bounding-box fallbacks, used only when nothing tags the entity.
    private static final double MEDIUM_LARGE_WIDTH = 0.99D;

    private static final double LARGE_WIDTH = 1.4D;

    private static final double HUGE_WIDTH = 2.2D;

    private NetEscape() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return the chance per roll, or 0 for anything captured outright}
     * <p>
     * ⚠ TAGS FIRST, SIZE SECOND. The four tier tags let a datapack place any mob — modded ones included — exactly where
     * it belongs; the bounding box is only the fallback so an untagged mod mob still behaves sensibly instead of
     * defaulting to "escapes like a queen".
     */
    public static float escapeChance(LivingEntity living) {
        var type = living.getType();

        if (type.is(PredatorEntityTypeTags.NET_ESCAPE_HUGE)) {
            return CHANCE_HUGE;
        }

        if (type.is(PredatorEntityTypeTags.NET_ESCAPE_LARGE)) {
            return CHANCE_LARGE;
        }

        if (type.is(PredatorEntityTypeTags.NET_ESCAPE_MEDIUM_LARGE)) {
            return CHANCE_MEDIUM_LARGE;
        }

        if (type.is(PredatorEntityTypeTags.NET_ESCAPE_MEDIUM)) {
            return CHANCE_MEDIUM;
        }

        if (type.is(PredatorEntityTypeTags.NET_ESCAPE_NONE) || isHarmless(living)) {
            return 0.0F;
        }

        var width = living.getBbWidth();

        if (width >= HUGE_WIDTH) {
            return CHANCE_HUGE;
        }

        if (width >= LARGE_WIDTH) {
            return CHANCE_LARGE;
        }

        if (width >= MEDIUM_LARGE_WIDTH) {
            return CHANCE_MEDIUM_LARGE;
        }

        return CHANCE_MEDIUM;
    }

    /**
     * {@return whether this creature can never fight back, so the net simply holds it}
     * <p>
     * [stated] "if its a passive creature like a villager or a cow something that cant become aggressive it also is
     * captured." A cow and a villager have no attack damage and are not hostile, so they qualify; a wolf, a bee or an
     * iron golem all carry attack damage and still get their rolls.
     */
    public static boolean isHarmless(LivingEntity living) {
        if (living instanceof Enemy) {
            return false;
        }

        var attack = living.getAttribute(Attributes.ATTACK_DAMAGE);

        return attack == null || attack.getValue() <= 0.0D;
    }

    /** {@return the damage a failed struggle costs} [stated] "10% or two hearts whichever is less". */
    public static float struggleDamage(LivingEntity living) {
        return Math.min(living.getMaxHealth() * STRUGGLE_DAMAGE_FRACTION, STRUGGLE_DAMAGE_CAP);
    }

    /** {@return whether this mob should be captured the moment the net lands, with no struggling at all} */
    public static boolean capturedOnContact(LivingEntity living) {
        return escapeChance(living) <= 0.0F;
    }

    /** {@return whether the mob is one the net can hold at all} Everything living, minus the immune tag. */
    public static boolean canBeHeld(LivingEntity living) {
        return living instanceof Mob || living instanceof PathfinderMob;
    }
}
