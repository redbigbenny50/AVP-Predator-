package com.predator.mixin.cloak;

import com.predator.common.gameplay.cloak.PredatorCloakManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds landed damage into the cloak's absorption budget. Injected at RETURN and gated on the return value so that
 * blocked, cancelled and invulnerability-window hits do not count — only damage that actually happened.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_CloakDamage {

    @Inject(method = "hurt", at = @At("RETURN"))
    private void predator$absorbCloakDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        var self = LivingEntity.class.cast(this);

        if (self.level().isClientSide) {
            return;
        }

        PredatorCloakManager.onDamaged(self, source, amount);
    }
}
