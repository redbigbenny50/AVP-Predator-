package com.predator.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.predator.PredatorResources;
import com.predator.common.gameplay.effect.PredatorMud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The muddy player's own view — a caked vignette around the screen edge, the same shape as vanilla's powder-snow
 * outline but running the other way: thick when freshly applied, thinning as the mud dries and flakes off, gone when
 * the effect ends. That ramp is the wearer's only warning that their thermal cover is running out.
 * <p>
 * Alpha is measured against the longest duration this overlay has seen for the current application rather than a fixed
 * constant, so a short smear and a full bucket both start opaque and both fade to nothing. The high-water mark resets
 * whenever the effect lapses or is topped up.
 */
public class MudOverlayRenderer {

    private static final ResourceLocation TEXTURE = PredatorResources.location("textures/gui/mud_outline.png");

    private static final int TEXTURE_SIZE = 256;

    /** Mud never renders fainter than this while it is still active — it should always read as present. */
    private static final float MINIMUM_ALPHA = 0.15F;

    private static int referenceDuration = PredatorMud.REFERENCE_DURATION_TICKS;

    public static void render(GuiGraphics guiGraphics) {
        var player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        var remaining = PredatorMud.remainingTicks(player);

        if (remaining <= 0) {
            referenceDuration = PredatorMud.REFERENCE_DURATION_TICKS;
            return;
        }

        // Track the high-water mark so a top-up re-thickens the overlay instead of leaving it stuck part-faded.
        if (remaining > referenceDuration) {
            referenceDuration = remaining;
        }

        var alpha = Math.max(MINIMUM_ALPHA, PredatorMud.thickness(player, referenceDuration));

        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        guiGraphics.blit(
            TEXTURE,
            0,
            0,
            guiGraphics.guiWidth(),
            guiGraphics.guiHeight(),
            0.0F,
            0.0F,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
            TEXTURE_SIZE
        );
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private MudOverlayRenderer() {}
}
