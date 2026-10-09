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
        } else {
            CLOAKED.remove(entityId);
        }
    }

    public static boolean isCloaked(LivingEntity entity) {
        return !CLOAKED.isEmpty() && CLOAKED.contains(entity.getId());
    }

    /** Dropped on world change — ids are only meaningful within one level. */
    /** {@return the entity ids currently believed cloaked} Used by the wet-loop sound manager. */
    public static java.util.Set<Integer> cloakedIds() {
        return CLOAKED;
    }

    public static void clear() {
        CLOAKED.clear();
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
