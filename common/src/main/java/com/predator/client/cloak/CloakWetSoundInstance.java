package com.predator.client.cloak;

import com.predator.common.gameplay.cloak.PredatorCloak;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The electrical crackle while water is shorting an engaged cloak out.
 * <p>
 * Tickable and entity-following: it tracks the wearer as they move, and when the condition ends it fades rather than
 * cutting, so stepping out of a river tails off instead of stopping mid-crackle.
 * <p>
 * ⚠ Tied to the SHORTING-OUT condition, not to being wet. An uncloaked wearer standing in rain makes no sound, and a
 * cloaked one whose field has already overloaded goes quiet too — the noise is the field arcing, so it stops when there
 * is no field left to arc.
 */
public class CloakWetSoundInstance extends AbstractTickableSoundInstance {

    /** How quickly the loop fades once the condition ends. Reached from full over roughly half a second. */
    private static final float FADE_PER_TICK = 0.08F;

    private static final float TARGET_VOLUME = 0.8F;

    private final LivingEntity wearer;

    public CloakWetSoundInstance(LivingEntity wearer) {
        super(PredatorSoundEvents.CLOAK_WET_LOOP.get(), SoundSource.PLAYERS, wearer.getRandom());

        this.wearer = wearer;
        this.looping = true;
        this.delay = 0;
        this.volume = TARGET_VOLUME;
        this.x = (float) wearer.getX();
        this.y = (float) wearer.getY();
        this.z = (float) wearer.getZ();
    }

    public LivingEntity wearer() {
        return wearer;
    }

    @Override
    public void tick() {
        if (wearer.isRemoved()) {
            stop();
            return;
        }

        x = (float) wearer.getX();
        y = (float) wearer.getY();
        z = (float) wearer.getZ();

        if (PredatorCloak.isShortingOut(wearer)) {
            volume = Math.min(TARGET_VOLUME, volume + FADE_PER_TICK);
            return;
        }

        volume -= FADE_PER_TICK;

        if (volume <= 0.0F) {
            volume = 0.0F;
            stop();
        }
    }
}
