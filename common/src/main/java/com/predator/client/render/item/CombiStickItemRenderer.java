package com.predator.client.render.item;

import com.blib.api.client.render.v1.item.AzItemRenderer;
import com.blib.api.client.render.v1.item.AzItemRendererConfig;
import com.predator.PredatorResources;
import com.predator.client.animation.item.CombiStickAnimator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renders the combi stick.
 * <p>
 * Same shape as {@code TripMineItemRenderer}, the renderer in this mod that is known to work — model, texture,
 * {@code useNewOffset(true)} — plus the animator for the open/loop/close clips.
 * <p>
 * <strong>⚠ useNewOffset(true) was also missing.</strong> Checked rather than assumed: it changes exactly one thing,
 * {@code poseStack.translate(0.5f, useNewOffset ? 0.0f : 0.51f, 0.5f)} — a 0.51 block vertical shift, which is why it
 * sat too high in hand.
 * <p>
 * <strong>⚠ The animation dispatch was removed from renderByItem.</strong> It was calling into the pipeline's context
 * during rendering to find the holder, which is fragile and was not the thing making the stick visible. The clips are
 * driven by the item's own state; if the deploy animation needs a nudge it belongs in a tick handler, not in the middle
 * of a draw call.
 */
public class CombiStickItemRenderer extends AzItemRenderer {

    public static final String NAME = "combi_stick";

    private static final ResourceLocation MODEL = PredatorResources.itemGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = PredatorResources.itemTextureLocation(NAME);

    /**
     * ⚠⚠ NO setScale HERE, DELIBERATELY. The 75% lives in his exported display transforms in
     * {@code models/item/combi_stick.json}. Setting it here as well would compound to 0.5625.
     * <p>
     * ⚠ The THROWN projectile is a different renderer with no display transforms at all, so it carries its own 0.75 —
     * the two are not interchangeable, and that is why one looked right while the other did not.
     */

    public CombiStickItemRenderer() {
        super(
            AzItemRendererConfig.builder(MODEL, TEXTURE)
                .useNewOffset(true)
                .setAnimatorProvider(CombiStickAnimator::new)
                // ⭐⭐ NO ANIMATION OUTSIDE THE PLAYER'S HANDS — THE SAME STANDARD EVERY avp_human GUN USES
                // (MuzzledGunItemRenderer.animatesInContext). Animation state lives PER STACK, so once extend /
                // throw / stab is playing, every render of that stack shows the pose — including the held spear's
                // own hotbar icon. Gating when clips START cannot fix that; only freezing the bone transforms for
                // the non-hand passes can, and BLib already does that when this predicate returns false — the
                // builder default is just `$ -> true`.
                .setShouldAnimateInContext(CombiStickItemRenderer::animatesInContext)
                .build()
        );
    }

    /** {@return whether this display context is one of the four in-hand renders} */
    private static boolean animatesInContext(ItemDisplayContext context) {
        return switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND ->
                true;
            default -> false;
        };
    }
}
