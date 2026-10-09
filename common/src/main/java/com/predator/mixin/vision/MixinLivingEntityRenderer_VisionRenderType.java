package com.predator.mixin.vision;

import com.blib.api.client.posteffect.v1.BLibPostEffectFramework;
import com.predator.client.vision.PredatorVisionAccessor;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When any non-regular predator vision mode is active, substitute alpha-blended entity render types
 * ({@code entityTranslucent}, {@code itemEntityTranslucentCull}) with their non-blended cutout equivalents. Reason:
 * alpha blending applies to every color attachment of the MainTarget MRT, and on some drivers the {@code SRC_ALPHA}
 * blend factor for the single-channel mask attachment evaluates to 0 — silently zeroing the mask write and making the
 * entity invisible-to-vision. {@code PlayerModel}-derived entities (players, piglins, zombie piglins) hit this path.
 * <p>
 * Visual cost is essentially zero: {@code rendertype_entity_cutout_no_cull} uses {@code discard} for transparent pixels
 * rather than alpha blending; for player/piglin textures the body is fully opaque so no pixels are affected, and any
 * sub-100% alpha pixels (rare on entity skins) become hard cutoffs at the 0.1 threshold rather than blended. Only
 * matters during the vision post-effect, which doesn't care about edge alpha precision anyway.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer_VisionRenderType {

    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void predator$forceCutoutForVision(
        LivingEntity livingEntity,
        boolean bodyVisible,
        boolean translucent,
        boolean glowing,
        CallbackInfoReturnable<RenderType> cir
    ) {
        // ⚠ A SHADER PACK NO LONGER MEANS "DO NOTHING". BLib's classification pass is the one point under a pack
        // where this per-entity work IS wanted — it draws into BLib's own private framebuffer, which the vision then
        // samples. Bail for the pack's own passes; take part in ours.
        if (
            !PredatorVisionAccessor.isAnyVisionActive()
                || (BLibPostEffectFramework.isShaderModActive()
                    && !BLibPostEffectFramework.isClassificationPassActive())
        ) {
            return;
        }

        var rt = cir.getReturnValue();

        if (rt == null) {
            return;
        }

        var name = rt.toString();

        if (name.contains("entity_translucent") || name.contains("item_entity_translucent_cull")) {
            // noinspection unchecked
            var self = (LivingEntityRenderer<LivingEntity, ?>) (Object) this;
            cir.setReturnValue(RenderType.entityCutoutNoCull(self.getTextureLocation(livingEntity)));
        }
    }
}
