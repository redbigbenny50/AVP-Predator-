package com.predator.client.sound;

import com.predator.common.gameplay.entity.projectile.SmartDiscProjectile;
import net.minecraft.client.Minecraft;

/**
 * Client entry point for the disc's flight hum.
 * <p>
 * ⚠ A one-method class so the projectile — which runs on BOTH sides — never names {@code Minecraft} directly. Common
 * code that mentions a client class loads fine on a client and explodes on a dedicated server, and it is the kind of
 * mistake that only ever shows up in someone else's crash report.
 */
public final class SmartDiscClientSounds {

    private SmartDiscClientSounds() {
        throw new UnsupportedOperationException();
    }

    public static void startLoop(SmartDiscProjectile disc) {
        Minecraft.getInstance().getSoundManager().play(new SmartDiscSoundInstance(disc));
    }
}
