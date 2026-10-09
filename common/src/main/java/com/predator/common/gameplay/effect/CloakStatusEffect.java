package com.predator.common.gameplay.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Purely a HUD marker so the wearer can tell their cloak is engaged — in first person there is otherwise nothing to
 * see, which is the whole point of a cloak and also deeply confusing.
 * <p>
 * ⚠ Carries NO behaviour. Every rule the cloak has — targeting, damage break, water, rendering — lives in
 * {@code PredatorCloakManager} and keys off its own state, not off this effect. It is applied and removed alongside
 * that state and is safe to strip.
 * <p>
 * A dedicated effect rather than vanilla INVISIBILITY on purpose: vanilla invisibility nulls the body render type, so
 * applying it would delete the very geometry the cloak draws its shimmer onto, and armour would keep rendering.
 */
public class CloakStatusEffect extends MobEffect {

    public CloakStatusEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7FB8D4);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
