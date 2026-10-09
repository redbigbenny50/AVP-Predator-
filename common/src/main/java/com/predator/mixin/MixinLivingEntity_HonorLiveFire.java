package com.predator.mixin;

import com.predator.common.gameplay.hunt.YautjaHonor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Watches for the first avp_human bullet that lands on a hostile, for the Live Fire advancement. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_HonorLiveFire {

    @Inject(method = "hurt", at = @At("RETURN"))
    private void avp_predator$liveFire(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) {
            YautjaHonor.onHurt((LivingEntity) (Object) this, source);
        }
    }
}
