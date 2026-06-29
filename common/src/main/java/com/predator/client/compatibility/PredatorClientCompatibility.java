package com.predator.client.compatibility;

import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class PredatorClientCompatibility {

    private static final Component SODIUM_VISION_INCOMPATIBLE_MESSAGE = Component.literal(
        "Predator visions are currently incompatible with Sodium and will work with it soon in a future update."
    );

    private PredatorClientCompatibility() {
        throw new UnsupportedOperationException();
    }

    public static boolean isSodiumLoaded() {
        return Platform.isModLoaded("sodium");
    }

    public static boolean areVisionsDisabledBySodium() {
        return isSodiumLoaded();
    }

    public static void sendSodiumVisionIncompatibleMessage(Player player) {
        player.displayClientMessage(SODIUM_VISION_INCOMPATIBLE_MESSAGE, false);
    }
}
