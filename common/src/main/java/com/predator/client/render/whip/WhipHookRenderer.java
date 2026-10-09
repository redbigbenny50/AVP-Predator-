package com.predator.client.render.whip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.PredatorResources;
import com.predator.common.gameplay.whip.WhipHookEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the grapple: the hook itself as a flat billboard, and the cord running back to the owner's hand — sagging,
 * because a thrown line sags, unlike the lash which is driven taut.
 */
public class WhipHookRenderer extends EntityRenderer<WhipHookEntity> {

    /** ⚠ The CHAIN WHIP's own art now — the grapple is the gauntlet's device, not part of the whip. */
    private static final ResourceLocation HOOK_TEXTURE = PredatorResources.location("textures/entity/chain_whip_tip.png");

    /** Likewise the cord: the grapple draws its own chain, while the whip's lash keeps whip_cord.png. */
    public static final ResourceLocation CHAIN_TEXTURE = PredatorResources.location("textures/entity/chain_whip_cord.png");

    /**
     * How far the middle of the line droops, blocks per block of length.
     * <p>
     * ⚠ ZERO BY DEFAULT — [stated] "the grapple needs to be straighter ... basically is like the hookshot from zelda."
     * A hookshot is a rigid line, not a rope: sag read as slack on something that is under tension. Raise it if you
     * ever want a slack-rope look while the hook is in flight.
     */
    public static double SAG = 0.0D;

    private static final int CORD_SEGMENTS = 16;

    public WhipHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
        @NotNull WhipHookEntity hook,
        float entityYaw,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource buffers,
        int packedLight
    ) {
        renderHook(hook, partialTick, poseStack, buffers, packedLight);

        if (hook.getOwner() instanceof LivingEntity owner) {
            renderCord(hook, owner, partialTick, poseStack, buffers, packedLight);
        }
    }

    private void renderHook(WhipHookEntity hook, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.5F, 0.5F, 0.5F);

        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(HOOK_TEXTURE));
        var pose = poseStack.last();

        for (
            var corner : new float[][] {
                { -0.5F, -0.5F, 0.0F, 1.0F },
                { 0.5F, -0.5F, 1.0F, 1.0F },
                { 0.5F, 0.5F, 1.0F, 0.0F },
                { -0.5F, 0.5F, 0.0F, 0.0F } }
        ) {
            vertices.addVertex(pose.pose(), corner[0], corner[1], 0.0F)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(corner[2], corner[3])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
        }

        poseStack.popPose();
    }

    private void renderCord(
        WhipHookEntity hook,
        LivingEntity owner,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight
    ) {
        var hookPos = hook.getPosition(partialTick);
        // ⚠⚠ THE WRIST, NOT THE HAND. The grapple fires from the GAUNTLET now, and the gauntlet is worn on the arm
        // OPPOSITE the main hand — so a right-hander's chain leaves their left wrist. Using the hand anchor here
        // would draw the chain from the wrong arm entirely.
        var hand = WhipCordRenderer.wristAnchor(owner, partialTick);
        var span = hand.subtract(hookPos);
        var droop = span.length() * SAG;
        var points = new Vec3[CORD_SEGMENTS + 1];

        for (var i = 0; i <= CORD_SEGMENTS; i++) {
            var along = i / (double) CORD_SEGMENTS;
            var sag = Math.sin(along * Math.PI) * droop;

            points[i] = hookPos.add(span.scale(along)).subtract(0.0D, sag, 0.0D);
        }

        poseStack.pushPose();
        WhipCordRenderer.draw(poseStack, buffers, hookPos, points, packedLight, 0.0F);
        poseStack.popPose();
    }

    /**
     * ⚠⚠ NEVER FRUSTUM-CULLED. The hook is small and its cord spans up to 32 blocks back to the hand; culling on the
     * HOOK's own box would drop the cord whenever the hook left the frustum. Never cull.
     */
    @Override
    public boolean shouldRender(
        @NotNull WhipHookEntity entity,
        @NotNull net.minecraft.client.renderer.culling.Frustum frustum,
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        return true;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull WhipHookEntity entity) {
        return HOOK_TEXTURE;
    }
}
