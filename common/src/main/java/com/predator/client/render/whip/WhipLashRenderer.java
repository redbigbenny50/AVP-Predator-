package com.predator.client.render.whip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.common.gameplay.whip.WhipCord;
import com.predator.common.gameplay.whip.WhipLashEntity;
import com.predator.common.gameplay.whip.WhipTuning;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Draws the lash: the same {@link WhipCord} curve the server hits along, so what you see is what connects. */
public class WhipLashRenderer extends EntityRenderer<WhipLashEntity> {

    public WhipLashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
        @NotNull WhipLashEntity lash,
        float entityYaw,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource buffers,
        int packedLight
    ) {
        var owner = lash.owner();

        if (owner == null) {
            return;
        }

        var progress = WhipCord.progress(lash.tickCount, partialTick);
        var origin = WhipCordRenderer.handAnchor(owner, partialTick);

        // ⚠⚠ AIM FROM THE HAND AT WHAT THE CROSSHAIR SEES. Using the raw view vector made the cord run PARALLEL to
        // the eye line from a root that sits below and right of it — so it read as "too high" and drifted off the
        // handle as the pitch changed, worst at steep angles. Pointing the cord at a spot along the eye ray instead
        // keeps the far end on the crosshair and the near end on the grip, which is what the eye actually checks.
        var aimPoint = owner.getEyePosition(partialTick).add(owner.getViewVector(partialTick).scale(WhipTuning.LASH_REACH));
        var direction = aimPoint.subtract(origin).normalize();
        var side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        var points = new Vec3[WhipCord.SEGMENTS + 1];

        for (var i = 0; i <= WhipCord.SEGMENTS; i++) {
            points[i] = WhipCord.pointAt(origin, direction, side, progress, i / (double) WhipCord.SEGMENTS);
        }

        // The flare peaks at the snap and falls away either side of it, so the tip "cracks" rather than just widening.
        var flare = Mth.clamp(1.0F - Math.abs(progress - WhipTuning.LASH_SNAP_AT) * 6.0F, 0.0F, 1.0F);

        poseStack.pushPose();
        WhipCordRenderer.draw(poseStack, buffers, lash.position(), points, packedLight, flare);
        poseStack.popPose();
    }

    /**
     * ⚠⚠ NEVER FRUSTUM-CULLED. The lash entity is a 0.1-block dot AT THE PLAYER, so in first person it sits on top of
     * the camera and vanilla culls it — taking the whole cord with it. The cord is what matters, so this renderer never
     * culls.
     */
    @Override
    public boolean shouldRender(
        @NotNull WhipLashEntity entity,
        @NotNull net.minecraft.client.renderer.culling.Frustum frustum,
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        return true;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull WhipLashEntity entity) {
        return WhipCordRenderer.CORD_TEXTURE;
    }
}
