package com.predator.client.render.entity;

import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import com.predator.PredatorResources;
import com.predator.client.animation.entity.PlasmaBoltAnimator;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltProjectile;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the plasma bolt: the X of planes with the cube tip at the front.
 * <h2>The animator is not optional</h2> ⚠ It looks like a bolt needs no animation — the motion is velocity, not a clip.
 * But {@code bolt.fire} carries the model's authored 0.3 scale and the {@code gBolt} Y offset, so without the animator
 * the bolt renders at full size, six blocks long, and floating well clear of the muzzle.
 * <h2>Emissive rather than glow-masked</h2> ⚠ Built lazily, never in a static initialiser. {@code RenderType} touches
 * the render system, and a static block runs whenever the class first loads — which on Fabric is during mod loading,
 * before the render system is up. That exact mistake took the Fabric client down on world load once already, with no
 * crash report, because the failure was below the JVM. See the same note on {@code YautjaRenderer}.
 */
public class PlasmaBoltRenderer extends AzEntityRenderer<PlasmaBoltProjectile> {

    private static final String NAME = "plasma_bolt";

    private static final ResourceLocation MODEL = PredatorResources.entityGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = PredatorResources.entityTextureLocation(NAME);

    private static RenderType emissive;

    public PlasmaBoltRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.<PlasmaBoltProjectile>builder(MODEL, TEXTURE)
                .setRenderType(bolt -> emissiveType())
                .setAnimatorProvider(PlasmaBoltAnimator::new)
                .setShadowRadius(0.0F)
                .setModelRenderer(
                    (pipeline, layer) -> new BoltModelRenderer(
                        (com.blib.api.client.render.v1.entity.pipeline.AzEntityRendererPipeline<PlasmaBoltProjectile>) pipeline,
                        layer
                    )
                )
                .build(),
            context
        );
    }

    /** Full-bright and translucent, so the bolt lights itself in a dark cave and the plane edges blend. */
    private static RenderType emissiveType() {
        if (emissive == null) {
            emissive = RenderType.entityTranslucentEmissive(TEXTURE);
        }

        return emissive;
    }

    /**
     * Turns the bolt to its TRUE heading — yaw AND pitch.
     * <p>
     * [stated] "when fired diagnolly the plasmabolt from the hand caster is sideways its not following the direction
     * straight of the reticle."
     * <p>
     * ⚠⚠ BLib's entity renderer turns a model with YP(180 - yaw) — the LIVING-entity convention — and never applies
     * pitch. The bolt's yaw is set the PROJECTILE way, atan2(dx, dz) (PlasmaBoltProjectile.launch, and vanilla's own
     * updateRotation every tick). Worked through against the geo, whose tip is at +Z: that combination points the nose
     * right going east or west, backwards going north or south (invisible — the X of planes is symmetric), and 90
     * degrees off on EVERY diagonal: the sideways bolt. The yautja's plasma caster fires the same bolt and had the same
     * fault.
     * <p>
     * The correct turn for a +Z nose and a projectile yaw is YP(yaw) — the nose (0,0,1) goes to (sin yaw, 0, cos yaw),
     * which IS the flight direction — then XP(-pitch), which tips a +Z nose UP for a positive (upward) projectile
     * pitch. Both smoothed between ticks.
     */
    /** [stated] 30% larger. */
    public static float BOLT_SCALE = 1.3F;

    private static final class BoltModelRenderer extends com.blib.api.client.render.v1.entity.model.AzEntityModelRenderer<PlasmaBoltProjectile> {

        BoltModelRenderer(
            com.blib.api.client.render.v1.entity.pipeline.AzEntityRendererPipeline<PlasmaBoltProjectile> pipeline,
            com.blib.api.client.render.v1.AzLayerRenderer<java.util.UUID, PlasmaBoltProjectile> layer
        ) {
            super(pipeline, layer);
        }

        @Override
        protected void applyRotations(
            PlasmaBoltProjectile bolt,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            float ageInTicks,
            float rotationYaw,
            float partialTick,
            float nativeScale
        ) {
            var yaw = net.minecraft.util.Mth.rotLerp(partialTick, bolt.yRotO, bolt.getYRot());
            var pitch = net.minecraft.util.Mth.lerp(partialTick, bolt.xRotO, bolt.getXRot());

            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
            // [stated] "please make the projectile larger it seems too small now that im seeing it. make it 30%
            // larger".
            // ⚠ Drawn larger only — its hitbox is unchanged. The yautja's shoulder caster fires the same bolt, so its
            // bolts are 30% larger too.
            poseStack.scale(BOLT_SCALE, BOLT_SCALE, BOLT_SCALE);
        }
    }
}
