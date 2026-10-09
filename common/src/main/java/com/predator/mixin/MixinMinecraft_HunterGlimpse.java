package com.predator.mixin;

import com.predator.client.hunt.HunterGlimpseClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ticks the phase-1 Hunter glimpse (vanishing it when the player gets close or it has lingered long enough). */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_HunterGlimpse {

    @Inject(method = "tick", at = @At("RETURN"))
    private void avp_predator$tickHunterGlimpse(CallbackInfo callback) {
        HunterGlimpseClient.clientTick((Minecraft) (Object) this);
    }
}
