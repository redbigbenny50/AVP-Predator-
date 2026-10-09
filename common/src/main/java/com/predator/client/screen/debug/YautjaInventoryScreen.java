package com.predator.client.screen.debug;

import com.predator.common.gameplay.debug.yautja.YautjaInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The yautja's screen: its armour, a live preview of it, its main hand, its rack, and your own inventory.
 * <p>
 * [stated] "make it look similar to this example. where the mainhand slot is clearly visible and the armor it has on is
 * shown as well so if you wanted to you can remove the armor too." Laid out as his mockup. The background
 * (textures/gui/container/yautja_inventory.png) was generated in vanilla's own GUI palette FROM the slot positions in
 * YautjaInventoryMenu, so the two must move together.
 */
public class YautjaInventoryScreen extends AbstractContainerScreen<YautjaInventoryMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        "avp_predator",
        "textures/gui/container/yautja_inventory.png"
    );

    /** The black preview window, in texture pixels: left, top, right, bottom. */
    private static final int PREVIEW_LEFT = 64;

    private static final int PREVIEW_TOP = 17;

    private static final int PREVIEW_RIGHT = 114;

    private static final int PREVIEW_BOTTOM = 90;

    /** How much of the window's height the yautja fills — scaled from its real height, so any size fits. */
    private static final float PREVIEW_FILL = 0.82F;

    private static final int LABEL_COLOUR = 0x404040;

    private static final int MAIN_HAND_CENTRE_X = 141;

    private static final int MAIN_HAND_LABEL_Y = 66;

    private static final int RACK_LABEL_Y = 96;

    private static final int HOTBAR_LABEL_Y = 215;

    public YautjaInventoryScreen(YautjaInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 250;
        inventoryLabelY = 147;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        var yautja = menu.yautja();

        if (yautja != null) {
            var scale = Math.max(1, Math.round((PREVIEW_BOTTOM - PREVIEW_TOP) * PREVIEW_FILL / Math.max(0.5F, yautja.getBbHeight())));

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                leftPos + PREVIEW_LEFT,
                topPos + PREVIEW_TOP,
                leftPos + PREVIEW_RIGHT,
                topPos + PREVIEW_BOTTOM,
                scale,
                0.0625F,
                mouseX,
                mouseY,
                yautja
            );
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        var mainHand = Component.translatable("screen.avp_predator.yautja_debug.main_hand");

        graphics.drawString(font, mainHand, MAIN_HAND_CENTRE_X - font.width(mainHand) / 2, MAIN_HAND_LABEL_Y, LABEL_COLOUR, false);
        graphics.drawString(
            font,
            Component.translatable("screen.avp_predator.yautja_debug.yautja_inventory"),
            8,
            RACK_LABEL_Y,
            LABEL_COLOUR,
            false
        );
        graphics.drawString(
            font,
            Component.translatable("screen.avp_predator.yautja_debug.hotbar"),
            8,
            HOTBAR_LABEL_Y,
            LABEL_COLOUR,
            false
        );
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
