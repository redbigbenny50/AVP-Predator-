package com.predator.mixin;

import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops the yautja's roar from zooming the camera.
 * <p>
 * ⚠⚠ THE STUN IS A MOVEMENT-SPEED MODIFIER, AND FOV FOLLOWS MOVEMENT SPEED. {@code RoarStunEffect} immobilises with
 * -100% on {@code MOVEMENT_SPEED}; vanilla's first-person FOV is scaled by {@code (speed / walkingSpeed + 1) / 2}, so
 * speed zero halves the FOV — the Slowness IV zoom. [stated] "the camera zooms in when the predator roars that shouldnt
 * happen." While the stun is on, the modifier is pinned to 1: the player cannot sprint or draw a bow through it anyway,
 * so nothing else the modifier expresses is lost, and vanilla's own FOV smoothing eases both edges.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer_RoarStunNoZoom {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void avp_predator$noZoomWhileRoarStunned(CallbackInfoReturnable<Float> callback) {
        var player = (AbstractClientPlayer) (Object) this;

        if (player.hasEffect(PredatorMobEffects.getRoarStunHolder())) {
            callback.setReturnValue(1.0F);
        }
    }
}
