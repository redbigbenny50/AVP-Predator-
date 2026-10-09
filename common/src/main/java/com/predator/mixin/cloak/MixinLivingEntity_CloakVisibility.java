package com.predator.mixin.cloak;

import com.predator.common.gameplay.cloak.PredatorCloak;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The AI half of the cloak. {@code TargetingConditions#test} multiplies its follow range by
 * {@code getVisibilityPercent}, so returning zero here means no mob using vanilla targeting can acquire a concealed
 * wearer at any distance.
 * <p>
 * This is deliberately NOT vanilla's invisibility path. Vanilla scales the benefit by armour coverage
 * ({@code 0.7 * armorCover}), which leaves a fully-armoured predator visible at 70% of normal range — useless for a
 * cloak, and backwards besides, since the armour is part of what the field is hiding.
 * <p>
 * ⚠ Two exemptions, both intentional. The {@code avp_predator:cloak_immune} tag lets a mob ignore the field entirely
 * (xenomorphs have no eyes for it to fool; the warden hunts by vibration). And water/rain shorts the field out, which
 * {@link PredatorCloak#isConcealed} already folds in — a wearer standing in the rain is targetable like anyone else.
 * <p>
 * Sculk sensors and shriekers need nothing here: they listen to {@code GameEvent} vibrations, which are emitted
 * regardless of visibility, so they detect a cloaked wearer for free.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_CloakVisibility {

    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void predator$hideCloakedFromTargeting(
        @Nullable Entity lookingEntity,
        CallbackInfoReturnable<Double> cir
    ) {
        if (PredatorCloak.isImmune(lookingEntity)) {
            return;
        }

        var self = LivingEntity.class.cast(this);

        if (PredatorCloak.isConcealed(self)) {
            cir.setReturnValue(0.0);
        }
    }
}
