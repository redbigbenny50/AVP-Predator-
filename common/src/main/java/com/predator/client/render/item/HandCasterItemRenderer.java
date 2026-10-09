package com.predator.client.render.item;

import com.blib.api.client.render.v1.item.AzItemRenderer;
import com.blib.api.client.render.v1.item.AzItemRendererConfig;
import com.predator.PredatorResources;
import com.predator.client.animation.item.HandCasterAnimator;
import net.minecraft.resources.ResourceLocation;

/**
 * The hand caster model (geo/item/hand_caster.geo.json) with its idle and fire clips. Placement in the hand comes from
 * his models/item/hand_caster.json display settings — [stated] "look at the display settings file as 3rd person and
 * first should be placed correctly".
 * <p>
 * ⚠⚠ useNewOffset(FALSE) — BLib's default. [stated] "plasmacaster not using block benches first person view you cant
 * see it in first person. in thirdperson it sits behind your arm", and before that it sat half a slot low in the GUI.
 * One cause for all three: useNewOffset(true) removes a 0.51-block upward shift (AzItemRendererPipeline: translate(0.5,
 * useNewOffset ? 0.0 : 0.51, 0.5)). Blockbench's Azure-model preview assumes that shift, so with it removed EVERY view
 * drew ~8 px lower than designed. With it back, his display file places it exactly as Blockbench shows — first person,
 * third person, GUI, ground and frame alike.
 */
public class HandCasterItemRenderer extends AzItemRenderer {

    public static final String NAME = "hand_caster";

    private static final ResourceLocation MODEL = PredatorResources.itemGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = PredatorResources.itemTextureLocation(NAME);

    public HandCasterItemRenderer() {
        super(
            AzItemRendererConfig.builder(MODEL, TEXTURE)
                .useNewOffset(false)
                .setAnimatorProvider(HandCasterAnimator::new)
                .setModelRenderer(
                    (pipeline, layer) -> new BarrelTrackingModelRenderer(
                        (com.blib.api.client.render.v1.item.pipeline.AzItemRendererPipeline) pipeline,
                        layer
                    )
                )
                .build()
        );
    }

    /**
     * The tip of the barrel: the top of gBarrel's main cube, in model space. Geo (-0.007, 7.899, -0.392) px — BLib
     * bakes a geo's X negated and works in blocks, so (+0.007, 7.899, -0.392) / 16.
     */
    private static final org.joml.Vector3f BARREL_TIP = new org.joml.Vector3f(0.007F / 16.0F, 7.899F / 16.0F, -0.392F / 16.0F);

    /**
     * Finds where the barrel's tip ACTUALLY is on screen, every frame, for the local player's own caster.
     * <p>
     * [stated] "the particles should be where the end of the barrel is the tip of the caster barrel group."
     * <p>
     * ⚠ BLib tracks an item bone only RELATIVE to where the item began drawing (localSpace = inverse(itemStart) x pose
     * — RenderUtil.invertAndMultiplyMatrices), so the bone's full pose is itemStart x localSpace. Where that lands
     * depends on the view: in FIRST person the hand is drawn in view space with no camera rotation, so the point is
     * turned into the world by the camera's rotation (camera.rotation() — checked: it maps view-forward exactly onto
     * the look, pitch included); in THIRD person the level is drawn camera-relative with the rotation elsewhere, so it
     * is simply offset by the camera position. Either way the result follows the display settings, the animation,
     * bobbing — everything.
     */
    private static final class BarrelTrackingModelRenderer extends com.blib.api.client.render.v1.item.model.AzItemModelRenderer {

        BarrelTrackingModelRenderer(
            com.blib.api.client.render.v1.item.pipeline.AzItemRendererPipeline pipeline,
            com.blib.api.client.render.v1.AzLayerRenderer<java.util.UUID, net.minecraft.world.item.ItemStack> layer
        ) {
            super(pipeline, layer);
        }

        @Override
        public void renderRecursively(
            com.blib.api.client.render.v1.AzRendererPipelineContext<java.util.UUID, net.minecraft.world.item.ItemStack> context,
            com.blib.api.client.model.v1.AzBone bone,
            boolean isReRender
        ) {
            var barrel = "gBarrel".equals(bone.getName());

            if (barrel && !bone.isTrackingMatrices()) {
                bone.setTrackingMatrices(true);
            }

            super.renderRecursively(context, bone, isReRender);

            if (!barrel || isReRender) {
                return;
            }

            var minecraft = net.minecraft.client.Minecraft.getInstance();

            // ⚠⚠ DO NOT REINSTATE A currentEntity() CHECK HERE. It was
            // context.currentEntity() != minecraft.player
            // and it rejected EVERY frame, because BLib never populates currentEntity on the ITEM pipeline - the only
            // setCurrentEntity call in the whole library is in the ENTITY pipeline, and it sets it to null. So the tip
            // was never reported, which broke two things at once and made them look like separate bugs:
            // - the charge sparks never drew (freshTip was always null, in first AND third person), and
            // - every shot fell back to muzzlePosition(), a fixed waist-height offset - the bolt "from the stomach".
            // The transform check below is what actually identifies the holder's own view: FIRST_PERSON_* and
            // THIRD_PERSON_* only ever occur while drawing the item in a player's hand, and the local player is the
            // only one whose hand is drawn in first person.
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }

            var type = ((com.blib.api.client.render.v1.item.pipeline.AzItemRendererPipelineContext) context).getTransformType();
            var firstPerson = type == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || type == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            var thirdPerson = type == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || type == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

            if (!firstPerson && !thirdPerson) {
                return;
            }

            var pose = new org.joml.Matrix4f(itemRendererPipeline.getItemRenderTranslations()).mul(bone.getLocalSpaceMatrix());
            var tip = pose.transformPosition(new org.joml.Vector3f(BARREL_TIP.x, BARREL_TIP.y, BARREL_TIP.z));
            var camera = minecraft.gameRenderer.getMainCamera();

            if (firstPerson) {
                camera.rotation().transform(tip);
            }

            com.predator.client.handcaster.HandCasterClient.setBarrelTip(camera.getPosition().add(tip.x(), tip.y(), tip.z()));
        }
    }
}
