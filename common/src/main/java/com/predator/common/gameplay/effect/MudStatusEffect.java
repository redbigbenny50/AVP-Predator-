package com.predator.common.gameplay.effect;

import com.blib.api.common.color.v1.Color;
import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mud status effect — a thermal-vision cloak. Caked-on mud insulates the wearer's heat signature so a predator's
 * thermal vision reads them as world-cold instead of as a foreground entity (game-world reference: Dutch in the 1987
 * film). The effect is consumed by {@link com.predator.client.vision.PredatorVisionClassification}, which downgrades a
 * THERMAL-visible classification to BACKGROUND while the effect is active. Other vision modes are unaffected, so an
 * em-tagged mob covered in mud still shows up under EM.
 * <p>
 * Particles are suppressed entirely by tagging this effect into {@code BLibMobEffectTags#NO_PARTICLES} (the mud should
 * be silent, not announced by an obvious purple swirl). Milk does NOT cure mud — handled the same way, via
 * {@code BLibMobEffectTags#MILK_IMMUNE}. Mud washes off the moment the wearer goes underwater, handled by the per-tick
 * check below.
 */
public class MudStatusEffect extends MobEffect {

    public MudStatusEffect() {
        super(MobEffectCategory.NEUTRAL, Color.ofOpaque(0x6B4226).getColor());
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Default for most effects is "tick rarely" (gated by amplifier). We want to check water state every tick
        // so the cloak washes off the instant the wearer dives in.
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.isUnderWater()) {
            // removeEffect inside applyEffectTick mid-iteration of LivingEntity.tickEffects can trigger CME, but
            // tickEffects has a try/catch around the iteration specifically for this case. Safe in practice.
            entity.removeEffect(PredatorMobEffects.getMudHolder());
        }
        return true;
    }
}
