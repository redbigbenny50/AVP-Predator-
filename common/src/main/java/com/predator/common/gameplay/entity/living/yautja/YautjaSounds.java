package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The yautja's movement vocals: his exertion recordings, played when it climbs, vaults and leaps. A small pitch wobble
 * on every call keeps repeats from sounding canned. Server side; nearby players hear it like any mob sound.
 */
public final class YautjaSounds {

    private YautjaSounds() {}

    /** Climbing: a vault over an obstacle, a mantle over a lip, a standing jump. */
    public static void climb(Yautja yautja) {
        play(yautja, PredatorSoundEvents.YAUTJA_CLIMB.get());
    }

    /** A long jump: the running leap across a gap, and the fall-back leap away from a threat. */
    public static void longJump(Yautja yautja) {
        play(yautja, PredatorSoundEvents.YAUTJA_LONG_JUMP.get());
    }

    /** Chance a plain swing gets a vocal. Every swing of a fast combo shouting would be a lot. */
    public static float ATTACK_VOCAL_CHANCE = 0.6F;

    /** A weapon swing: one of his attack grunts, on most swings. */
    public static void attack(Yautja yautja) {
        if (yautja.getRandom().nextFloat() < ATTACK_VOCAL_CHANCE) {
            play(yautja, PredatorSoundEvents.YAUTJA_ATTACK.get());
        }
    }

    /** The big ones: the battleaxe slam and a combo's finishing blow. His PRED_YELL_SHORT, every time. */
    public static void heavyAttack(Yautja yautja) {
        play(yautja, PredatorSoundEvents.YAUTJA_HEAVY_ATTACK.get());
    }

    private static void play(Yautja yautja, SoundEvent sound) {
        if (yautja.level().isClientSide || yautja.isSilent()) {
            return;
        }

        yautja.level()
            .playSound(
                null,
                yautja.getX(),
                yautja.getY(),
                yautja.getZ(),
                sound,
                SoundSource.HOSTILE,
                1.0F,
                0.95F + yautja.getRandom().nextFloat() * 0.1F
            );
    }
}
