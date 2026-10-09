package com.predator.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.common.gameplay.block.entity.GauntletBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the placed gauntlet as the item itself, in its GROUND pose — the same picture a dropped one makes, resting flat
 * — through the normal item pipeline, so the armed lightning layer renders on it exactly as it does in a hand.
 */
public class GauntletBlockRenderer implements BlockEntityRenderer<GauntletBlockEntity> {

    /**
     * Blocks above the block floor the item origin sits at. [stated] lowered by 4 pixels (0.25 blocks) from 0.4 — it
     * was floating. Non-final: tune in a hot-swap.
     */
    public static float BLOCK_LIFT = 0.15F;

    @Override
    public void render(
        @NotNull GauntletBlockEntity entity,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        var stack = entity.getGauntlet();

        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        // ⚠ The geo is authored around the item origin, so half of it sits below y=0 in the GROUND pose — placed at the
        // block's floor it was buried in the ground ("when i place the gauntlet down on the ground i cannot see it").
        // Lifted so the sleeve rests on the surface.
        poseStack.translate(0.5, BLOCK_LIFT, 0.5);
        Minecraft.getInstance()
            .getItemRenderer()
            .renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffers,
                entity.getLevel(),
                (int) entity.getBlockPos().asLong()
            );
        poseStack.popPose();
    }
}
