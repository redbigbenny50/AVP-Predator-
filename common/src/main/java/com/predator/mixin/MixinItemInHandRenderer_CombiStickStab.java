package com.predator.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.client.input.CombiStickCharge;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The FIRST-PERSON combi stick stab and charge pose.
 * <p>
 * <strong>⚠⚠ THE TRANSFORM GOES ON THE TAIL OF applyItemArmTransform, NOT THE HEAD OF renderArmWithItem.</strong> That
 * was the bug: {@code renderArmWithItem} calls {@code pushPose()} and only then {@code applyItemArmTransform}, which is
 * what actually PLACES the item in front of the camera. Transforming before that rotated the entire coordinate frame
 * the item was about to be positioned in, so the hand swung around but the spear never turned in place. Injecting on
 * the TAIL of the placement means we rotate the item where it already sits, which is what "the tip drops" requires.
 * <p>
 * <strong>⚠ No push/pop needed here.</strong> We are already inside vanilla's own push, and it pops at the end of
 * {@code renderArmWithItem}. Adding another pair would be a second unbalanced risk for no gain.
 * <p>
 * <strong>⚠ Y IS NOT FLIPPED IN FIRST PERSON.</strong> Third person goes through {@code LivingEntityRenderer} and its
 * {@code scale(-1, -1, 1)}, which is why the third-person lift is NEGATIVE for "up". Here positive is up.
 * <p>
 * <strong>⚠ Timing windows must match {@link MixinHumanoidModel_CombiStickPose}</strong> or the two views run at
 * visibly different speeds.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer_CombiStickStab {

    /**
     * Degrees at peak.
     * <p>
     * ⚠⚠ NEGATIVE HERE, POSITIVE IN THIRD PERSON — and that is the same Y-flip story as the lift. Third person goes
     * through {@code LivingEntityRenderer} with its {@code scale(-1, -1, 1)}; first person does not. At +40 the BOTTOM
     * of the shaft swung out and it read as a golf swing rather than a thrust, because the rotation was going the wrong
     * way about the grip.
     * <p>
     * ⚠ If it ever reads as the wrong end leading again, this SIGN is the dial, not the magnitude.
     */
    private static float AVP_FP_STAB_ROTATION = -55.0F;

    /** ⚠ POSITIVE IS UP here, unlike the third-person twin. */
    private static float AVP_FP_LIFT = 0.30F;

    /** ⚠ Forward drive in blocks. */
    private static float AVP_FP_EXTEND = 0.55F;

    /**
     * How far the spear DRAWS BACK during the wind-up, in blocks.
     * <p>
     * ⚠⚠ THE ITEM HAD NO PULL-BACK AT ALL. Both views drove the item purely from {@code (thrust - settle)}, which only
     * ever moves FORWARD — so the spear had nothing to thrust FROM and the motion read as a smack rather than a stab.
     * The arm was winding up; the spear was not.
     * <p>
     * ⚠ Positive Z is backwards here, toward the camera.
     */
    private static float AVP_FP_DRAW = 0.28F;

    /**
     * Degrees the tip drops during the wind-up.
     * <p>
     * ⚠⚠ THIS IS WHAT MAKES THE THRUST TRAVEL STRAIGHT. It shares a sign with the stab rotation, so the draw already
     * starts the tip downward — at 22 it only got part of the way, leaving the remaining rotation to happen DURING the
     * drive. That is what read as a swipe: the spear was still turning while it was moving forward, so the point traced
     * an arc instead of a line.
     * <p>
     * ⚠ At 40 most of the turn is finished before the thrust begins, so the drive is nearly pure translation. Raise it
     * further if any arc remains; the ceiling is where it looks like it is aiming at the floor.
     */
    private static float AVP_FP_DRAW_ROTATION = 40.0F;

    /** ⚠ Radians at full charge, from the charge mixin this one absorbed. */
    private static float AVP_FP_MAX_LIFT = 1.15F;

    /** ⚠ MUST equal AVP_WINDUP_END in the pose mixin. */
    private static float AVP_WINDUP_END = 0.15F;

    /** ⚠ MUST equal AVP_THRUST_END in the pose mixin. */
    private static float AVP_THRUST_END = 0.24F;

    /**
     * Removes vanilla's first-person swing while an extended combi stick is held.
     * <p>
     * ⚠⚠ WITHOUT THIS NOTHING ELSE SHOWS — vanilla's transform would be applied over ours and the stab would keep
     * reading as an ordinary sword swipe. Same lesson as third person, where {@code setupAttackAnimation} had to be
     * cancelled outright.
     */
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void avp_predator$suppressVanillaSwing(
        PoseStack poseStack,
        HumanoidArm arm,
        float swingProgress,
        CallbackInfo callback
    ) {
        var player = Minecraft.getInstance().player;

        if (player != null && CombiStickItem.isExtendedInHand(player)) {
            callback.cancel();
        }
    }

    /**
     * Applies the stab and the charge pose, AFTER vanilla has placed the item.
     * <p>
     * ⚠ {@code applyItemArmTransform} runs for BOTH hands, so the arm is checked against the player's main arm — the
     * off-hand must not inherit the spear's motion. That mistake, in the third-person equivalent, is what made the
     * off-hand item swing backwards.
     */
    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void avp_predator$stab(PoseStack poseStack, HumanoidArm arm, float equippedProgress, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;

        if (player == null || arm != player.getMainArm() || !CombiStickItem.isExtendedInHand(player)) {
            return;
        }

        var side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        avp_predator$applyCharge(poseStack, side);

        var partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        var attackTime = player.getAttackAnim(partialTick);

        if (attackTime <= 0.0F) {
            return;
        }

        // ⚠ Identical terms to the third-person mixins. One motion, two views.
        var thrust = Mth.clamp(Mth.inverseLerp(attackTime, AVP_WINDUP_END, AVP_THRUST_END), 0.0F, 1.0F);

        thrust = thrust * thrust;

        var settle = Mth.clamp(Mth.inverseLerp(attackTime, 0.4F, 1.0F), 0.0F, 1.0F);

        if (settle < 0.5F) {
            settle = settle == 0.0F ? 0.0F : (float) (Math.pow(2.0, 20.0 * settle - 10.0) / 2.0);
        } else {
            settle = settle == 1.0F ? 1.0F : (float) ((2.0 - Math.pow(2.0, -20.0 * settle + 10.0)) / 2.0);
        }

        // ⚠ The wind-up term, which the item was missing. It peaks as the thrust begins and is gone by the time
        // the drive is at full, so the spear draws back and THEN goes forward instead of only going forward.
        var windUp = -(Mth.cos(
            Mth.PI * Mth.clamp(Mth.inverseLerp(attackTime, 0.0F, AVP_WINDUP_END), 0.0F, 1.0F)
        ) - 1.0F) / 2.0F;

        var draw = windUp * (1.0F - thrust);
        var drive = thrust - settle;

        poseStack.translate(
            0.0F,
            AVP_FP_LIFT * drive,
            AVP_FP_DRAW * draw - AVP_FP_EXTEND * drive
        );

        poseStack.mulPose(
            Axis.XP.rotationDegrees((AVP_FP_STAB_ROTATION * drive - AVP_FP_DRAW_ROTATION * draw) * side)
        );
    }

    /** The charge wind-back, folded in from the old charge mixin so both live inside vanilla's single push. */
    private void avp_predator$applyCharge(PoseStack poseStack, float side) {
        var player = net.minecraft.client.Minecraft.getInstance().player;

        if (!CombiStickCharge.charging(player)) {
            return;
        }

        var charge = Mth.clamp(
            CombiStickCharge.charge(player)
                / (float) com.predator.common.gameplay.item.combistick.CombiStickItem.FULL_CHARGE_TICKS,
            0.0F,
            1.0F
        );

        // ⚠ Eased, not linear: a linear lift snaps at the start of the hold and reads mechanically.
        var lift = AVP_FP_MAX_LIFT * (charge * charge * (3.0F - 2.0F * charge));

        poseStack.translate(side * lift * 0.16F, lift * 0.22F, lift * 0.30F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-lift * 34.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * lift * 12.0F));
    }
}
