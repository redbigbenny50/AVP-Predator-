package com.predator.client.effect;

import com.predator.common.gameplay.effect.PredatorMud;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client cache of who is muddy, fed by {@code S2CMudStatePayload}.
 * <p>
 * Stores an <em>expiry game-time</em> rather than a countdown, so nothing here needs a client tick hook: remaining
 * duration is derived from the level's current game time whenever it is asked for, and an entry that has run out simply
 * stops matching. The server refreshes each muddy entity about once a second, which keeps the two in step.
 */
public final class PredatorMudClientState {

    private static final Map<Integer, Long> EXPIRY_BY_ENTITY_ID = new ConcurrentHashMap<>();

    private PredatorMudClientState() {
        throw new UnsupportedOperationException();
    }

    public static void install() {
        PredatorMud.setClientView(PredatorMudClientState::remainingTicks);
    }

    public static void set(int entityId, int remainingTicks, long gameTime) {
        if (remainingTicks <= 0) {
            EXPIRY_BY_ENTITY_ID.remove(entityId);
            return;
        }

        EXPIRY_BY_ENTITY_ID.put(entityId, gameTime + remainingTicks);
    }

    public static int remainingTicks(LivingEntity entity) {
        if (EXPIRY_BY_ENTITY_ID.isEmpty()) {
            return 0;
        }

        var expiry = EXPIRY_BY_ENTITY_ID.get(entity.getId());

        if (expiry == null) {
            return 0;
        }

        var remaining = expiry - entity.level().getGameTime();

        if (remaining <= 0L) {
            EXPIRY_BY_ENTITY_ID.remove(entity.getId());
            return 0;
        }

        return (int) Math.min(remaining, Integer.MAX_VALUE);
    }

    public static void clear() {
        EXPIRY_BY_ENTITY_ID.clear();
    }
}
