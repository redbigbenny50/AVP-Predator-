package com.predator.compatibility.walkers.client;

import com.blib.api.BLibAPI;
import com.blib.api.common.mod.v1.BLibMod;
import com.predator.Predator;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Proxy;

/**
 * Makes the yautja animate when a player is morphed into one with ReMorphed / Walkers.
 * <p>
 * [stated] Oct 23 - morphs into our models did not animate; "add the remorph fixes as well". The same hook as
 * avp_alien's own copy of this class, registered for avp_predator's entity types, so each mod works on its own.
 * </p>
 * <p>
 * Walkers draws a morph as a client-side COPY of the mob and never ticks it. The yautja picks its body clip in its own
 * client tick, so the copy never picked one. Walkers' {@code EntityUpdater} hook is called on the copy every frame,
 * right after Walkers copies the player onto it and right before it is drawn; we use it to run the yautja's own clip
 * choice from the player ({@link Yautja#tickAsMorph}). Walkers only ever hands this hook its morph copies, so a real
 * yautja is never touched.
 * </p>
 * <p>
 * ⚠ SOFT DEPENDENCY BY REFLECTION: avp_predator does not compile against Walkers and touches none of its classes unless
 * Walkers is installed. A failure is logged once and morphs simply look as they did before.
 * </p>
 */
public final class WalkersShapeAnimations {

    private static final BLibMod MOD = BLibAPI.createMod("walkers");

    private static final String UPDATERS_CLASS = "tocraft.walkers.api.model.EntityUpdaters";

    private static final String UPDATER_INTERFACE = "tocraft.walkers.api.model.EntityUpdater";

    private WalkersShapeAnimations() {}

    /** The resolved Walkers hook, kept until our entity types exist to be registered with it. */
    private static java.lang.reflect.Method registerMethod;

    private static Object updater;

    /** True from {@link #register} until our entity types have actually been handed to Walkers. */
    private static boolean pending;

    /**
     * Call once from client setup. Does nothing when Walkers is not installed.
     * <p>
     * 🚨 Oct 23 - [stated] "still animations not working per tester." His log said it outright: "Walkers detected:
     * morph animations hooked for 0 avp_predator entity types". On NeoForge this runs while mods are being CONSTRUCTED,
     * before any entity type is registered, so the registry walk found nothing of ours and nothing was hooked (Fabric
     * runs client setup after registration, which is why it compiled and looked right). Now this only resolves the
     * hook; the types are handed over by {@link #retryIfPending} on the first client tick that can see them.
     * </p>
     */
    public static void register() {
        if (!MOD.isLoaded()) {
            return;
        }

        try {
            var updatersClass = Class.forName(UPDATERS_CLASS);
            var updaterInterface = Class.forName(UPDATER_INTERFACE);
            registerMethod = updatersClass.getMethod("register", EntityType.class, updaterInterface);
            updater = Proxy.newProxyInstance(
                updaterInterface.getClassLoader(),
                new Class<?>[] { updaterInterface },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "update" -> {
                            if (
                                args != null && args.length == 2 && args[0] instanceof Player player
                                    && args[1] instanceof LivingEntity shape
                            ) {
                                update(player, shape);
                            }
                            return null;
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return args != null && args.length == 1 && proxy == args[0];
                        }
                        case "toString" -> {
                            return "avp_predator Walkers shape animations";
                        }
                        default -> {
                            return null;
                        }
                    }
                }
            );
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            Predator.LOGGER.warn("Walkers detected but its shape hook could not be found; morphs will not animate", exception);
            return;
        }

        pending = true;
        retryIfPending();
    }

    /**
     * Hands our entity types to Walkers once they exist. Called every client tick; a single boolean check once done.
     * Until our types are registered it simply finds none and waits for the next tick.
     */
    public static void retryIfPending() {
        if (!pending) {
            return;
        }

        var registered = 0;

        try {
            for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
                if (Predator.MOD_ID.equals(entry.getKey().location().getNamespace())) {
                    registerMethod.invoke(null, entry.getValue(), updater);
                    registered++;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            pending = false;
            Predator.LOGGER.warn("Walkers detected but its shape hook could not be registered; morphs will not animate", exception);
            return;
        }

        if (registered == 0) {
            return;
        }

        pending = false;
        Predator.LOGGER.info("Walkers detected: morph animations hooked for {} avp_predator entity types", registered);
    }

    /** One frame of a morphed player's copy. Only ever handed a Walkers copy - never a real mob. */
    private static void update(Player player, LivingEntity shape) {
        if (shape instanceof Yautja yautja) {
            yautja.tickAsMorph(player);
        }
    }
}
