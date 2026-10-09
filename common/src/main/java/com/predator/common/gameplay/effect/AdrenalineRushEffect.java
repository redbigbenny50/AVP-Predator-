package com.predator.common.gameplay.effect;

import com.predator.PredatorResources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A yautja's adrenaline rush: what a hunter does when the odds turn. [stated] "when outnumbered by 3 or more enemies
 * this effect activates and lasts for 60s in which the yautja gains resistance, and damage increase with minor regen."
 * <p>
 * Resistance is a flat damage reduction applied in {@code Yautja.hurt} (attributes have no "resistance"); the damage
 * bonus and the regeneration live here. ⚠ Dials are the non-final statics.
 */
public class AdrenalineRushEffect extends MobEffect {

    private static final int ADRENALINE_RED = 0xC81F1F;

    /** Fraction of incoming damage removed while the rush is up. Read by Yautja.hurt. */
    public static float DAMAGE_RESISTANCE = 0.25F;

    /** Melee damage bonus, multiplied into the base. */
    public static double DAMAGE_BONUS = 0.30D;

    /** Health restored per REGEN_INTERVAL_TICKS. "Minor" — about 2.5 hearts across the full 60 s at these values. */
    public static float REGEN_AMOUNT = 1.0F;

    public static int REGEN_INTERVAL_TICKS = 40;

    public AdrenalineRushEffect() {
        super(MobEffectCategory.BENEFICIAL, ADRENALINE_RED);
        addAttributeModifier(
            Attributes.ATTACK_DAMAGE,
            PredatorResources.location("adrenaline_rush_damage"),
            DAMAGE_BONUS,
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            PredatorResources.location("adrenaline_rush_speed"),
            0.10D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(REGEN_AMOUNT * (amplifier + 1));
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % REGEN_INTERVAL_TICKS == 0;
    }
}
