package com.predator.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.common.gameplay.item.battleaxe.BattleaxeItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the off-hand arm in first person while the battleaxe is held.
 * <p>
 * [stated] "in first person the arm with the gauntlet should not be visible on screen."
 * <p>
 * ⚠ FIRST PERSON ONLY. The worn gauntlet stays visible in THIRD person — [stated] "if the player has one in the slot we
 * should still see them wearing one in 3rd person" — because that is drawn by the arm rig, not by this renderer, and
 * nothing here touches it. Cancelling the off-hand draw here removes the arm from the screen and leaves the item
 * exactly where it is in the inventory.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer_BattleaxeFirstPerson {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void avp_predator$hideOffHandWhileTwoHanded(
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
        if (hand == InteractionHand.OFF_HAND && player.getMainHandItem().getItem() instanceof BattleaxeItem) {
            callback.cancel();
        }
    }

    /** First person, RAISED: the axe tips back over the shoulder. */
    private static final float AVP_FP_RAISE_DEGREES = 50.0F;

    /** First person, STRUCK: driven down — negative X, the same direction as vanilla's own chop (-80 in its swing). */
    private static final float AVP_FP_STRIKE_DEGREES = -45.0F;

    /**
     * [stated] "jump while raising the axe, it holds, then slams down when you land" — in first person too. Hooked at
     * the END of vanilla's applyItemArmTransform, where the pose stack sits at the hand, so the axe turns about the
     * grip.
     */
    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void avp_predator$slamFirstPerson(
        PoseStack poseStack,
        net.minecraft.world.entity.HumanoidArm arm,
        float equipProgress,
        CallbackInfo callback
    ) {
        var player = net.minecraft.client.Minecraft.getInstance().player;

        if (player == null || arm != player.getMainArm() || !(player.getMainHandItem().getItem() instanceof BattleaxeItem)) {
            return;
        }

        var partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        var slam = com.predator.common.gameplay.item.battleaxe.BattleaxeSlamPose.of(
            player.getMainHandItem(),
            player.level().getGameTime() + partial,
            player
        );

        if (!slam.active()) {
            return;
        }

        poseStack.translate(0.0F, 0.12F * slam.raise(), 0.0F);
        poseStack.mulPose(
            com.mojang.math.Axis.XP.rotationDegrees(slam.raise() * AVP_FP_RAISE_DEGREES + slam.strike() * AVP_FP_STRIKE_DEGREES)
        );
    }
}
