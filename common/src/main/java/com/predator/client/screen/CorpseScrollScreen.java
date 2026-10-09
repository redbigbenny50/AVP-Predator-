package com.predator.client.screen;

import com.predator.common.gameplay.menu.CorpseScrollMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The screen for a skinned corpse bigger than a chest: vanilla's six-row chest look, with a scrollbar on the right in
 * the style of the creative inventory. The mouse wheel, clicking the track, or dragging the thumb scrolls it — each a
 * menu button sent to the server, which moves the window and re-sends the slots (see {@link CorpseScrollMenu}).
 */
public class CorpseScrollScreen extends AbstractContainerScreen<CorpseScrollMenu> {

    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");

    private static final ResourceLocation SCROLLER_DISABLED = ResourceLocation.withDefaultNamespace(
        "container/creative_inventory/scroller_disabled"
    );

    /** The track, just right of the chest frame. */
    private static final int TRACK_X = 176;

    private static final int TRACK_Y = 18;

    private static final int TRACK_WIDTH = 14;

    private static final int TRACK_HEIGHT = CorpseScrollMenu.VISIBLE_ROWS * 18;

    private static final int THUMB_WIDTH = 12;

    private static final int THUMB_HEIGHT = 15;

    private boolean dragging;

    public CorpseScrollScreen(CorpseScrollMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + CorpseScrollMenu.VISIBLE_ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        var x = leftPos;
        var y = topPos;
        var rows = CorpseScrollMenu.VISIBLE_ROWS;

        // Exactly as vanilla draws a six-row chest: the top with the rows, then the player's inventory.
        graphics.blit(CHEST, x, y, 0, 0, imageWidth, rows * 18 + 17);
        graphics.blit(CHEST, x, y + rows * 18 + 17, 0, 126, imageWidth, 96);

        // The scrollbar track and thumb.
        var trackX = x + TRACK_X;
        var trackY = y + TRACK_Y;

        graphics.fill(trackX - 1, trackY - 1, trackX + TRACK_WIDTH + 1, trackY + TRACK_HEIGHT + 1, 0xFF373737);
        graphics.fill(trackX, trackY, trackX + TRACK_WIDTH, trackY + TRACK_HEIGHT, 0xFF8B8B8B);

        var max = menu.maxScrollRow();
        var thumbY = max == 0 ? trackY : trackY + Math.round((TRACK_HEIGHT - THUMB_HEIGHT) * (float) menu.scrollRow() / max);

        graphics.blitSprite(max == 0 ? SCROLLER_DISABLED : SCROLLER, trackX + 1, thumbY, THUMB_WIDTH, THUMB_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.maxScrollRow() > 0 && scrollY != 0.0) {
            press(scrollY > 0.0 ? CorpseScrollMenu.BUTTON_UP : CorpseScrollMenu.BUTTON_DOWN);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && onTrack(mouseX, mouseY) && menu.maxScrollRow() > 0) {
            dragging = true;
            jumpTo(mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            jumpTo(mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // The track sits outside the chest frame; a click on it is not a click outside the screen.
        return !onTrack(mouseX, mouseY) && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private boolean onTrack(double mouseX, double mouseY) {
        var trackX = leftPos + TRACK_X;
        var trackY = topPos + TRACK_Y;

        return mouseX >= trackX && mouseX < trackX + TRACK_WIDTH && mouseY >= trackY && mouseY < trackY + TRACK_HEIGHT;
    }

    private void jumpTo(double mouseY) {
        var max = menu.maxScrollRow();
        var fraction = Mth.clamp((mouseY - topPos - TRACK_Y - THUMB_HEIGHT / 2.0) / (TRACK_HEIGHT - THUMB_HEIGHT), 0.0, 1.0);
        var row = (int) Math.round(fraction * max);

        if (row != menu.scrollRow()) {
            press(CorpseScrollMenu.JUMP_BASE + row);
        }
    }

    private void press(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }
}
