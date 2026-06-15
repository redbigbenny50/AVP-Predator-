package com.predator.mixin.vision;

import com.blib.api.client.posteffect.v1.BLibPostEffectFramework;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.client.vision.PredatorVisionClassification;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Background-flag push for block (tile) entities — chests, beacons, signs, banners, beds, conduits, end portals, etc.
 * They render via {@code BlockEntityRenderDispatcher}, parallel to (not under) {@code EntityRenderDispatcher}. Many
 * BERs use {@code rendertype_entity_*} render types under the hood (chest sheet, sign sheet, banner sheet) which BLib's
 * patcher classifies as entity ({@code mask.r = 1.0}) — without a background-flag push, the vision shader would render
 * them as foreground entities (warm glow).
 * <p>
 * RETURN pops based on the per-render frame recorded by HEAD rather than recomputing — recomputation is unsafe because
 * the transition state can change mid-render and produce mismatched pop counts that leak BLib lane depth.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher_VisionBackground {

    private static final ThreadLocal<Deque<int[]>> FRAME_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "render", at = @At("HEAD"))
    private void predator$pushBackgroundForBlockEntity(
        BlockEntity blockEntity,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        CallbackInfo ci
    ) {
        var frame = new int[2];
        FRAME_STACK.get().push(frame);

        if (BLibPostEffectFramework.isShaderModActive()) {
            return;
        }

        var classification = PredatorVisionClassification.nonLivingClassification();
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
    private void predator$popBackgroundForBlockEntity(
        BlockEntity blockEntity,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        CallbackInfo ci
    ) {
        var stack = FRAME_STACK.get();

        if (stack.isEmpty()) {
            return;
        }

        var frame = stack.pop();
        var poppedLaneA = frame[0] == 1;
        var poppedLaneB = frame[1] == 1;

        if (!poppedLaneA && !poppedLaneB) {
            return;
        }

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
