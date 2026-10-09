package com.predator.client.screen;

import com.predator.Predator;
import com.predator.common.network.packet.C2SOpenGauntletPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * The tab hanging off the left of the inventory screen that opens the gauntlet.
 * <h2>⚠ Draws itself rather than using a texture</h2> A tab is four rectangles and an item icon. Adding another sheet
 * for it would mean a second file to keep in step with the GUI's palette every time that changes.
 */
public class GauntletTabButton extends Button {

    public static final int WIDTH = 26;

    public static final int HEIGHT = 26;

    private static final int BODY = 0xFF474749;

    private static final int EDGE_LIGHT = 0xFF77777C;

    private static final int EDGE_DARK = 0xFF2C2C2E;

    public GauntletTabButton(int x, int y) {
        super(
            x,
            y,
            WIDTH,
            HEIGHT,
            Component.empty(),
            button -> Predator.MOD.networking().sendToServer(C2SOpenGauntletPayload.INSTANCE),
            DEFAULT_NARRATION
        );

        setTooltip(Tooltip.create(Component.translatable("container.avp_predator.gauntlet")));
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var hovered = isHovered();

        graphics.fill(getX(), getY(), getX() + width, getY() + height, BODY);
        graphics.fill(getX(), getY(), getX() + width - 1, getY() + 1, hovered ? 0xFFA0A0A6 : EDGE_LIGHT);
        graphics.fill(getX(), getY(), getX() + 1, getY() + height - 1, hovered ? 0xFFA0A0A6 : EDGE_LIGHT);
        graphics.fill(getX() + width - 1, getY() + 1, getX() + width, getY() + height, EDGE_DARK);
        graphics.fill(getX() + 1, getY() + height - 1, getX() + width, getY() + height, EDGE_DARK);

        // ⚠ The gauntlet's own icon, so the tab is self-explanatory without a label. Falls back to a plain item if
        // the registry lookup somehow misses, rather than drawing nothing and looking broken.
        var icon = new ItemStack(
            com.predator.common.registry.init.item.PredatorItems.GAUNTLET.get() == null
                ? Items.IRON_INGOT
                : com.predator.common.registry.init.item.PredatorItems.GAUNTLET.get()
        );

        graphics.renderItem(icon, getX() + 5, getY() + 5);
    }
}
