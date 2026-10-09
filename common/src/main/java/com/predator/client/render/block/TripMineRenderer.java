package com.predator.client.render.block;

import com.blib.api.client.render.v1.block.AzBlockEntityRenderer;
import com.blib.api.client.render.v1.block.AzBlockEntityRendererConfig;
import com.blib.api.client.render.v1.layer.AzAutoGlowingLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.PredatorResources;
import com.predator.common.gameplay.block.TripMineBlock;
import com.predator.common.gameplay.block.entity.TripMineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the trip mine geo, turned to sit on whatever surface it is mounted to.
 * <h2>⚠⚠ THE ROTATION IS OURS, NOT BLib's</h2> BLib rotates any block carrying vanilla's {@code facing} property, on
 * the assumption the model faces NORTH. This geo is authored on the FLOOR (pointing UP), and the block's property is
 * named {@code mount} precisely so BLib leaves it alone — see {@link TripMineBlock}.
 * <h2>⚠⚠ ABOVE THE PIPELINE, NOT IN preRenderEntry</h2> The first version rotated in {@code preRenderEntry}. BLib runs
 * that hook for the main pass AND AGAIN for every render layer's re-render, on a pose stack that still carries the main
 * pass's transform — so the glow layer was rotated twice and drew off the mine. Rotating here, before
 * {@code super.render}, the main pass and every layer inherit one rotation. Rotating about the block CENTRE means
 * BLib's own bottom-centre translate then lands on the centre of the mounting face, so the geo's base sits flush
 * against a wall or ceiling exactly as it sits on a floor.
 * <p>
 * ⚠ The six rotations are the same table as {@code TripMineBlock.rotateFromUp}. Change both or neither.
 */
public class TripMineRenderer extends AzBlockEntityRenderer<TripMineBlockEntity> {

    public static final String NAME = "trip_mine";

    private static final ResourceLocation GEO = PredatorResources.blockGeoModelLocation(NAME);

    private static final ResourceLocation TEX = PredatorResources.blockTextureLocation(NAME);

    public TripMineRenderer() {
        super(
            AzBlockEntityRendererConfig.<TripMineBlockEntity>builder(GEO, TEX)
                .addRenderLayer(new AzAutoGlowingLayer<>())
                .build()
        );
    }

    @Override
    public void render(
        @NotNull TripMineBlockEntity entity,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource source,
        int packedLight,
        int packedOverlay
    ) {
        poseStack.pushPose();
        rotateToMount(entity, poseStack);
        super.render(entity, partialTick, poseStack, source, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void rotateToMount(TripMineBlockEntity entity, PoseStack poseStack) {
        var state = entity.getBlockState();

        if (!state.hasProperty(TripMineBlock.MOUNT)) {
            return;
        }

        poseStack.translate(0.5, 0.5, 0.5);

        switch (state.getValue(TripMineBlock.MOUNT)) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            case NORTH -> poseStack.mulPose(Axis.XN.rotationDegrees(90.0F));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case EAST -> poseStack.mulPose(Axis.ZN.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            default -> {
                // UP: the authored pose.
            }
        }

        poseStack.translate(-0.5, -0.5, -0.5);
    }
}
