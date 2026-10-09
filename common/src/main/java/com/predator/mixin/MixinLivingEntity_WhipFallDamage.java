package com.predator.mixin;

import com.predator.common.gameplay.whip.WhipGrapple;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No fall damage while grappling, or for a moment after — [stated]. Without the grace the ledge boost at the end of a
 * reel is a death trap: it throws you up a wall and vanilla charges you for the landing.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_WhipFallDamage {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void avp_predator$whipFallGrace(
        float distance,
        float multiplier,
        net.minecraft.world.damagesource.DamageSource source,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (WhipGrapple.hasFallGrace((LivingEntity) (Object) this)) {
            callback.setReturnValue(false);
        }
    }
}
