package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * "Am I losing this?" — the two readings the fall-back goal and the adrenaline rush both key on, in one place so they
 * can never disagree.
 * <h2>His spec (Sep 13)</h2> Fall back when "losing health quickly and/or outnumbered"; the rush "when outnumbered by 3
 * or more enemies"; and while outnumbered, "retarget to the weakest enemy so it can clear adds before refocusing on the
 * bigger target".
 */
public final class YautjaThreatAssessment {

    /** How many hostiles within {@link #CROWD_RADIUS} count as outnumbered. */
    public static int CROWD_COUNT = 3;

    public static double CROWD_RADIUS = 12.0D;

    /** Fraction of max health that must be lost inside {@link #BURST_WINDOW_TICKS} to count as bleeding fast. */
    public static float BURST_FRACTION = 0.25F;

    public static int BURST_WINDOW_TICKS = 60;

    /** The rush's duration, and how long before it can be granted again. */
    public static int RUSH_DURATION_TICKS = 20 * 60;

    public static int RUSH_COOLDOWN_TICKS = 20 * 90;

    private YautjaThreatAssessment() {
        throw new UnsupportedOperationException();
    }

    /** {@return everything hostile within CROWD_RADIUS that can actually be fought} */
    /**
     * ⭐ Oct 6 - performance. One yautja asked for its crowd several times in the same tick - the adrenaline check in
     * its own tick, the net goal, the battleaxe slam's count, the fall-back goal - and each ask was a fresh entity
     * query plus a line-of-sight ray per candidate. The answer is now kept for the rest of that game tick and handed
     * back (unmodifiable) to every later caller; the next tick asks the world again. Weak-keyed, so a removed yautja is
     * not held.
     */
    private static final java.util.Map<Yautja, CrowdSnapshot> CROWD_THIS_TICK = new java.util.WeakHashMap<>();

    private record CrowdSnapshot(
        long gameTime,
        List<LivingEntity> crowd
    ) {}

    public static List<LivingEntity> crowd(Yautja yautja) {
        // Server thread only: the cache map is not thread-safe, and the client never needs the crowd.
        if (yautja.level().isClientSide) {
            return scanCrowd(yautja);
        }

        var gameTime = yautja.level().getGameTime();
        var cached = CROWD_THIS_TICK.get(yautja);

        if (cached != null && cached.gameTime() == gameTime) {
            return cached.crowd();
        }

        var fresh = java.util.List.copyOf(scanCrowd(yautja));
        CROWD_THIS_TICK.put(yautja, new CrowdSnapshot(gameTime, fresh));

        return fresh;
    }

    private static List<LivingEntity> scanCrowd(Yautja yautja) {
        return yautja.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                yautja.getBoundingBox().inflate(CROWD_RADIUS),
                candidate -> candidate != yautja
                    && candidate.isAlive()
                    && !candidate.isSpectator()
                    && !(candidate instanceof Player player && (player.isCreative() || player.isSpectator()))
                    && !(candidate instanceof Yautja)
                    && isFightingIt(yautja, candidate)
                    && yautja.hasLineOfSight(candidate)
            );
    }

    /**
     * {@return whether {@code candidate} is actually in this fight} — its own target, something targeting it, or
     * something that has hit it in the last {@link Yautja#RECENT_ATTACKER_TICKS}.
     * <p>
     * 🚨 The old test was {@code yautja.canAttack(candidate)}, which is true for almost every living thing — cows,
     * sheep, villagers, a passing chicken. A Hunter in a 1-v-1 sword fight next to two grazing sheep counted three
     * enemies and took its adrenaline rush ([tester] "his hunter has adrenaline rush in a 1vs 1 melee fight"). [stated]
     * the rush is for "when outnumbered by 3 or more enemies" — enemies, not bystanders. The fall-back leap and the net
     * read the same count, so they were firing on bystanders too.
     */
    public static boolean isFightingIt(Yautja yautja, LivingEntity candidate) {
        if (candidate == yautja.getTarget()) {
            return true;
        }

        if (candidate instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == yautja) {
            return true;
        }

        return yautja.wasRecentlyAttackedBy(candidate);
    }

    public static boolean isOutnumbered(Yautja yautja) {
        return crowd(yautja).size() >= CROWD_COUNT;
    }

    /** {@return whether it has lost BURST_FRACTION of its max health inside the window} */
    public static boolean isBleedingFast(Yautja yautja) {
        return yautja.getRecentDamage() >= yautja.getMaxHealth() * BURST_FRACTION;
    }

    /**
     * {@return the weakest thing in the crowd, or null} ⚠ Lowest CURRENT health, not lowest max: an almost-dead brute
     * is a faster kill than a fresh weakling, and the point is to thin the crowd. [stated] "retarget to the weakest
     * enemy so it can clear adds before refocusing on the bigger target."
     */
    public static @Nullable LivingEntity weakest(List<LivingEntity> crowd) {
        LivingEntity weakest = null;

        for (var candidate : crowd) {
            if (weakest == null || candidate.getHealth() < weakest.getHealth()) {
                weakest = candidate;
            }
        }

        return weakest;
    }

    /** Grants the rush if it is outnumbered and off cooldown. Server side. */
    public static void tickRush(Yautja yautja) {
        if (yautja.tickCount < yautja.getNextAdrenalineTick() || !isOutnumbered(yautja)) {
            return;
        }

        yautja.addEffect(new MobEffectInstance(PredatorMobEffects.getAdrenalineRushHolder(), RUSH_DURATION_TICKS, 0, false, true, true));
        yautja.setAdrenalineCooldown(RUSH_DURATION_TICKS + RUSH_COOLDOWN_TICKS);
    }
}
