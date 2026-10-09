package com.predator.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.common.gameplay.item.ShurikenItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses the first-person shuriken wind-up exactly where a CHARGED BOW sits.
 * <h2>⚠⚠ WHY A NUDGE COULD NOT WORK, AND WHY THIS REPLACES THE POSE INSTEAD</h2> The first attempt hooked the TAIL of
 * {@code applyItemArmTransform} and offset from there. That can never fix the height, because vanilla's SPEAR branch
 * calls {@code applyItemArmTransform} FIRST and then applies its own transforms on top:
 *
 * <pre>
 * poseStack.translate(k * -0.5F, 0.7F, 0.1F); // +0.7 on Y - the trident hoist
 * </pre>
 *
 * Every offset added at the earlier hook was simply overwritten a few lines later. [stated] Sep 25: "this is still way
 * to high and it doesnt seem to launch from the hand throwing it."
 * <p>
 * ⭐ So this cancels vanilla's whole hand render for a winding-up shuriken and rebuilds it with the BOW numbers, which
 * is what he asked for - "this needs to be in the same location as the bow being charged". They are vanilla's own
 * constants, copied from the bow branch of {@code renderArmWithItem}, including the draw shake and the slight push
 * along Z as the charge builds.
 * </p>
 * <p>
 * ⚠ FIRST PERSON ONLY. {@code ShurikenItem.getUseAnimation} still returns SPEAR, which is what gives THIRD person the
 * cocked arm that holds - and third person is correct already. Nothing here touches it.
 * </p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer_ShurikenFirstPerson {

    /** Vanilla's bow-charge constants, from the BOW branch of renderArmWithItem. */
    private static final float AVP_BOW_X = -0.4785682F;

    private static final float AVP_BOW_Y = -0.094387F;

    private static final float AVP_BOW_Z = 0.05731531F;

    private static final float AVP_BOW_PITCH = -11.935F;

    private static final float AVP_BOW_YAW = 65.3F;

    private static final float AVP_BOW_ROLL = -9.785F;

    /** How long a full wind-up takes, for the draw shake. The bow uses its own charge duration; ours is fixed. */
    private static final float AVP_DRAW_TICKS = 10.0F;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void avp_predator$shurikenBowPose(
        AbstractClientPlayer player,
        float partialTick,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo callback
    ) {
        if (
            !(stack.getItem() instanceof ShurikenItem)
                || !player.isUsingItem()
                || player.getUsedItemHand() != hand
                || player.getUseItemRemainingTicks() <= 0
        ) {
            return;
        }

        var arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        var side = arm == HumanoidArm.RIGHT ? 1 : -1;

        poseStack.pushPose();

        // applyItemArmTransform is private, so its one line is reproduced here rather than reached for.
        poseStack.translate(side * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

        poseStack.translate(side * AVP_BOW_X, AVP_BOW_Y, AVP_BOW_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(AVP_BOW_PITCH));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * AVP_BOW_YAW));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * AVP_BOW_ROLL));

        var used = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1.0F);
        var charge = Math.min(1.0F, used / AVP_DRAW_TICKS);

        // The bow's draw shake: it only starts once the pull is past a tenth, so a tap does not jitter.
        if (charge > 0.1F) {
            var shake = Mth.sin((used - 0.1F) * 1.3F) * (charge - 0.1F);

            poseStack.translate(0.0F, shake * 0.004F, 0.0F);
        }

        poseStack.translate(0.0F, 0.0F, charge * 0.04F);
        poseStack.mulPose(Axis.YN.rotationDegrees(side * 45.0F));

        ((ItemInHandRenderer) (Object) this).renderItem(
            player,
            stack,
            arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            arm != HumanoidArm.RIGHT,
            poseStack,
            buffer,
            packedLight
        );

        poseStack.popPose();
        callback.cancel();
    }
}
