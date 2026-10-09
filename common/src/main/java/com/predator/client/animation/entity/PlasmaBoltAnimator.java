package com.predator.client.animation.entity;

import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.predator.PredatorResources;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltAnimationDispatcher;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltProjectile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code bolt.fire}, which carries the model's authored 0.3 scale and the {@code gBolt} offset.
 * <p>
 * Transition length is zero on purpose. A bolt exists for well under a second at close range, and a blend-in would be
 * visible as the model inflating from full size down to its real one over the first few frames of every shot.
 */
public class PlasmaBoltAnimator extends AzEntityAnimator<PlasmaBoltProjectile> {

    private static final String NAME = "plasma_bolt";

    private static final ResourceLocation ANIMATION = PredatorResources.entityAnimationLocation(NAME);

    /**
     * Mirrors {@code bolt.fire}. ⚠ Kept in step with the clip BY HAND — if the model is rescaled in Blockbench, this is
     * the second place to change. The duplication buys determinism: a value written every frame cannot drift back to
     * the bind pose the way a clip-driven one does.
     */
    private static final float BOLT_SCALE = 0.363F;

    /** Mirrors the {@code gBolt} offset in {@code bolt.fire}, in model units. */
    private static final float BOLT_OFFSET_Y = -27.0F;

    public PlasmaBoltAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<PlasmaBoltProjectile> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, PlasmaBoltAnimationDispatcher.BOLT_CONTROLLER_NAME)
                .setTransitionLength(0)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(PlasmaBoltProjectile animatable) {
        return ANIMATION;
    }

    /**
     * Pitches the bolt to its flight angle. Yaw is NOT set here.
     * <p>
     * <b>⚠⚠ THE RENDERER ALREADY APPLIES YAW, AND SETTING IT HERE TOO WAS THE SIDEWAYS BOLT.</b>
     * {@code AzEntityModelRenderer.applyRotations} does {@code Axis.YP.rotationDegrees(180 - rotationYaw)}, and for a
     * non-living entity {@code rotationYaw} is simply {@code getYRot()} — which {@code Projectile.updateRotation} keeps
     * pointing along the flight. Adding another yaw on the root bone doubled it, so the bolt was off by its own
     * heading: correct only when fired due south, and worst at the diagonals.
     * <p>
     * ⚠ Pitch genuinely IS missing from the renderer — {@code applyRotations} handles yaw and then only living-entity
     * extras, so a projectile never gets its elevation. That is the one thing left to do here.
     * <p>
     * ⚠ Sign: the yaw above leaves the model turned 180 degrees about Y relative to vanilla's arrow setup, which flips
     * the local X axis — so a POSITIVE rotX pitches the nose UP, and MC's xRot is positive DOWNWARD. Hence the
     * negation. Read from {@code getXRot} rather than from velocity so it agrees with the yaw the renderer used.
     */
    @Override
    public void setCustomAnimations(PlasmaBoltProjectile animatable, float partialTick) {
        var root = context().boneCache().getBakedModel().getBoneOrNull("root");

        if (root == null) {
            return;
        }

        // ⚠⚠ FROM VELOCITY, NOT FROM getXRot(). The entity pitch field is produced by
        // Projectile.updateRotation, which runs through lerpRotation at 0.2 and is then re-derived on the
        // client from its own synced delta — two lags on a projectile that lives under a second. The velocity
        // is exact and available on both sides, so there is no reason to read a laggy proxy for it.
        var motion = animatable.getDeltaMovement();
        var horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

        if (horizontal + Math.abs(motion.y) < 1.0E-4) {
            return;
        }

        // ⚠ A CONSTANT half turn, not the heading. The renderer's yaw assumes a model whose front is -Z; this
        // one points +Z once its bind rotation is applied, so it flew tail-first. A fixed 180 corrects that
        // without reintroducing the heading-dependent yaw that made it fly sideways.
        root.setRotY(Mth.PI);

        // ⚠ DERIVED, NOT GUESSED. rotateMatrixAroundBone composes Rz then Ry then Rx, and gBolt carries a
        // bind rotation of [180, 0, -135], so the nose sits at -Z in root space. Working the composite
        // through: Ry(180) * Rx(t) takes the nose to elevation sin(t). Setting t = atan2(dy, horizontal)
        // therefore makes the nose elevation match the flight direction exactly, in radians, no sign guess.
        root.setRotX((float) Mth.atan2(motion.y, horizontal));

        // ⚠⚠ SCALE AND OFFSET ARE SET HERE, NOT LEFT TO THE bolt.fire CLIP, AND THAT IS WHY THE BOLT KEPT
        // COMING BACK FULL SIZE. A bone the clip is not currently writing gets lerped back toward its bind
        // pose by AzCachedBoneUpdateUtil — scale included — over boneResetTime. bolt.fire is a zero-length
        // looping pose, so as soon as its queue drains the root scale drifts from 0.33 back to 1.0 and the
        // bolt visibly inflates mid-flight. Writing it every frame cannot drift.
        root.setScaleX(BOLT_SCALE);
        root.setScaleY(BOLT_SCALE);
        root.setScaleZ(BOLT_SCALE);

        var bolt = context().boneCache().getBakedModel().getBoneOrNull("gBolt");

        if (bolt != null) {
            bolt.setPosY(BOLT_OFFSET_Y);
        }
    }
}
