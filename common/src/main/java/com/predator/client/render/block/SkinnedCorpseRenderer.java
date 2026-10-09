package com.predator.client.render.block;

import com.blib.api.client.render.v1.block.AzBlockEntityRenderer;
import com.blib.api.client.render.v1.block.AzBlockEntityRendererConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.PredatorResources;
import com.predator.common.gameplay.block.SkinnedCorpseBlock;
import com.predator.common.gameplay.block.entity.SkinnedCorpseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the skinned corpse — hanging upside down from the block above, or lying on the ground.
 * <h2>The model</h2> His {@code skinned_player_v2} geo: a standing, player-sized body, feet at the origin, two blocks
 * tall. BLib's block renderer translates the model to the block's bottom-centre and draws it from there; every pose
 * below is applied AROUND that, before {@code super.render}, so the main pass and any layer inherit one transform — the
 * lesson from {@link TripMineRenderer}.
 * <ul>
 * <li><b>Hanging:</b> turned upside down (180° about X) and lifted one block, so the feet touch the underside of the
 * ceiling and the head hangs down into the block below.</li>
 * <li><b>Lying:</b> tipped 90° onto its back and slid down its own length by one block, so the body is centred on its
 * block rather than sticking two blocks out of one side; raised 2 px so the back is not buried.</li>
 * <li><b>Floating:</b> the lying pose raised to the water's surface.</li>
 * </ul>
 * Both are then yawed to the block's {@code orientation}.
 */
public class SkinnedCorpseRenderer extends AzBlockEntityRenderer<SkinnedCorpseBlockEntity> {

    public static final String NAME = "skinned_corpse";

    private static final ResourceLocation GEO = PredatorResources.blockGeoModelLocation(NAME);

    private static final ResourceLocation TEX = PredatorResources.blockTextureLocation(NAME);

    /** Lying pose: how far above the floor the body's centre line sits, in blocks. The model is 4 px deep. */
    public static float LYING_LIFT = 2.0F / 16.0F;

    /**
     * Floating pose: the same lying body raised to sit just under the water's surface (a water source's surface is
     * about 14 px up), so its front shows above the water and its back below.
     */
    public static float FLOATING_LIFT = 13.0F / 16.0F;

    public SkinnedCorpseRenderer() {
        super(AzBlockEntityRendererConfig.<SkinnedCorpseBlockEntity>builder(GEO, TEX).build());
    }

    @Override
    public void render(
        @NotNull SkinnedCorpseBlockEntity entity,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource source,
        int packedLight,
        int packedOverlay
    ) {
        var state = entity.getBlockState();

        if (!state.hasProperty(SkinnedCorpseBlock.HANGING) || !state.hasProperty(SkinnedCorpseBlock.ORIENTATION)) {
            super.render(entity, partialTick, poseStack, source, packedLight, packedOverlay);
            return;
        }

        var yaw = -state.getValue(SkinnedCorpseBlock.ORIENTATION).toYRot();

        poseStack.pushPose();

        if (state.getValue(SkinnedCorpseBlock.HANGING)) {
            // Feet at the top of this block, body hanging down into the one below.
            poseStack.translate(0.5, 1.0, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        } else {
            // On its back, centred on this block — at the surface when it floats.
            var floating = state.hasProperty(SkinnedCorpseBlock.FLOATING) && state.getValue(SkinnedCorpseBlock.FLOATING);
            poseStack.translate(0.5, floating ? FLOATING_LIFT : LYING_LIFT, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.translate(0.0, -1.0, 0.0);
        }

        // BLib adds its own +0.5 / +0.5 bottom-centre translate inside super.render — cancel it here so the pose above
        // is applied about the model's own origin (its feet).
        poseStack.translate(-0.5, 0.0, -0.5);

        super.render(entity, partialTick, poseStack, source, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
