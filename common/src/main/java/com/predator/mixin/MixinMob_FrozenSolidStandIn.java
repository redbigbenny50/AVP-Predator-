package com.predator.mixin;

import com.predator.common.gameplay.freeze.FreezeBridge;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The stand-in freeze's AI stop: a mob under avp_predator's own Frozen Solid effect (only ever given when avp_human is
 * not installed) skips its server AI step. avp_human's effect has its own copy of this.
 */
@Mixin(Mob.class)
public abstract class MixinMob_FrozenSolidStandIn {

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void avp_predator$frozenSolid(CallbackInfo callback) {
        if (FreezeBridge.isStandInFrozen((Mob) (Object) this)) {
            callback.cancel();
        }
    }
}
