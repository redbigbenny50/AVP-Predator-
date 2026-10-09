package com.predator.client.animation.entity;

import com.blib.api.client.animation.v1.animator.AzAnimationContext;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.predator.PredatorResources;
import com.predator.client.animation.BasicAnimationUtils;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaAnimationRefs;
import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class YautjaAnimator extends AzEntityAnimator<Yautja> {

    /**
     * ⚠⚠ MUST MATCH THE ASSET FILENAME, WHICH IS {@code yautja_jungle.animation.json}.
     * <p>
     * This read {@code "yautja"} — a leftover from the yautja_jungle rename — so the animator asked the baked cache for
     * {@code avp_predator:animations/entity/yautja.animation.json}, a file that does not exist.
     * {@code AzBakedAnimationCache.getOrNull} returned null and {@code AzAnimator.getAnimation} dereferenced it:
     * instant NPE on the render thread, crashing the client the moment a yautja ticked.
     * <p>
     * ⚠ It lay dormant because NOTHING EVER DISPATCHED AN ANIMATION — the dispatcher was an empty shell until the
     * caster and locomotion work filled it in, at which point the very first client tick of every yautja walked
     * straight into it. {@code YautjaRenderer} next door uses the correct name, which is why the model always drew.
     */
    private static final String NAME = "yautja_jungle";

    private static final ResourceLocation ANIMATION = PredatorResources.entityAnimationLocation(NAME);

    /**
     * Mid-body, in geo units above the feet. The model stands about 40 units tall (the hip pivot is at 15 and the head
     * at ~32), so 20 puts the swim rotation through the middle of the torso.
     */
    private static final float BODY_CENTRE_HEIGHT = 20.0F;

    public YautjaAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Yautja> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME)
                .setTransitionLength(5)
                .build()
        );

        // The caster's own track. Shares no bone with the body track — see YautjaAnimationRefs for why that is what
        // makes independent fire work rather than a trick. Shorter transition: the arm should snap up, not drift.
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, YautjaAnimationRefs.CASTER_CONTROLLER_NAME)
                .setTransitionLength(3)
                .build()
        );

        // The attack track. Short transition so a swing snaps in and hands the arms straight back to the walk.
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, YautjaAnimationRefs.ATTACK_CONTROLLER_NAME)
                .setTransitionLength(2)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Yautja animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Yautja animatable, float partialTick) {
        showHelmet(animatable, context());
        showWristBlades(animatable, context());
        applySwimPitch(animatable, context());
        applyCasterAim(animatable, context());
        applyHangTilt(animatable, context());

        // ⚠⚠ THE PROCEDURAL LIMB SWING IS GONE, AND MUST STAY GONE.
        //
        // BasicAnimationUtils.applyLimbRotations used to run here, writing gLeftArm, gRightArm, gLeftLeg and
        // gRightLeg with setRotX every frame. That was the right call while the yautja played no clips at all — it
        // was the only thing making the legs move. It is exactly wrong now: idle, walk and run all animate those
        // four bones, and setRotX OVERWRITES rather than adds, so the helper would flatten the X component of every
        // locomotion clip and leave a yautja gliding with a vanilla-zombie limb swing.
        //
        // The head look-at stays. It targets gNeckUpper, which no clip animates — idle and run drive gNeckLower and
        // gHead instead — so it composes with the clips rather than fighting them.
        BasicAnimationUtils.applyHeadRotations(animatable, context(), partialTick, "gNeckUpper", 0F);
    }

    /**
     * Tips the whole body to the direction it is swimming. <strong>Why the clip alone is not enough</strong> ⚠
     * {@code swim} is authored as a SURFACE stroke — it sets {@code gWaist} to +45 and then cancels that at the neck
     * with -22.5 on {@code gNeckLower} and {@code gHead}, so the body angles down while the head stays level. That is
     * right for wading across a river and wrong for a dive, which is the "head up and kicking" he saw. The clip is left
     * alone; the whole rig is pitched underneath it instead. <strong>⚠⚠ It rotates about the BODY CENTRE, not the
     * feet</strong> {@code root} pivots at the feet, so rotating it 60 degrees there would swing the head through an
     * arc almost three blocks long and leave the model visibly detached from its own hitbox. Instead the pivot is MOVED
     * to mid body for the duration, which needs no translation to compensate — moving a pivot without rotating is a
     * no-op, so this costs nothing on land. It is reset to zero when not swimming; leaving it raised would silently
     * change the origin for anything that rotates root later.
     * <p>
     * ⚠ {@code root} POSITION is deliberately untouched: the swim clip drives a vertical bob through it, and writing
     * that channel would flatten the stroke.
     */
    private static void applySwimPitch(Yautja entity, AzAnimationContext<?> context) {
        var root = context.boneCache().getBakedModel().getBoneOrNull("root");

        if (root == null) {
            return;
        }

        var pitch = entity.getClientSwimPitch();

        if (Math.abs(pitch) < 0.05F) {
            root.setPivotY(0.0F);
            root.setRotX(0.0F);
            return;
        }

        root.setPivotY(BODY_CENTRE_HEIGHT);

        // Same sign convention as the head look-at in BasicAnimationUtils: nose-down is a NEGATIVE setRotX.
        root.setRotX(-pitch * Mth.DEG_TO_RAD);
    }

    /**
     * Points the caster at whatever it is shooting.
     * <p>
     * <b>⚠⚠ ADDITIVE, ON THE MOUNT — and the first version was neither, which is why the arm sat off to one side.</b>
     * It wrote an ABSOLUTE yaw onto {@code gCasterArm}, a bone whose parent {@code gCasterMount} the clip has already
     * yawed by -60 degrees. So a world-relative angle was being applied inside a frame rotated 60 degrees out, and the
     * caster pointed roughly that far wide of the target.
     * <p>
     * Reading {@code getRotY} and adding to it sidesteps the whole problem: it needs no knowledge of what the clip set,
     * or of how bedrock degrees map onto Az radians. {@code AzAnimator.animate} runs every track BEFORE calling this,
     * so the clip's value is already in the bone and the sum is the aim.
     * <p>
     * ⚠ Both axes go on {@code gCasterMount} now. {@code gCasterArm} is left entirely to the clip.
     */
    /**
     * The deploy pose {@code caster.ready} puts on {@code gCasterMount}, in bone radians.
     * <p>
     * ⚠ Bedrock says -60; the animation deserializer negates Y, so the bone carries +60. If the clip is re-authored,
     * this is the second place to change.
     */
    /**
     * How far the body leans back under a lip. Deliberately partial — fully horizontal would put the yautja inside the
     * block it is hanging from, which is exactly what he asked to avoid.
     */
    private static final float HANG_TILT_DEGREES = -35.0F;

    private static final float CASTER_MOUNT_BASE_YAW_RADIANS = (float) Math.toRadians(60.0);

    private static void applyCasterAim(Yautja entity, AzAnimationContext<?> context) {
        if (!entity.isCasterDeployed()) {
            return;
        }

        var mount = context.boneCache().getBakedModel().getBoneOrNull("gCasterMount");

        if (mount == null) {
            return;
        }

        var deltaYaw = Mth.clamp(
            Mth.wrapDegrees(entity.getCasterAimYaw() - entity.yBodyRot),
            -PlasmaCaster.AIM_YAW_LIMIT,
            PlasmaCaster.AIM_YAW_LIMIT
        );

        var pitch = Mth.clamp(
            entity.getCasterAimPitch(),
            -PlasmaCaster.AIM_PITCH_LIMIT,
            PlasmaCaster.AIM_PITCH_LIMIT
        );

        // ⚠⚠ ABSOLUTE, NOT ADDITIVE — additive is what made the gun spin.
        // AzCachedBoneUpdateUtil.updateCachedBoneRotation folds a bone's CURRENT rotation back into its
        // snapshot once the reset completes: saveSnapshot.updateRotation(bone.getRotX(), ...). The clip then
        // animates relative to that snapshot, so anything added here becomes part of next frame's baseline and
        // compounds every frame. Reading getRotY() and adding to it is a feedback loop, not an offset.
        //
        // Writing the aim absolutely replaces the clip's -60 yaw on this bone, which is the right outcome: that
        // -60 was a fixed "point roughly forward" pose, and an actual target angle is strictly better. The
        // deployed LOOK comes from gCasterArm and gCaster, which the clip still owns.
        // ⚠⚠ THE CLIP BASE HAS TO BE ADDED BACK — without it the caster aims from its STOWED orientation and
        // ends up pointing into the yautja's own head. Writing absolutely (which is necessary; see above)
        // replaces whatever the clip put on this bone, and what the clip puts there is the deploy pose.
        //
        // The base is +60 degrees, NOT -60. caster.ready holds gCasterMount at [0, -60, 0] in bedrock, and
        // AzBakedAnimationsJsonDeserializer negates X and Y on the way in — Math.toRadians(-rawYValue) — so a
        // bedrock -60 lands on the bone as +60. Read from the deserializer, not assumed from the JSON.
        mount.setRotY(CASTER_MOUNT_BASE_YAW_RADIANS - deltaYaw * Mth.DEG_TO_RAD);

        // No base for pitch: every caster clip holds gCasterMount at x = 0.
        mount.setRotX(-pitch * Mth.DEG_TO_RAD);
    }

    private static void showWristBlades(Yautja entity, AzAnimationContext<?> context) {
        var bakedModel = context.boneCache().getBakedModel();
        var blade = bakedModel.getBoneOrNull("gWristBlade");

        if (blade != null) {
            blade.setHidden(!entity.getMainHandItem().isEmpty() && !entity.isAggressive());
        }
    }

    /**
     * Tilts the body back when hanging under a one-block lip.
     * <p>
     * His note: the upper body holds the block and the feet swing up toward its underside — diagonal, not flat, so the
     * yautja is not inside the block it is hanging from. The head drops clear as a result, which is the pose and the
     * suffocation fix in one.
     * <p>
     * ⚠ The HITBOX half is separate — {@code Yautja.getDefaultDimensions} drops to 1.5 while hanging. Both read the
     * SAME synced flag, so the box and the pose can never disagree.
     * <p>
     * ⚠ Written EVERY FRAME while hanging, not toggled on the transition: a bone the animator stops writing gets lerped
     * back toward its bind pose, so a one-shot set would sag out of the tilt over the following second.
     * <p>
     * ⚠ The sign is the one thing here a compiler cannot check. If it tips FORWARD into the wall rather than back under
     * the lip, negate {@link #HANG_TILT_DEGREES}.
     */
    private static void applyHangTilt(Yautja entity, AzAnimationContext<?> context) {
        if (!entity.isClimbHanging()) {
            return;
        }

        var root = context.boneCache().getBakedModel().getBoneOrNull("root");

        if (root != null) {
            root.setRotX(HANG_TILT_DEGREES * Mth.DEG_TO_RAD);
        }
    }

    private static void showHelmet(Yautja entity, AzAnimationContext<?> context) {
        var bakedModel = context.boneCache().getBakedModel();
        var helmet = bakedModel.getBoneOrNull("gArmorMask");

        if (helmet != null) {
            helmet.setHidden(!entity.hasMask());
        }
    }
}
