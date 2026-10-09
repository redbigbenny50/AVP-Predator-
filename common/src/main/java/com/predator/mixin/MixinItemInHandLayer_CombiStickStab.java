package com.predator.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates the combi stick itself during a stab, in third person.
 * <p>
 * <strong>⚠⚠ THE ARM SWING ALONE IS NOT THE THRUST.</strong> The arm was already animating correctly, but the spear
 * stayed in its carry pose the whole time — so it read as a swing with a weapon along for the ride. The reference mod
 * does this in a SECOND mixin, on the held-item layer, rotating the ITEM so the tip comes down and points forward while
 * the arm comes up. That is the missing half of the motion.
 * <p>
 * <strong>⚠ Driven by the SAME {@code (thrust - settle)} term as the arm pose</strong> in
 * {@code MixinHumanoidModel_CombiStickPose}. One value drives both, which is why they read as a single motion rather
 * than two things happening near each other. If you retune one, retune the other.
 * <p>
 * <strong>⚠ rotateAround, NOT mulPose.</strong> It rotates about a pivot in front of and below the grip, so the shaft
 * pulls BACK as the tip drops. A plain {@code mulPose} spins about the origin and merely tilts the whole spear.
 * <p>
 * <strong>⚠ {@code Axis.XN}</strong> — negative X. {@code XP} points the tip at the ground instead of forward.
 */
@Mixin(ItemInHandLayer.class)
/*
 * ⚠ TUNING CONSTANTS BELOW ARE DELIBERATELY **NOT final**. javac INLINES a `static final` primitive at every use site,
 * so editing one and hot-swapping does nothing — the old literal is already baked into the bytecode. Dropping `final`
 * forces a real field read each frame, which HotSwap can then change while the game is running. The cost is one field
 * load per frame. It is nothing, and it turns a 2-minute relaunch into a keystroke.
 */
public abstract class MixinItemInHandLayer_CombiStickStab {

    /**
     * ⚠ Degrees at peak thrust, from the reference. Flip the sign on AVP_STAB_ROTATION if the tip still goes the wrong
     * way — that single constant controls which end swings forward.
     */
    private static float AVP_STAB_ROTATION = 45.0F;

    /**
     * ⚠⚠ THE PIVOT IS WHAT DECIDES WHETHER THE HANDLE STAYS IN THE HAND. rotateAround spins the item about this point,
     * so anything away from the grip swings the handle out of the fist — which is what dropped it. At (0, 0, 0) it
     * rotates IN the hand and the handle cannot leave.
     * <p>
     * ⚠ Raise AVP_PIVOT_Y to lever the tip down harder while keeping the grip planted; push AVP_PIVOT_Z negative to
     * draw the shaft back. Start from zero and add only what you need.
     */
    private static float AVP_PIVOT_Y = 0.0F;

    /** ⚠ See {@link #AVP_PIVOT_Y}. Negative draws the shaft back toward the shoulder. */
    private static float AVP_PIVOT_Z = 0.0F;

    /** ⚠ How far the spear drives forward at full thrust, in blocks. */
    private static float AVP_EXTEND = 0.4F;

    /**
     * A small constant lift on the grip during the drive, in blocks.
     * <p>
     * ⚠ NOT a hand-follow. Vanilla already attaches the item to the fist; an arc-follow here caused a one-frame
     * 0.75-block jump. This is a gentle nudge and nothing more — if a real gap appears, look at the PIVOT first.
     */
    private static float AVP_LIFT = -0.15F;

    /** ⚠ MUST equal AVP_WINDUP_END in MixinHumanoidModel_CombiStickPose. */
    private static float AVP_WINDUP_END = 0.15F;

    /** ⚠ MUST equal AVP_THRUST_END in MixinHumanoidModel_CombiStickPose. */
    private static float AVP_THRUST_END = 0.24F;

    /**
     * ⚠⚠ THE MATCHING popPose IS IN {@link #avp_predator$popStabRotation}. Without it this transform LEAKS.
     * <p>
     * {@code ItemInHandLayer.render} calls {@code renderArmWithItem} ONCE PER HAND, and a HEAD inject lands outside
     * vanilla's own push. So the rotation applied for the main hand was still on the stack when the OFF-HAND item was
     * drawn — which is why the off-hand item swung backwards no matter how many times I removed arm-posing code. It was
     * never the arm; it was an unpopped matrix.
     */
    @Inject(
        method = "renderArmWithItem",
        at = @At("HEAD")
    )
    private void avp_predator$stabRotation(
        LivingEntity entity,
        ItemStack stack,
        ItemDisplayContext displayContext,
        HumanoidArm arm,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo callback
    ) {
        if (!avp_predator$shouldStab(entity, stack)) {
            return;
        }

        var attackTime = ((net.minecraft.world.entity.player.Player) entity).getAttackAnim(
            net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)
        );

