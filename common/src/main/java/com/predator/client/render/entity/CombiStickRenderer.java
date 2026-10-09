package com.predator.client.render.entity;

import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.PredatorResources;
import com.predator.common.gameplay.entity.projectile.CombiStickProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the thrown combi stick.
 * <p>
 * <strong>⚠⚠ THE SPEAR STOOD UPRIGHT BECAUSE Az NEVER APPLIES PITCH.</strong> Read from
 * {@code AzEntityModelRenderer.applyRotations}: it does exactly one rotation for a non-living entity,
 * {@code Axis.YP.rotationDegrees(180 - yaw)}. The model points along +Y, and spinning a +Y vector about Y leaves it
 * pointing at the sky — so the stick was yawed correctly and never tipped. It stuck out of the tree vertically.
 * <p>
 * <strong>⚠ The transform was SOLVED, not copied from vanilla's trident.</strong> My first attempt used the trident's
 * {@code YP(yaw - 90) * ZP(pitch + 90)} and came out mirrored on Z — checked numerically before it shipped. Because the
 * model's tip is +Y and Az's rotation is about Y, Az cannot change the DIRECTION at all; it only rolls the shaft.
 * Solving {@code A * (0,1,0) = forward(yaw, pitch)} gives {@code A = YP(90 - yaw) * ZP(90 + pitch)}, verified against
 * seven yaw/pitch pairs.
 * <p>
 * <strong>⚠ Do NOT "simplify" this by dropping the last line.</strong> It looks redundant and is not: without it Az's
 * yaw survives and the spear corkscrews around its own shaft as it flies.
 */
public class CombiStickRenderer extends AzEntityRenderer<CombiStickProjectile> {

    private static final String NAME = "combi_stick";

    private static final ResourceLocation MODEL = PredatorResources.itemGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = PredatorResources.itemTextureLocation(NAME);

    /**
     * ⚠ His figure: the combi stick renders at 75%. The model runs 65 units along Y — over four blocks at full size,
     * which reads as a flagpole rather than a weapon a hunter carries.
     * <p>
     * ⚠ It must be set on BOTH renderers. The held item and the thrown projectile are separate renderers with separate
     * configs, so a scale on one does nothing for the other — which is why the thrown spear came out full size while
     * the held one was right.
     */
    private static final float SCALE = 0.75F;

    /** How far back along its own shaft a landed spear is drawn, blocks. [stated] "3/5 a block". */
    private static final float STUCK_PULL_OUT = 0.6F;

    public CombiStickRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.<CombiStickProjectile>builder(MODEL, TEXTURE)
                .setShadowRadius(0.0F)
                .setScale(SCALE)
                .build(),
            context
        );
    }

    @Override
    public void render(
        @NotNull CombiStickProjectile entity,
        float entityYaw,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource buffer,
        int packedLight
    ) {
        var yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        var pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // ⚠⚠ THE CANCELLATION MUST USE THE **RAW** YAW, NOT THE INTERPOLATED ONE. Az's getLerpRot returns
        // animatable.getYRot() for a non-living entity — no interpolation at all — and applies YP(180 - that).
        // Cancelling with the interpolated yaw only works when yRotO == getYRot(), i.e. when the spear is not
        // turning. A spear thrown straight and stuck is stationary and looked correct; one still turning left a
        // residual yaw rotation that reads as ROLL. That is the "sideways when thrown at an angle" bug.
        var rawYaw = entity.getYRot();

        poseStack.pushPose();

        // ⚠⚠ MATCHED TO THE ARROW'S OWN CONVENTION, NOT THE PLAYER'S VIEW VECTOR. That was the bug: AbstractArrow
        // sets its rotation from MOTION —
        // yRot = atan2(dx, dz) xRot = atan2(dy, horizontalDistance)
        // so its forward is ( sin y cos p, sin p, cos y cos p ). I had been solving against the player view
        // vector ( -sin y cos p, -sin p, cos y cos p ), which is the OPPOSITE SIGN ON X AND Y.
        //
        // Straight ahead (yaw 0, pitch 0) both give (0,0,1) — which is exactly why a level throw embedded
        // perfectly and every angled one came out sideways with the tip out of the block.
        //
        // Solving A * (0,1,0) = arrow forward gives A = YP(90 + yaw) * ZP(90 - pitch). Verified against seven
        // yaw/pitch pairs including SE and NE angles.
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F + yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F - pitch));

        // ⚠ Cancels Az's own YP(180 - yaw) so the roll about the shaft is deterministic instead of spinning with
        // the throw direction. Looks redundant, is not.
        poseStack.mulPose(Axis.YP.rotationDegrees(rawYaw - 180.0F));

        // ⚠⚠ PULLED BACK OUT OF THE BLOCK IT LANDED IN. [stated] "the combi stick pierces the ground a bit too much
        // ... pull it up about 60% high or 3/5 a block." A thrown spear stops where its hitbox met the surface, and
        // this model is long enough that the rest of the shaft carries on into the ground — far deeper than a
        // trident's. The translation is along the model's own +Y, which the two rotations above have already aimed
        // along the flight direction, so backing off on that axis withdraws it along the shaft rather than shoving
        // it sideways at an angle.
        //
        // ⚠ ONLY WHILE STUCK. Applied in flight it would trail the spear behind where it actually is, and the hit
        // detection is at the entity, not at the drawing.
        if (entity.isStuckInGround()) {
            poseStack.translate(0.0F, -STUCK_PULL_OUT, 0.0F);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);

        poseStack.popPose();
    }
}
