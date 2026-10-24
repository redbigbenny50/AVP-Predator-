package com.predator.mixin;

import com.predator.common.gameplay.freeze.FreezeBridge;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [stated] Oct 19 - a mob frozen by the stand-in freeze (avp_human not installed) can be broken out by a real attack or
 * a heavy fall, as with avp_human's: {@link FreezeBridge#onHurtStandIn}. Only once the damage has actually landed.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_StandInIceBreak {

    @Inject(method = "hurt", at = @At("RETURN"))
    private void avp_predator$standInIceBreak(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            FreezeBridge.onHurtStandIn((LivingEntity) (Object) this, source, amount);
        }
    }
}
