package com.predator.client.compatibility;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side compatibility helpers for the predator module.
 * <p>
 * The Sodium check previously routed through Architectury's {@code dev.architectury.platform.Platform}, which is not a
 * declared or shipped dependency of this project — causing a {@link NoClassDefFoundError} at runtime the first time a
 * predator vision post effect evaluated {@code shouldRun()}. The check is now performed reflectively against the active
 * mod loader (Fabric Loader or NeoForge), with no compile-time dependency on either, and the result is cached so the
 * per-frame render path never pays the reflection cost more than once.
 * </p>
 */
public final class PredatorClientCompatibility {

    private static final Component SODIUM_VISION_INCOMPATIBLE_MESSAGE = Component.literal(
        "Predator visions are currently incompatible with Sodium and will work with it soon in a future update."
    );

    private static Boolean sodiumLoadedCache;

    private PredatorClientCompatibility() {
        throw new UnsupportedOperationException();
    }

    public static boolean isSodiumLoaded() {
        Boolean cached = sodiumLoadedCache;

        if (cached == null) {
            cached = detectModLoaded("sodium");
            sodiumLoadedCache = cached;
        }

        return cached;
    }

    public static boolean areVisionsDisabledBySodium() {
        return isSodiumLoaded();
    }

    public static void sendSodiumVisionIncompatibleMessage(Player player) {
        player.displayClientMessage(SODIUM_VISION_INCOMPATIBLE_MESSAGE, false);
    }

    /**
     * Loader-agnostic mod presence check usable from common code.
     * <p>
     * Tries Fabric Loader first, then NeoForge's mod list. Both lookups are reflective, so this class has no
     * compile-time dependency on either loader and can never trigger a {@link NoClassDefFoundError} when the other
     * loader is absent. Any failure is treated as "not loaded".
     * </p>
     */
    private static boolean detectModLoaded(String modId) {
        // Fabric / Quilt
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object fabricLoader = fabricLoaderClass.getMethod("getInstance").invoke(null);
            Object result = fabricLoaderClass.getMethod("isModLoaded", String.class).invoke(fabricLoader, modId);

            return result instanceof Boolean loaded && loaded;
        } catch (Throwable ignored) {
            // Not running on Fabric — fall through to NeoForge.
        }

        // NeoForge
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            Object result = modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);

            return result instanceof Boolean loaded && loaded;
        } catch (Throwable ignored) {
            // Not running on NeoForge either.
        }

        return false;
    }
}
