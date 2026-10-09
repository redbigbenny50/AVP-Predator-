package com.predator.client.render.layer;

import com.blib.internal.client.animation.AzAnimatorAccessor;
import com.blib.internal.client.render.item.AzItemArmRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.common.gameplay.item.GauntletItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Draws the WORN gauntlet on the player's real arm in third person.
 * <h2>⚠⚠ WORN MEANS PARENTED TO THE ARM BONE, LIKE ARMOUR — NOT PLACED BY DISPLAY TRANSFORMS</h2> A held item hangs off
 * the hand and is offset by {@code thirdperson_*}; it can never track the forearm. This layer moves into the vanilla
 * arm part's own frame ({@code leftArm.translateAndRotate}) so the gauntlet inherits every arm pose for free — the
 * ready raise, walking, the combi stick stab, riding.
 * <h2>⚠⚠ HOW THE AUTHORED PLACEMENT CARRIES OVER WITHOUT TOUCHING THE GEO</h2> In first person BLib draws the skin arm
 * at a known matrix inside the rig ({@code AzItemArmRenderUtil.rigArmMatrix}: the rig's animated {@code leftArm} bone,
 * then the 0.67/1.33 size fudge, the centring shift, the 180-degree turn, the part pivot). The gauntlet is authored
 * relative to that arm. So from the real arm's frame, applying the INVERSE of that matrix puts the gauntlet exactly
 * where it sits on the first-person arm — with {@code fire}'s tilt and {@code open}'s swing coming through relative to
 * the arm, because the bone is read LIVE from the stack's animator. The rig arm's own rotation cancels out (the real
 * arm supplies it), which is why the {@code ready} clip's root offset stops mattering here.
 * <p>
 * The inverse is taken from BLib's helper, not re-derived, so the two views cannot drift apart.
 * <h2>⚠ The in-hand render is cancelled while equipped</h2> {@code MixinItemInHandLayer_GauntletWorn}. Without it the
 * gauntlet draws twice — once here on the arm and once floating off the hand.
 * <h2>⚠ First frame for another player</h2> Nothing else renders a remote player's offhand gauntlet through BLib once
 * the in-hand render is cancelled, so its animator does not exist until this layer draws it. On that first frame the
 * rig bone cannot be read, so the render goes through at zero scale purely to create the animator; from the next frame
 * on it is placed normally.
 */
public class GauntletArmLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public GauntletArmLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource buffers,
        int packedLight,
        @NotNull AbstractClientPlayer player,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        // ⚠ First line is one item check. This runs for every player every frame.
        var stack = GauntletItem.equipped(player);

        if (stack.isEmpty()) {
            return;
        }

        var arm = GauntletItem.arm(player);
        var left = arm == HumanoidArm.LEFT;
        var model = getParentModel();
        var part = left ? model.leftArm : model.rightArm;
        var rigBone = rigArmBone(stack, left);

        poseStack.pushPose();
        part.translateAndRotate(poseStack);

        if (rigBone == null) {
            // ⚠ No animator yet (remote player, first frame). Render invisibly so BLib creates one — see class doc.
            poseStack.scale(0.0F, 0.0F, 0.0F);
        } else {
            poseStack.mulPose(AzItemArmRenderUtil.rigArmMatrix(rigBone, left).invert());
        }

        // ⚠ ItemRenderer.render translates -0.5 on every axis before a builtin renderer, and BLib's preRender adds
        // back +0.5 on X and Z (useNewOffset). The net -0.5 on Y is cancelled here so the geo's origin lands at the
        // rig's origin, the same relationship the first-person render has.
        poseStack.translate(0.0F, 0.5F, 0.0F);

        Minecraft.getInstance()
            .getItemRenderer()
            .renderStatic(
                player,
                stack,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                buffers,
                player.level(),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                player.getId()
            );

        poseStack.popPose();
    }

    /**
     * {@return the rig's {@code leftArm} / {@code rightArm} bone with this frame's animated values, or null if the
     * stack has no animator yet} Read from the stack's own cached animator (keyed by its {@code AZ_ID}), so every
     * client sees the same one the animation sync dispatches to.
     */
    private static com.blib.api.client.model.v1.AzBone rigArmBone(ItemStack stack, boolean left) {
        var animator = AzAnimatorAccessor.<UUID, ItemStack>getOrNull(stack);

        if (animator == null) {
            return null;
        }

        var baked = animator.context().boneCache().getBakedModel();

        if (baked == null) {
            return null;
        }

        return baked.getBoneOrNull(left ? "leftArm" : "rightArm");
    }
}
