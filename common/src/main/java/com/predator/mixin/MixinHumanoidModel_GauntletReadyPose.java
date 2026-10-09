package com.predator.mixin;

import com.blib.internal.client.animation.AzAnimatorAccessor;
import com.predator.common.gameplay.item.GauntletItem;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the gauntlet arm into the ready pose in third person while a gauntlet is worn.
 * <h2>⚠ The offhand arm, read from the player</h2> Opposite of {@code getMainArm()}, so a left-handed player raises the
 * right arm. Yaw is signed per side: positive brings the LEFT arm inward, negative the right — the same signs vanilla's
 * bow pose uses.
 * <h2>⚠⚠ THE ARM DOES NOT FOLLOW THE CAMERA</h2> The first version added head pitch and head yaw into the pose, so
 * looking around bent the raised arm at the shoulder in every direction — [stated] "it looks like the arm is broken".
 * Now it holds the ready pose relative to the BODY (the body already turns with movement direction, the way vanilla
 * turns a held item) and only carries vanilla's small idle sway. [stated] "a little wobble is fine".
 * <h2>⚠ THE ARM FOLLOWS THE CLIP'S PITCH</h2> {@code GauntletArmLayer} cancels the rig arm's rotation on purpose (the
 * real arm supplies it), which meant the fire clip's arm motion never reached third person. The rig arm's live pitch,
 * read from the stack's animator, is compared with the ready pitch and the difference added here — so the arm recoils
 * in third person by exactly what the clip says. Rig X and vanilla X run opposite ways (rig +90 raises, vanilla -90
 * raises), hence the subtraction.
 * <h2>⚠ TAIL of setupAnim, listed AFTER the combi stick pose in the mixin config</h2> Both inject at TAIL; the later
 * listed mixin's call lands closer to the return and so runs last. The stab subtracts body yaw from BOTH arms' yaw;
 * this sets the gauntlet arm absolutely afterwards, so the stab cannot drag it.
 * <h2>⚠ Dials are non-final statics</h2> A {@code static final} primitive is inlined by javac and cannot be
 * hot-swapped. Radians: {@code -1.5708} is dead horizontal.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModel_GauntletReadyPose extends EntityModel<LivingEntity> {

    /** How far the forearm comes up. -1.35 rad is ~77 degrees: raised and forward, wrist door facing up-and-out. */
    private static float AVP_READY_PITCH = -1.35F;

    /**
     * The rig's own ready pitch, so the third-person arm can follow the CLIP: whatever the rig arm does beyond this
     * (the fire recoil, the open swing) is added to AVP_READY_PITCH. Rig radians, positive = raised. ⚠ Matches the
     * left/right .ready keyframe (90 degrees); if the ready clip is re-authored at a different angle, move this.
     */
    private static float AVP_RIG_READY_PITCH = Mth.HALF_PI;

    /** Inward yaw toward the body's centre line, radians. Relative to the BODY, not the head — see below. */
    private static float AVP_READY_YAW = 0.4F;

    /** Idle wobble amplitude, the multiplier vanilla's own {@code bobModelPart} takes (1.0 = a hanging arm's sway). */
    private static float AVP_READY_BOB = 0.6F;

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    /** Winding up a caster shot, or inside the fire animation's window. */
    private static boolean avp_predator$isFiring(Player player) {
        return com.predator.client.handcaster.CasterChargeClientState.isCharging(player);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void avp_predator$poseGauntletArm(
        LivingEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        CallbackInfo callback
    ) {
        if (!(entity instanceof Player player) || GauntletItem.equipped(player).isEmpty()) {
            return;
        }

        // ⭐ [stated] Sep 25: "having the arm raised all the time for the gauntlet can feel awkward ... and of course
        // when firing the gauntlet". Wearing one is no longer enough - the arm comes up only while a shot is actually
        // being wound up or fired, then drops back to vanilla.
        //
        // ⚠⚠ THIS IS THE THIRD-PERSON MODEL ONLY, which is also what every OTHER player sees. That is why the charge
        // state is synced (S2CCasterChargePayload) instead of read from a client-local flag: this method runs once per
        // rendered player, so a global boolean would raise every predator's arm at once and never a remote player's.
        // First person is a different render path entirely and is deliberately untouched - the gauntlet stays visible
        // there so you can see you are armed.
        if (!avp_predator$isFiring(player)) {
            return;
        }

        // ⚠ Poses vanilla owns outright are left alone: a raised arm mid-glide or mid-swim looks broken.
        if (player.isFallFlying() || player.isVisuallySwimming() || player.isSleeping()) {
            return;
        }

        var left = GauntletItem.arm(player) == HumanoidArm.LEFT;
        var arm = left ? leftArm : rightArm;
        var side = left ? 1.0F : -1.0F;

        arm.xRot = AVP_READY_PITCH - (rigPitch(GauntletItem.equipped(player), left) - AVP_RIG_READY_PITCH);
        arm.yRot = side * AVP_READY_YAW;
        arm.zRot = 0.0F;

        // Vanilla's own idle sway, scaled down. bobArms passes +1 for the RIGHT arm and -1 for the LEFT, so -side.
        AnimationUtils.bobModelPart(arm, ageInTicks, -side * AVP_READY_BOB);
    }

    /** {@return the rig arm bone's current X rotation, or the ready pitch when nothing can be read} */
    private static float rigPitch(ItemStack stack, boolean left) {
        var animator = AzAnimatorAccessor.<java.util.UUID, ItemStack>getOrNull(stack);

        if (animator == null) {
            return AVP_RIG_READY_PITCH;
        }

        var model = animator.context().boneCache().getBakedModel();

        if (model == null) {
            return AVP_RIG_READY_PITCH;
        }

        var bone = model.getBoneOrNull(left ? "leftArm" : "rightArm");

        // ⚠ A bone at exactly 0 has not been posed by any clip yet (first frame); treat it as ready, not as a 90
        // degree recoil.
        return bone == null || bone.getRotX() == 0.0F ? AVP_RIG_READY_PITCH : bone.getRotX();
    }
}
