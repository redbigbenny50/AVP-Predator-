package com.predator.client.net;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which entity ids the client believes are netted.
 * <h2>⚠ Entity IDs, not UUIDs</h2> The render layer is handed an entity and needs an answer in the same frame, and
 * {@code getId()} is the cheap lookup. The server sends ids for the same reason avp_alien's capture hold payload does.
 * <p>
 * ⚠ Cleared on disconnect. Entity ids are per-session and are reused, so a stale set would put a net on whatever
 * unlucky mob inherited the number in the next world.
 */
public final class ClientNetState {

    /**
     * ⚠ Concurrent, matching {@code PredatorCloakClientState}. The render thread reads this while the network thread
     * writes it, and a plain HashSet would be a rare, unreproducible crash rather than an obvious one.
     */
    private static final Set<Integer> NETTED = ConcurrentHashMap.newKeySet();

    private ClientNetState() {
        throw new UnsupportedOperationException();
    }

    public static boolean isNetted(int entityId) {
        return NETTED.contains(entityId);
    }

    public static void set(int entityId, boolean netted) {
        if (netted) {
            NETTED.add(entityId);
        } else {
            NETTED.remove(entityId);
        }
    }

    public static void clear() {
        NETTED.clear();
    }
}
