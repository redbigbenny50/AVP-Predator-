package com.predator.mixin;

import com.predator.client.effect.PlasmaClientEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the plasma detonation's flash over the HUD. */
@Mixin(Gui.class)
public abstract class MixinGui_PlasmaFlash {

    @Inject(method = "render", at = @At("RETURN"))
    private void avp_predator$renderPlasmaFlash(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        PlasmaClientEffects.renderFlash(graphics, deltaTracker);
    }
}
