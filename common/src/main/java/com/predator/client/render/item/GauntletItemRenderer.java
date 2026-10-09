package com.predator.client.render.item;

import com.blib.api.client.render.v1.item.AzItemRenderer;
import com.blib.api.client.render.v1.item.AzItemRendererConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.PredatorResources;
import com.predator.client.animation.item.GauntletAnimator;
import com.predator.client.render.layer.GauntletDestructLightningLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the wrist gauntlet.
 * <p>
 * <strong>⚠⚠ ONE MODEL, ALWAYS: gauntlet_player.geo.</strong> I previously swapped to gauntlet_item.geo outside first
 * person, and that could never have worked. {@code AzProvider.provideBakedModel} returns {@code cache.getBakedModel()}
 * — the per-instance deep copy — as soon as an animator and bone cache exist, and IGNORES the model location provider
 * from then on. The hotbar and GUI draw before you ever look at your hand, so the item model was cached first and the
 * first-person switch never took effect. That is why the arm never appeared.
 * <p>
 * <strong>⚠ The arm bones are hidden instead of switching models.</strong> {@link GauntletAnimator} scales them, from
 * {@code setCustomAnimations}, which runs BEFORE the draw each frame — so there is no one-frame flash of a stray limb
 * in the GUI.
 * <p>
 * <strong>⚠ useNewOffset(true)</strong> — every working item renderer here sets it; it shifts placement by 0.51 blocks.
 */
public class GauntletItemRenderer extends AzItemRenderer {

    public static final String NAME = "gauntlet";

    private static final ResourceLocation MODEL = PredatorResources.itemGeoModelLocation("gauntlet_player");

    private static final ResourceLocation TEXTURE = PredatorResources.itemTextureLocation(NAME);

    /**
     * ⚠ Which display context is being drawn, recorded for {@link GauntletAnimator} — the animator has no access to it,
     * and the arm bones must only exist in first person.
     */
    private static final ThreadLocal<Boolean> FIRST_PERSON = ThreadLocal.withInitial(() -> false);

    /** {@return whether the pass currently being drawn is a first-person hand} */
    public static boolean isFirstPersonPass() {
        return FIRST_PERSON.get();
    }

    public GauntletItemRenderer() {
        super(
            AzItemRendererConfig.builder(MODEL, TEXTURE)
                .useNewOffset(true)
                .addRenderLayer(new GauntletDestructLightningLayer())
                .setAnimatorProvider(GauntletAnimator::new)
                // ⚠⚠ NO ANIMATION IN GUI / FIXED / GROUND / HEAD. The equipped stack is the SAME object the hotbar
                // draws, and its animator is on the ready loop, whose root offset slides the gauntlet 5.8 units to
                // sit on the arm. Drawn animated in the offhand slot, that offset is what shoved the icon into the
                // corner. Frozen contexts draw the bones at their rest pose instead (the arm bones stay hidden).
                // Hand contexts and NONE keep animating: NONE is what GauntletArmLayer renders through in third
                // person, and it needs the live fire / open / close motion.
                .setShouldAnimateInContext(
                    context -> context != ItemDisplayContext.GUI
                        && context != ItemDisplayContext.FIXED
                        && context != ItemDisplayContext.GROUND
                        && context != ItemDisplayContext.HEAD
                )
                .build()
        );
    }

    @Override
    public void renderByItem(
        ItemStack stack,
        ItemDisplayContext transformType,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource source,
        int packedLight
    ) {
        // ⚠ Set BEFORE super, because the animator's setCustomAnimations runs inside it and reads this to decide
        // whether the arm bones should exist. Cleared in a finally so a throw cannot leak first-person into the
        // next item drawn.
        var firstPerson = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        FIRST_PERSON.set(firstPerson);

        try {
            // ⚠⚠ NO OFFSET CANCELLING. His rule: EQUIPPED means WORN, in every view — first person shows the
            // rigged arm wearing it, third person shows it on the player's forearm. NOT equipped means held as an
            // ordinary item.
            //
            // That split is already handled upstream and needs nothing here: GauntletItem.inventoryTick only sends
            // the ready clip while the gauntlet is in the OFFHAND, so an unequipped one plays no clip at all and
            // renders purely from its display transforms — a plain held item. An equipped one gets the clip, and
            // the clip's root offset is what puts it on the arm.
            //
            // ⚠ I previously cancelled that offset outside first person, which forced an equipped gauntlet back
            // into the hand — the opposite of worn.
            super.renderByItem(stack, transformType, poseStack, source, packedLight);
        } finally {
            FIRST_PERSON.set(false);
        }
    }
}
