package com.predator.common.gameplay.effect;

import com.predator.PredatorResources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The stun left behind by a yautja's roar.
 * <h2>Why an attribute modifier rather than a movement lock</h2> ⭐ The same shape avp_alien's
 * {@code ImmovableStatusEffect} uses — a {@code MobEffect} that carries an attribute modifier and nothing else. There
 * is no tick handler to get wrong, it comes off cleanly when the duration expires even if the game is reloaded
 * mid-effect, and it works identically on players and mobs.
 * <p>
 * ⚠ {@code ADD_MULTIPLIED_TOTAL} at -1.0, so movement speed is scaled to exactly zero regardless of what the victim's
 * base speed or other modifiers are. A flat {@code ADD_VALUE} would have to guess at a magnitude and would leave a
 * speed-potioned player still walking.
 * <p>
 * ⚠ Movement only. It does NOT blind, silence or stop attacking — being frozen in place while a predator closes is
 * already the punishment, and taking the camera as well reads as a bug rather than a mechanic.
 */
public class RoarStunEffect extends MobEffect {

    /** Dull bronze, to read as the mask rather than as a xenomorph effect. */
    private static final int ROAR_BRONZE_COLOR = 0x8C6239;

    public RoarStunEffect() {
        super(MobEffectCategory.HARMFUL, ROAR_BRONZE_COLOR);

        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            PredatorResources.location("roar_stun_movement"),
            -1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }
}
