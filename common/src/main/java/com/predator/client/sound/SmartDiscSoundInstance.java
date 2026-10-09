package com.predator.client.sound;

import com.predator.common.gameplay.entity.projectile.SmartDiscProjectile;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * The disc's flight hum, for as long as the disc is flying.
 * <h2>⚠⚠ WHY A TICKABLE INSTANCE AND NOT level.playSound</h2> A server-side {@code playSound} fires once and is then
 * beyond reach: it cannot follow the disc, and nothing can stop it when the disc comes home, is caught, or is dropped —
 * the hum would carry on from wherever the throw happened. This instance rides the entity, so its position updates
 * every tick and it stops ITSELF the moment the disc is gone. No packets, no bookkeeping.
 */
public class SmartDiscSoundInstance extends AbstractTickableSoundInstance {

    private final SmartDiscProjectile disc;

    public SmartDiscSoundInstance(SmartDiscProjectile disc) {
        super(PredatorSoundEvents.SMART_DISC_LOOP.get(), SoundSource.PLAYERS, disc.getRandom());

        this.disc = disc;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.x = disc.getX();
        this.y = disc.getY();
        this.z = disc.getZ();
    }

    @Override
    public void tick() {
        if (disc.isRemoved() || !disc.isAlive()) {
            stop();

            return;
        }

        x = disc.getX();
        y = disc.getY();
        z = disc.getZ();
    }
}
