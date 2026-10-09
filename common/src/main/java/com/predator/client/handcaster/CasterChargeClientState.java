package com.predator.client.handcaster;

import net.minecraft.world.entity.LivingEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of which entity ids are currently winding up a hand-caster shot, fed by
 * {@code S2CCasterChargePayload}. Read by the third-person arm pose.
 * <p>
 * ⚠ Keyed by ENTITY ID, not by "is this me". The pose mixin runs once per rendered player, so it has to be able to ask
 * about any of them - including remote players, which is the entire reason the state is synced.
 * </p>
 */
public final class CasterChargeClientState {

    private static final Set<Integer> CHARGING = ConcurrentHashMap.newKeySet();

    private CasterChargeClientState() {
        throw new UnsupportedOperationException();
    }

    public static void set(int entityId, boolean charging) {
        if (charging) {
            CHARGING.add(entityId);
        } else {
            CHARGING.remove(entityId);
        }
    }

    public static boolean isCharging(LivingEntity entity) {
        return entity != null && CHARGING.contains(entity.getId());
    }

    /** ⚠ Called on disconnect: ids are per-session and a stale one would raise a stranger's arm next world. */
    public static void clear() {
        CHARGING.clear();
    }
}
