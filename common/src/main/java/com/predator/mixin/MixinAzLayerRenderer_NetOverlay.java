package com.predator.mixin;

import com.blib.api.client.render.v1.AzLayerRenderer;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.predator.client.render.net.AzNetOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the capture net on everything BLib renders — every Az entity (yautja, and every xenomorph, whose renderers in
 * avp_alien go through this same pipeline) and every Az armour piece on a netted wearer.
 * <p>
 * ⚠⚠ WHY THE VANILLA LAYER NEVER REACHED THEM: {@code NetOverlayLayer} is added to every {@code LivingEntityRenderer}
 * by constructor injection. {@code AzEntityRenderer} extends {@code EntityRenderer} directly — it has no render layers
 * at all — so no Az-rendered mob ever got the overlay. [stated] "net can be used on yautja but doesnt display" and
 * "xenomorphs cant be netted whatsoever". This hooks the pipeline's own layer pass instead, after the mod's layers.
 */
@Mixin(value = AzLayerRenderer.class, remap = false)
public abstract class MixinAzLayerRenderer_NetOverlay<K, T> {

    @Inject(method = "applyRenderLayers", at = @At("TAIL"))
    private void avp_predator$netOverlay(AzRendererPipelineContext<K, T> context, CallbackInfo callback) {
        AzNetOverlay.render(context);
    }
}
