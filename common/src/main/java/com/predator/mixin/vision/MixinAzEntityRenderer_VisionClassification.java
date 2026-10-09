package com.predator.mixin.vision;

import com.blib.api.client.posteffect.v1.BLibPostEffectFramework;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.client.vision.PredatorHeatMaterials;
import com.predator.client.vision.PredatorVisionClassification;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Vision classification for creatures rendered through BLib's animation renderer.
 * <h2>Why this exists</h2> Classification was split between two hooks:
 * {@code MixinLivingEntityRenderer_VisionPerBoneLight} for living things and
 * {@code MixinEntityRenderDispatcher_VisionNonLivingBackground} for everything else — and the second deliberately skips
 * {@code LivingEntity}, trusting the first to cover it.
 * <p>
 * ⚠⚠ {@link AzEntityRenderer} extends {@code EntityRenderer}, <b>not</b> {@code LivingEntityRenderer}. So every mob
 * animated through BLib — which is every xenomorph — fell straight through the gap between those two hooks and was
 * never classified at all. Its mask kept whatever the patched entity shader wrote, so the shader treated it as an
 * ordinary foreground entity.
 * <p>
 * The symptoms looked like three unrelated bugs: xenomorphs glowing on thermal when they should be invisible, showing
 * up on electromagnetic only intermittently, and two players on identical builds seeing different results. All of it
 * was the same gap — with nothing classifying them, what the mask held at those pixels depended on whatever wrote there
 * last in the frame, which is a race rather than a rule.
 */
@Mixin(AzEntityRenderer.class)
public abstract class MixinAzEntityRenderer_VisionClassification {

    /** Per-render frame: {@code [pushedLaneA, pushedLaneB, pushedMaterialId]}. A stack, so mounts nest safely. */
    private static final ThreadLocal<Deque<int[]>> FRAME_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "render", at = @At("HEAD"))
    private void avp_predator$pushVisionState(
        Entity entity,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        // Always push a frame even when bailing — RETURN pops unconditionally, so the two must stay paired.
        var frame = new int[3];
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

        var materialId = PredatorHeatMaterials.materialIdFor(entity);

        if (materialId != 0) {
            // Flush first: vertices already queued belong to whatever was current before this entity.
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            BLibPostEffectFramework.pushMaterialId(materialId);
            frame[2] = 1;
        }

        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        var classification = PredatorVisionClassification.classify(living);

        if (classification.isInactive()) {
            return;
        }

        var pushLaneA = classification.isBackgroundUnderOld();
        var pushLaneB = classification.isBackgroundUnderNew();

        if (!pushLaneA && !pushLaneB) {
            return;
        }

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

    @Inject(method = "render", at = @At("RETURN"))
    private void avp_predator$popVisionState(
        Entity entity,
        float entityYaw,
        float partialTick,
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

        if (frame[0] == 0 && frame[1] == 0 && frame[2] == 0) {
            return;
        }

        // One flush covers all three: this entity's vertices must be drawn under its own uniforms before they change.
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        if (frame[1] == 1) {
            BLibPostEffectFramework.popBackgroundEntityB();
        }

        if (frame[0] == 1) {
            BLibPostEffectFramework.popBackgroundEntity();
        }

        if (frame[2] == 1) {
            BLibPostEffectFramework.popMaterialId();
        }
    }
}
