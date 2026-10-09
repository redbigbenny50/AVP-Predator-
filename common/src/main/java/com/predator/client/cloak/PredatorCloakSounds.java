package com.predator.client.cloak;

import com.predator.common.gameplay.cloak.PredatorCloak;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Starts and retires the wet-cloak loop, one instance per shorting-out wearer.
 * <p>
 * Driven from the level-render hook rather than a client tick event because that hook already exists for the scene
 * capture and is guaranteed to run every frame the world is drawn. The scan is over the cloak cache, not all entities,
 * so it costs nothing when nobody is cloaked.
 */
public final class PredatorCloakSounds {

    private static final Map<Integer, CloakWetSoundInstance> ACTIVE = new HashMap<>();

    private PredatorCloakSounds() {
        throw new UnsupportedOperationException();
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;

        if (level == null) {
            clear();
            return;
        }

        ACTIVE.entrySet().removeIf(entry -> entry.getValue().isStopped());

        for (var entityId : PredatorCloakClientState.cloakedIds()) {
            if (ACTIVE.containsKey(entityId)) {
                continue;
            }

            if (!(level.getEntity(entityId) instanceof LivingEntity wearer) || !PredatorCloak.isShortingOut(wearer)) {
                continue;
            }

            var instance = new CloakWetSoundInstance(wearer);
            ACTIVE.put(entityId, instance);
            minecraft.getSoundManager().play(instance);
        }
    }

    /** Dropped on world change — the instances reference entities that no longer exist. */
    public static void clear() {
        ACTIVE.clear();
    }
}
