package com.predator.client.cloak;

import com.predator.common.gameplay.cloak.PredatorCloak;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of which entity ids currently have their cloak field up, fed by {@code S2CCloakStatePayload}.
 * <p>
 * Entity ids rather than UUIDs because that is what the packet carries and what the renderer has in hand; ids are
 * stable for the lifetime of a tracked entity, and a stale id simply never matches anything again.
 */
public final class PredatorCloakClientState {

    private static final Set<Integer> CLOAKED = ConcurrentHashMap.newKeySet();

    /**
     * Oct 10 - WHICH ENTITY each cloaked id belonged to. Network ids are reused, and a cloaked mob that died or left
     * tracking range never gets an "uncloaked" packet, so its id stayed in {@link #CLOAKED} - and the next entity the
     * client was handed that id rendered as a cloak shimmer (and kept the cloak's screen copy running for nothing).
     * Same flaw as the capture chain drawn to a stranger in avp_alien. The UUID is bound the first time the id is
     * resolved and a mismatch drops the entry.
     */
    private static final java.util.Map<Integer, java.util.UUID> OWNER = new ConcurrentHashMap<>();

    private PredatorCloakClientState() {
        throw new UnsupportedOperationException();
    }

    /** Installs the client view into the common facade. Called once from client init. */
    public static void install() {
        PredatorCloak.setClientView(PredatorCloakClientState::isCloaked);
    }

    public static void set(int entityId, boolean cloaked) {
        if (cloaked) {
            CLOAKED.add(entityId);
            var level = Minecraft.getInstance().level;
            var entity = level == null ? null : level.getEntity(entityId);

            if (entity != null) {
                OWNER.put(entityId, entity.getUUID());
            } else {
                OWNER.remove(entityId); // bound on first sight
            }
        } else {
            CLOAKED.remove(entityId);
            OWNER.remove(entityId);
        }
    }

    public static boolean isCloaked(LivingEntity entity) {
        if (CLOAKED.isEmpty() || !CLOAKED.contains(entity.getId())) {
            return false;
        }

        var owner = OWNER.putIfAbsent(entity.getId(), entity.getUUID());

        if (owner != null && !owner.equals(entity.getUUID())) {
            // The id now belongs to someone else: the cloak it recorded is long gone.
            CLOAKED.remove(entity.getId());
            OWNER.remove(entity.getId());
            return false;
        }

        return true;
    }

    /** Dropped on world change — ids are only meaningful within one level. */
    /** {@return the entity ids currently believed cloaked} Used by the wet-loop sound manager. */
    public static java.util.Set<Integer> cloakedIds() {
        return CLOAKED;
    }

    public static void clear() {
        CLOAKED.clear();
        OWNER.clear();
    }

    /**
     * {@return {@code true} when the local player should see through {@code entity}'s cloak anyway}
     * <p>
     * A cloaked predator is invisible to other predators' normal sight — that is the film's rule and it is what makes
     * two hunters in one world interesting. EM vision is the counter: the cloak defeats thermal by design but leaks an
     * EM signature, which is handled in {@code PredatorVisionClassification} rather than here.
     * <p>
     * ⚠ THE WEARER IS NOT EXEMPT. An earlier version returned {@code true} for {@code camera == entity}, reasoning that
     * a hunter sees their own body normally — and that made the whole feature invisible to the only person who can
     * confirm it works. In third person you must see your own cloak, exactly as vanilla invisibility shows you a
     * translucent self, or there is no feedback that the device did anything. First-person hands are unaffected either
     * way: they render through {@code ItemInHandRenderer}, not {@code EntityRenderDispatcher}, so this hook never
     * touches them.
     */
    public static boolean seesThrough(LivingEntity entity) {
        return PredatorCloak.isImmune(Minecraft.getInstance().player);
    }
}
