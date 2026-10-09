package com.predator.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Client entry point for the plasma bow's hold loop.
 * <p>
 * ⚠ A one-method class so PlasmaBowItem — which runs on BOTH sides — never names {@code Minecraft}. Common code that
 * mentions a client class loads fine on a client and dies on a dedicated server.
 */
public final class PlasmaBowClientSounds {

    private PlasmaBowClientSounds() {
        throw new UnsupportedOperationException();
    }

    public static void startHold(Player player) {
        Minecraft.getInstance().getSoundManager().play(new PlasmaBowHoldSound(player));
    }
}
