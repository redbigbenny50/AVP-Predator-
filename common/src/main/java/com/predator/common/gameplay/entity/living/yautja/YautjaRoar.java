package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.registry.init.PredatorMobEffects;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The roar a yautja gives when its mask comes off.
 * <h2>His spec</h2> "the roar animation uses the predator roar sound effect and activates when the mask breaks. like
 * the queens scream this will stun opponents for a few seconds and then it has a 90s cool down."
 * <h2>⚠ The mask coming off is the trigger, and it already existed</h2> {@code Yautja.checkMask} drops the helmet the
 * moment health falls below half. That was already in the mod; the roar hangs off it rather than re-deriving the
 * condition, so the two can never disagree about when the mask broke.
 * <p>
 * ⚠ WHICH ALSO MEANS THE COOLDOWN IS MOSTLY THEORETICAL. A mask breaks once — {@code checkMask} early-outs on
 * {@code !hasMask()} and nothing puts it back. The 90 seconds is there because he asked for it and because a future
 * second trigger (a re-masked yautja, a scripted taunt) would otherwise be able to chain-stun.
 * <h2>The visual half was already built</h2> ⚠ {@code YautjaAnimator.showHelmet} hides {@code gArmorMask} whenever
 * {@code hasMask()} is false, and the mask bones are its children. So by the time the roar plays the mask is already
 * gone from the model — his "the mask actually just goes invisible/groups turn off" needs no new code, and the clip's
 * mask-lifting motion animates a bone nobody can see.
 */
public final class YautjaRoar {

    /** 90 seconds, his figure. */
    public static final int COOLDOWN_TICKS = 1800;

    /** How long the roar clip runs before the body returns to normal locomotion. Matches the 1.5s clip. */
    public static final int ROAR_TICKS = 30;

    /**
     * How long the stun holds. His "a few seconds".
     * <p>
     * ⚠ Deliberately shorter than the roar itself. Being frozen should end while the yautja is still closing, so the
     * stun is what lets it reach you rather than what kills you.
     */
    private static final int STUN_TICKS = 60;

    /**
     * Blocks. Everything hostile inside this is stunned.
     * <p>
     * ⚠ Half the queen's 32. Hers is a hive-wide alarm; this is one hunter losing its temper, and a stun that reached
     * across a whole valley would catch people who never saw it happen.
     */
    private static final double RADIUS = 16.0;

    private YautjaRoar() {
        throw new UnsupportedOperationException();
    }

    /**
     * Roars: plays the sound and stuns everything nearby that could plausibly be fighting it.
     * <p>
     * ⚠ Skips other yautja. A predator is not stunned by another predator's roar — they share the trait, and a pack
     * freezing itself would be absurd.
     */
    public static void roar(ServerLevel level, Yautja yautja) {
        level.playSound(
            null,
            yautja.getX(),
            yautja.getY(),
            yautja.getZ(),
            PredatorSoundEvents.YAUTJA_ROAR.get(),
            SoundSource.HOSTILE,
            2.0F,
            1.0F
        );

        var victims = level.getEntitiesOfClass(
            LivingEntity.class,
            yautja.getBoundingBox().inflate(RADIUS),
            candidate -> candidate != yautja
                && candidate.isAlive()
                && !(candidate instanceof Yautja)
        );

        for (var victim : victims) {
            victim.addEffect(
                new MobEffectInstance(
                    PredatorMobEffects.getRoarStunHolder(),
                    STUN_TICKS,
                    0,
                    false,
                    true,
                    true
                )
            );
        }
    }
}
