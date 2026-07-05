package com.predator.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.predator.PredatorResources;
import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class MudOverlayRenderer {

    private static final ResourceLocation TEXTURE = PredatorResources.location("textures/gui/mud_outline.png");

    private static final int TEXTURE_SIZE = 256;

    public static void render(GuiGraphics guiGraphics) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.hasEffect(PredatorMobEffects.getMudHolder())) {
            return;
        }

        RenderSystem.enableBlend();
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
        RenderSystem.disableBlend();
    }

    private MudOverlayRenderer() {}
}
