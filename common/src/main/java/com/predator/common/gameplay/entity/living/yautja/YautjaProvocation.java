package com.predator.common.gameplay.entity.living.yautja;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Oct 8 - A PLAYER WHO STRIKES A YAUTJA STAYS ITS PREY.
 * <p>
 * [stated] "if the player struck first it would keep chasing it longer". The honour code makes an unarmed player prey
 * only while he is the yautja's LAST ATTACKER - and vanilla forgets the last attacker 5 seconds after the hit
 * (LivingEntity.baseTick, 100 ticks). A tester who struck one, put the weapon away and climbed a pillar was dropped
 * mid-chase: the path-debug log showed it looking at him with no attack target in its AI ("stood and stared",
 * "forgetting about the player"), and with no valid target nothing else - grenades included - would act either.
 * <p>
 * Now the striker stays prey for {@value #PROVOKED_TICKS} ticks after his LAST hit (each hit refreshes it), unless he
 * gets more than {@value #ESCAPE_DISTANCE} blocks away - then he has escaped and the hunt ends. Creative and spectator
 * players never provoke. An unarmed player who never touched it is still left alone.
 */
public final class YautjaProvocation {

    /** 60 seconds after the last hit. */
    public static final int PROVOKED_TICKS = 1200;

    /** Further than this and the provoker has escaped. */
    public static final double ESCAPE_DISTANCE = 64.0;

    private record Grudge(
        UUID player,
        long until
    ) {}

    /** Server thread only; entries die with the yautja. */
    private static final Map<Yautja, Grudge> GRUDGES = new WeakHashMap<>();

    private YautjaProvocation() {}

    /** Records (or refreshes) a strike by a player. */
    public static void struckBy(Yautja yautja, Player player) {
        GRUDGES.put(yautja, new Grudge(player.getUUID(), yautja.level().getGameTime() + PROVOKED_TICKS));
    }

    /** {@return whether this player struck the yautja recently enough, and is still near enough, to be hunted} */
    public static boolean isProvokedBy(Yautja yautja, Player player) {
        var grudge = GRUDGES.get(yautja);

        if (grudge == null || !grudge.player().equals(player.getUUID())) {
            return false;
        }

        if (yautja.level().getGameTime() > grudge.until() || yautja.distanceToSqr(player) > ESCAPE_DISTANCE * ESCAPE_DISTANCE) {
            GRUDGES.remove(yautja);
            return false;
        }

        return true;
    }
}
