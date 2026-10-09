package com.predator.client.render.layer;

import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.item.pipeline.AzItemRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzRenderLayer;
import com.predator.PredatorResources;
import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The charged-creeper lightning over an ARMED gauntlet, growing as the countdown runs.
 * <p>
 * Vanilla's {@code energySwirl} render type over his 64x32 lightning sheet, scrolling with time, re-rendering the
 * gauntlet geo through the pipeline's own {@code reRender} (the glow layer's pattern). Because it lives on the item
 * pipeline it appears everywhere the stack is drawn: worn, held, dropped, and on the placed block.
 * <p>
 * [stated] "the gauntlet itself would have the lightning creeper effect and it would grow overtime as it counts down.
 * To about 3x its size not the gauntlet but its lightning effect." Scale 1x from arming, 3x at zero, about the
 * gauntlet's centre so the gauntlet itself does not move.
 * <h2>⚠ Restores the context's render type</h2> Same rule as the net overlay: BLib draws held items from the context's
 * render type and the context outlives the frame.
 * <h2>⚠⚠ THE RE-RENDER RUNS WITH THE TRANSFORM TYPE SET TO NONE</h2> In a first-person pass BLib's item model renderer
 * draws the SKIN ARM for the arm bones — by bone name, in every pass including a layer's re-render. The arm asks the
 * immediate buffer source for its own render type, which ENDS the swirl batch, and the gauntlet bones that follow write
 * into a closed builder: crash-2026-09-10 {@code IllegalStateException: Not building!}. (A 3x-scaled duplicate arm
 * would have been wrong even if it had not crashed.) NONE is not a hand context, so the arm rule cannot fire; the
 * gauntlet bones still draw because they are ordinary bones. Put back afterwards — the context outlives the frame.
 */
public class GauntletDestructLightningLayer implements AzRenderLayer<UUID, ItemStack> {

    private static final ResourceLocation TEXTURE = PredatorResources.location("textures/entity/gauntlet_destruct_lightning.png");

    public static float MAX_SCALE = 3.0F;

    /** The bone the gauntlet hangs from; its pivot (plus its animated position) is the point the aura grows about. */
    private static final String ROOT_BONE = "root";

    @Override
    public void preRender(AzRendererPipelineContext<UUID, ItemStack> context) {}

    @Override
    public void render(AzRendererPipelineContext<UUID, ItemStack> context) {
        var stack = context.animatable();

        if (!GauntletSelfDestruct.isArmed(stack)) {
            return;
        }

        var level = Minecraft.getInstance().level;
        var now = level == null ? 0L : level.getGameTime();
        var progress = 0.0F;

        if (GauntletSelfDestruct.isCounting(stack)) {
            progress = 1.0F - GauntletSelfDestruct.remainingTicks(stack, now) / (float) GauntletSelfDestruct.COUNTDOWN_TICKS;
        }

        var scale = Mth.lerp(progress, 1.0F, MAX_SCALE);
        var ticks = now + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        var type = RenderType.energySwirl(TEXTURE, ticks * 0.01F % 1.0F, ticks * 0.01F % 1.0F);
        var previousType = context.renderType();
        var previousConsumer = context.vertexConsumer();
        var poseStack = context.poseStack();
        var itemContext = (AzItemRendererPipelineContext) context;
        var previousTransform = itemContext.getTransformType();

        // ⚠⚠ SCALE ABOUT THE GAUNTLET, NOT ABOUT THE ITEM'S UNIT CUBE. When a layer runs, BLib's (0.5, 0, 0.5) offset
        // is
        // already on the stack, so the gauntlet sits near this frame's ORIGIN; scaling about (0.5, 0.5, 0.5) here
        // slid the copy half a block per unit of scale ("shifts to the lower right until it is on a different
        // block"). The root bone's pivot + animated position is the gauntlet's centre in every context, worn included.
        var centre = rootCentre(context);

        poseStack.pushPose();
        poseStack.translate(centre[0], centre[1], centre[2]);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-centre[0], -centre[1], -centre[2]);

        // ⚠⚠ NONE ONLY FOR THE HANDS. NONE is an ANIMATED context; GUI, GROUND and FIXED are frozen at rest. Running
        // the
        // re-render as NONE everywhere put the swirl at the ready pose (root shoved 5.8 units, arm rotated) over an
        // icon or a placed block that sits at rest — the lightning drew beside the gauntlet, not on it. Hand contexts
        // switch to NONE (animated, but the skin-arm rule cannot fire); every other context keeps its own transform.
        itemContext.setTransformType(isHand(previousTransform) ? ItemDisplayContext.NONE : previousTransform);
        context.setRenderType(type);
        context.setVertexConsumer(context.multiBufferSource().getBuffer(type));
        context.rendererPipeline().reRender(context);

        itemContext.setTransformType(previousTransform);
        context.setRenderType(previousType);
        context.setVertexConsumer(previousConsumer);
        poseStack.popPose();
    }

    /**
     * {@return the root bone's centre in the layer's frame, blocks} Same sign rules as BLib's RenderUtil bone
     * translate.
     */
    private static float[] rootCentre(AzRendererPipelineContext<UUID, ItemStack> context) {
        var model = context.bakedModel();
        var root = model == null ? null : model.getBoneOrNull(ROOT_BONE);

        if (root == null) {
            return new float[] { 0.0F, 0.5F, 0.0F };
        }

        return new float[] {
            (root.getPivotX() - root.getPosX()) / 16.0F,
            (root.getPivotY() + root.getPosY()) / 16.0F,
            (root.getPivotZ() + root.getPosZ()) / 16.0F
        };
    }

    private static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, ItemStack> context, com.blib.api.client.model.v1.AzBone bone) {}
}
