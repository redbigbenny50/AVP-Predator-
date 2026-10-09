package com.predator.mixin.vision;

import com.blib.api.client.posteffect.v1.BLibPerBoneLightContext;
import com.blib.api.client.posteffect.v1.BLibPostEffectFramework;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.client.vision.PredatorHeatMaterials;
import com.predator.client.vision.PredatorVisionClassification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Two-part hook into {@code LivingEntityRenderer.render}, gated on any non-regular vision mode being active. The
 * current vision mode (read from the equipped predator helmet) supplies the visible/hot tags that drive how each entity
 * is processed. Classification is delegated to {@link PredatorVisionClassification}.
 * <p>
 * <b>Why HEAD records its decisions on a stack instead of recomputing at RETURN:</b> the classify result depends on
 * {@link com.predator.client.vision.PredatorVisionTransition#isActive()} and the helmet's current mode, both of which
 * can change between HEAD and RETURN (a transition ending mid-render, a server packet updating the helmet, etc.). If
 * RETURN recomputed and got a different answer, the pops wouldn't match the pushes — leaking the BLib lane depths
 * across renders and corrupting every subsequent entity's mask.g. The stack guarantees pushes and pops are always
 * symmetric regardless of intervening state changes.
 * <p>
 * <b>Why this mixin force-flushes the buffer source around background entities:</b> {@code LivingEntityRenderer.render}
 * only queues vertices into the level's {@code MultiBufferSource} — the actual GL draw and {@code ShaderInstance.apply}
 * (which is when the {@code BlibBackgroundEntity}* uniforms get pushed to the GPU) doesn't happen until the level
 * renderer flushes the whole entity batch later. Without forcing flushes at the visible↔background boundary, every
 * entity in the same batch ends up sampling whatever flag state happened to be active at flush time — usually whichever
 * entity rendered last — so the color of any given entity drifts based on render order. Calling
 * {@code BufferSource.endBatch()} at the boundary ensures each entity's vertices flush under the uniform values that
 * were set when its render was called.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer_VisionPerBoneLight {

    /**
     * Per-render frame: {@code [pushedLaneA, pushedLaneB, pushedPerBoneLight, pushedMaterialId]} as 0/1 ints. A stack
     * so re-entrant renders (passenger drawn inside its mount's render, etc.) compose correctly. Render-thread only.
     */
    private static final ThreadLocal<Deque<int[]>> FRAME_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "render", at = @At("HEAD"))
    private void predator$visionPushContext(
        LivingEntity entity,
        float entityYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        // Always push a frame even if we're going to bail — RETURN pops unconditionally so push/pop must be paired.
        var frame = new int[4];
        FRAME_STACK.get().push(frame);

        // ⚠ A SHADER PACK NO LONGER MEANS "DO NOTHING". BLib's classification pass is the one point under a pack
        // where this per-entity work IS wanted — it draws into BLib's own private framebuffer, which the vision then
        // samples. Bail for the pack's own passes; take part in ours.
        if (
            BLibPostEffectFramework.isShaderModActive()
                && !BLibPostEffectFramework.isClassificationPassActive()
        ) {
            return;
        }

        var classification = PredatorVisionClassification.classify(entity);

        if (classification.isInactive()) {
            return;
        }

        var pushLaneA = classification.isBackgroundUnderOld();
        var pushLaneB = classification.isBackgroundUnderNew();

        if (pushLaneA || pushLaneB) {
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            if (pushLaneA) {
                BLibPostEffectFramework.pushBackgroundEntity();
                frame[0] = 1;
            }

            if (pushLaneB) {
                BLibPostEffectFramework.pushBackgroundEntityB();
                frame[1] = 1;
            }
        }

        // Warm-blooded tagging is independent of visibility: an animal reads warm in thermal whether or not the mode
        // treats it as a foreground entity. Flushing the batch is what makes it per-entity rather than per-batch —
        // without it every creature in a batch samples whichever ID was current when the flush happened.
        var materialId = PredatorHeatMaterials.materialIdFor(entity);

        if (materialId != 0) {
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            BLibPostEffectFramework.pushMaterialId(materialId);
            frame[3] = 1;
        }

        if (!classification.anyVisible()) {
            return;
        }

        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();

        if (mc.level == null || !camera.isInitialized()) {
            return;
        }

        var visibleMode = PredatorVisionClassification.visibleMode(entity);
        var hotTag = visibleMode != null ? visibleMode.hotTag() : null;
        var blockLightFloor = (hotTag != null && entity.getType().is(hotTag)) ? 14 : 7;

        BLibPerBoneLightContext.push(mc.level, camera.getPosition(), blockLightFloor);
        frame[2] = 1;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void predator$visionPopContext(
        LivingEntity entity,
        float entityYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        var stack = FRAME_STACK.get();

        if (stack.isEmpty()) {
            return;
        }

        var frame = stack.pop();
        var poppedLaneA = frame[0] == 1;
        var poppedLaneB = frame[1] == 1;
        var poppedPerBoneLight = frame[2] == 1;
        var poppedMaterialId = frame[3] == 1;

        if (poppedMaterialId) {
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            BLibPostEffectFramework.popMaterialId();
        }

        if (poppedPerBoneLight) {
            BLibPerBoneLightContext.pop();
        }

        if (poppedLaneA || poppedLaneB) {
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            if (poppedLaneB) {
                BLibPostEffectFramework.popBackgroundEntityB();
            }

            if (poppedLaneA) {
                BLibPostEffectFramework.popBackgroundEntity();
            }
        }
    }
}