        // ⚠ Identical to the arm pose's terms — see the class javadoc.
        // ⚠⚠ THESE WINDOWS MUST MATCH MixinHumanoidModel_CombiStickPose EXACTLY. The arm and the spear are one
        // motion; if the windows drift apart the spear rotates at a different moment from the arm and the whole
        // thing reads as two animations. They are duplicated here rather than shared because a mixin cannot
        // reference another mixin's fields at runtime.
        var thrust = Mth.clamp(Mth.inverseLerp(attackTime, AVP_WINDUP_END, AVP_THRUST_END), 0.0F, 1.0F);

        thrust = thrust * thrust;

        var settle = Mth.clamp(Mth.inverseLerp(attackTime, 0.4F, 1.0F), 0.0F, 1.0F);

        if (settle < 0.5F) {
            settle = settle == 0.0F ? 0.0F : (float) (Math.pow(2.0, 20.0 * settle - 10.0) / 2.0);
        } else {
            settle = settle == 1.0F ? 1.0F : (float) ((2.0 - Math.pow(2.0, -20.0 * settle + 10.0)) / 2.0);
        }

        var drive = thrust - settle;

        // ⚠⚠ ARM SPACE, NOT ITEM SPACE — this is why the reference's own numbers had to change.
        // The inject is at HEAD of renderArmWithItem, so it runs BEFORE the display transform. Their spear is
        // "parent": "item/generated", a FLAT SPRITE whose local +Y runs along the blade, so translating +Y drove
        // their point forward. Ours is a 3D geo: in ARM space +Y is simply "up out of the hand", which is exactly
        // what it did — the stick rose away from the grip, handle first.
        //
        // ⚠ FORWARD IN ARM SPACE IS -Z. The drive is on Z now, not Y.
        // ⚠ Axis.XP, not XN. XN tipped the HANDLE forward and the point backwards — the rotation was inverted for
        // our model's orientation.
        // ⚠⚠ NO HAND-FOLLOW. Vanilla runs translateToHand BEFORE calling renderArmWithItem, so the pose stack is
        // ALREADY at the fist — the item is attached by construction and needs no help staying there.
        //
        // I previously added an arc-follow to close a gap. It was solving a problem that did not exist, and it
        // introduced the split-second vertical flash: at attackTime 0.05 the windUp term reaches 90 degrees, which
        // the follow turned into a 0.75-block displacement in a single frame, then snapped back.
        //
        // ⚠ BOTH the rotation and the drive use (thrust - settle) ONLY — no windUp — which is exactly what the
        // reference does, and for the same reason.
        // ⚠ A small CONSTANT lift, scaled by the drive — NOT a hand-follow. The item is already attached to the
        // fist; this only nudges the grip up so it seats better through the thrust.
        // ⚠ PUSH FIRST. Everything below is undone in the RETURN inject.
        poseStack.pushPose();

        poseStack.translate(0.0F, AVP_LIFT * drive, -AVP_EXTEND * drive);

        poseStack.rotateAround(
            Axis.XP.rotationDegrees(AVP_STAB_ROTATION * drive),
            0.0F,
            AVP_PIVOT_Y,
            AVP_PIVOT_Z
        );
    }

    /**
     * Pops the matrix pushed in the HEAD inject.
     * <p>
     * ⚠⚠ WITHOUT THIS THE TRANSFORM LEAKS. {@code ItemInHandLayer.render} calls {@code renderArmWithItem} ONCE PER
     * HAND, and a HEAD inject lands outside vanilla's own push — so the rotation applied for the main hand was still on
     * the stack when the OFF-HAND item was drawn. That is why the off-hand item swung backwards no matter how much
     * arm-posing code I removed. It was never the arm; it was an unpopped matrix.
     */
    @Inject(
        method = "renderArmWithItem",
        at = @At("RETURN")
    )
    private void avp_predator$popStabRotation(
        LivingEntity entity,
        ItemStack stack,
        ItemDisplayContext displayContext,
        HumanoidArm arm,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo callback
    ) {
        if (avp_predator$shouldStab(entity, stack)) {
            poseStack.popPose();
        }
    }

    /**
     * ⚠ Single source of truth for the guard.
     * <p>
     * The push and the pop MUST agree exactly. If one fires and the other does not, the pose stack goes out of balance
     * and the whole entity render corrupts — a far worse failure than the one being fixed. Sharing the predicate makes
     * drift impossible.
     */
    private static boolean avp_predator$shouldStab(LivingEntity entity, ItemStack stack) {
        if (!stack.is(com.predator.common.registry.init.item.PredatorItems.COMBI_STICK.get())) {
            return false;
        }

        if (
            !(entity instanceof net.minecraft.world.entity.player.Player player)
                || !CombiStickItem.isExtendedInHand(player)
        ) {
            return false;
        }

        return player.getAttackAnim(
            net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)
        ) > 0.0F;
    }
}
