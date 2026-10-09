package com.predator.mixin;

import com.predator.client.effect.PlasmaClientEffects;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ticks the plasma detonation's flash and shake. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_PlasmaEffects {

    @Inject(method = "tick", at = @At("RETURN"))
    private void avp_predator$tickPlasmaEffects(CallbackInfo callback) {
        PlasmaClientEffects.clientTick((Minecraft) (Object) this);
    }
}
