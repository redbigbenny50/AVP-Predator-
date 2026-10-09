package com.predator.mixin.vision;

import com.blib.api.client.posteffect.v1.BLibPostEffectFramework;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.client.vision.PredatorHeatMaterials;
import com.predator.client.vision.PredatorVisionClassification;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Background-flag push for non-LivingEntity entities (item entities, projectiles, minecarts, boats, paintings, item
 * frames, experience orbs, lightning bolts, etc.). They render via {@code EntityRenderer} subclasses that aren't
 * {@code LivingEntityRenderer}, so {@code MixinLivingEntityRenderer_VisionPerBoneLight} doesn't fire for them. Without
 * this mixin their pixels arrive in the gbuffer with {@code mask.g = 0} and the vision shader routes them through the
 * foreground-entity branch — making dropped items glow warm in thermal, etc.
 * <p>
 * LivingEntities are skipped here so the per-bone-light/visibility-tag logic in
 * {@code MixinLivingEntityRenderer_VisionPerBoneLight} can run with the right semantics. RETURN pops based on what HEAD
 * actually pushed (recorded on a per-render stack) rather than recomputing — recomputation is unsafe because the
 * transition state can change mid-render and would produce mismatched pop counts that leak BLib lane depth.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher_VisionNonLivingBackground {

    private static final ThreadLocal<Deque<int[]>> FRAME_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "render", at = @At("HEAD"))
    private void predator$pushBackgroundForNonLiving(
        Entity entity,
        double x,
        double y,
        double z,
        float rotationYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        if (predator$handledElsewhere(entity)) {
            // Living entities drawn by a LivingEntityRenderer or BLib's AzEntityRenderer are classified by those
            // mixins instead. Don't push a frame here so RETURN also bails for them — keeps stack push/pop balanced.
            return;
        }

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

        // Oct 7: a living entity reaching here has a renderer neither vision mixin covers - another mod's AzureLib or
        // GeckoLib renderer (Gigeresque's whole bestiary). It is classified by its tags like any other mob, instead of
        // drawing as a bright foreground entity in every mode, which is how Gigeresque's xenomorphs showed on thermal.
        var classification = entity instanceof LivingEntity living
            ? PredatorVisionClassification.classify(living)
            : PredatorVisionClassification.nonLivingClassification();
        var pushLaneA = classification.isBackgroundUnderOld();
        var pushLaneB = classification.isBackgroundUnderNew();

        // Projectiles carry heat too: a ghast's fireball should streak across a gorge, and a predator that can see the
        // ghast but not the shot has it backwards. Deliberately ABOVE the early return below — most projectiles are in
        // neither background lane, so putting it after would skip nearly all of them.
        var materialId = PredatorHeatMaterials.materialIdFor(entity);

        if (materialId != 0) {
            // Same flush discipline as the lanes: without it, every projectile in a batch samples whichever id
            // happened to be current when the batch flushed.
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            BLibPostEffectFramework.pushMaterialId(materialId);
            frame[2] = 1;
        }

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

    /**
     * Oct 7 - true for living entities the LivingEntityRenderer and BLib AzEntityRenderer vision mixins classify
     * themselves. Anything else - non-living entities, and living ones drawn by a renderer from another library
     * (Gigeresque uses AzureLib's own AzEntityRenderer, which BLib's fork is not) - is classified here. The renderer
     * for an entity does not change between HEAD and RETURN of one render call, so both sides agree.
     */
    @org.spongepowered.asm.mixin.Unique
    private boolean predator$handledElsewhere(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }

        var renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(entity);

        return renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>
            || renderer instanceof com.blib.api.client.render.v1.entity.AzEntityRenderer<?>;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void predator$popBackgroundForNonLiving(
        Entity entity,
        double x,
        double y,
        double z,
        float rotationYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        if (predator$handledElsewhere(entity)) {
            return;
        }

        var stack = FRAME_STACK.get();

        if (stack.isEmpty()) {
            return;
        }

        var frame = stack.pop();
        var poppedLaneA = frame[0] == 1;
        var poppedLaneB = frame[1] == 1;

        if (frame[2] == 1) {
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }

            BLibPostEffectFramework.popMaterialId();
        }

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
