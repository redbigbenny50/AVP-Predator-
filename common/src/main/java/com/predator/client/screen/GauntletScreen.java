package com.predator.client.screen;

import com.predator.Predator;
import com.predator.PredatorResources;
import com.predator.client.input.keybind.PredatorKeybindingRegistry;
import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import com.predator.common.gameplay.menu.GauntletMenu;
import com.predator.common.network.packet.C2SArmGauntletPayload;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The wrist gauntlet screen.
 * <h2>⚠ The texture is a 256x256 sheet with a 176x166 panel at its top-left</h2> That is the vanilla container
 * convention, which is why {@link GauntletMenu} could use vanilla inventory coordinates unchanged — the artwork was
 * built to that grid rather than the grid being bent to the artwork.
 */
public class GauntletScreen extends AbstractContainerScreen<GauntletMenu> {

    private static final ResourceLocation TEXTURE = PredatorResources.location("textures/gui/wrist_gui.png");

    /** The four self-destruct panels, measured off his GUI art: 16x22 each, y 40, x 48 / 68 / 88 / 108. */
    private static final int[] PANEL_X = { 48, 68, 88, 108 };

    private static final int PANEL_Y = 40;

    private static final int ARMED_MESSAGE_WIDTH = 100;

    /** Pixels between the message column and the window's left edge. */
    private static final int ARMED_MESSAGE_GAP = 8;

    private static final int ARMED_MESSAGE_Y = 40;

    /** Panel state while the screen is open: -1 blank, 9 set. Rebuilt from the stack when it opens. */
    private final int[] panels = new int[GauntletSelfDestruct.PANELS];

    /** See {@link #keyPressed} — set when G closed the screen, consumed by the very next PRESS the handler sees. */
    private static boolean suppressNextOpen;

    /** {@return whether the open handler should swallow this PRESS, clearing the flag either way} */
    public static boolean consumeSuppressedOpen() {
        var suppressed = suppressNextOpen;

        suppressNextOpen = false;

        return suppressed;
    }

    /** Called on RELEASE so a suppression that was never consumed cannot eat a later, real press. */
    public static void clearSuppressedOpen() {
        suppressNextOpen = false;
    }

    public GauntletScreen(GauntletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        imageWidth = 176;
        imageHeight = 166;

        // ⚠ The inventory label sits at 72, not vanilla's 93: everything above the seam is the bracer's screen and
        // the player's inventory starts higher than a chest's does.
        inventoryLabelY = imageHeight - 94;

        var armed = GauntletSelfDestruct.isArmed(menu.getGauntlet());

        java.util.Arrays.fill(panels, armed ? 9 : -1);
    }

    /**
     * ⚠⚠ THE ARMING PANELS. [stated] "the player clicks on those 4 panel areas one at a time and it starts at 9 each
     * press so you do 9 9 9 9 and it arms the destruct". A click on a blank panel sets it to 9; when all four read 9
     * the server is asked to arm and the message tells you to place it. A click on any panel while armed blanks it and
     * asks the server to cancel. The countdown itself never runs while worn.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (var panel = 0; panel < panels.length; panel++) {
                if (isOverPanel(panel, mouseX, mouseY)) {
                    clickPanel(panel);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverPanel(int panel, double mouseX, double mouseY) {
        var x = leftPos + PANEL_X[panel];
        var y = topPos + PANEL_Y;

        return mouseX >= x && mouseX < x + GauntletDigits.WIDTH && mouseY >= y && mouseY < y + GauntletDigits.HEIGHT;
    }

    private void clickPanel(int panel) {
        var gauntlet = menu.getGauntlet();

        if (GauntletSelfDestruct.isCounting(gauntlet)) {
            return;
        }

        var wasArmed = GauntletSelfDestruct.isArmed(gauntlet);

        if (wasArmed) {
            java.util.Arrays.fill(panels, -1);
            Predator.MOD.networking().sendToServer(new C2SArmGauntletPayload(C2SArmGauntletPayload.CANCEL));
            play(PredatorSoundEvents.GAUNTLET_DESTRUCT_ARMED_CANCEL.get(), 1.0F);
            return;
        }

        var firstClick = java.util.Arrays.stream(panels).allMatch(digit -> digit < 0);

        panels[panel] = 9;
        play(PredatorSoundEvents.GAUNTLET_DESTRUCT_DISARM_BUTTON.get(), 1.0F + panel * 0.08F);

        if (java.util.Arrays.stream(panels).allMatch(digit -> digit == 9)) {
            Predator.MOD.networking().sendToServer(new C2SArmGauntletPayload(C2SArmGauntletPayload.ARM));
            play(PredatorSoundEvents.GAUNTLET_DESTRUCT_ARMED.get(), 1.0F);
        } else if (firstClick) {
            // The client cannot read the prednuke rule; the server answers a probe with the "disabled" message.
            Predator.MOD.networking().sendToServer(new C2SArmGauntletPayload(C2SArmGauntletPayload.PROBE));
        }
    }

    private void play(net.minecraft.sounds.SoundEvent sound, float pitch) {
        var player = minecraft == null ? null : minecraft.player;

        if (player != null) {
            player.playSound(sound, 1.0F, pitch);
        }
    }

    /** ⚠ The wrist door opens WITH the screen — his ruling, "open close plays on the gui, thats correct." */
    // ⚠ open/close are no longer dispatched from the screen. The SERVER sends them (PredatorServerListener on open,
    // GauntletMenu.removed on close) so every player around sees the wrist door, not just the one using the GUI.
    // A client-side dispatch here as well would replay over the synced one and restart the clip.

    /** ⚠ And closes with it, including on escape rather than only on clicking away. */
    @Override
    public void onClose() {
        super.onClose();
    }

    /**
     * ⚠ G closes the screen as well as opening it. While a screen is up, key presses go to the screen, not to the
     * keybind tick — so the toggle has to live here. Checked before super so the container's own inventory-key handling
     * (which also closes) does not race it.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (PredatorKeybindingRegistry.OPEN_GAUNTLET.get().v1().matches(keyCode, scanCode)) {
            // ⚠⚠ ARM THE SUPPRESSION BEFORE CLOSING. Vanilla's KeyboardHandler calls this method FIRST and only
            // afterwards checks "is a screen open?" to decide whether the press reaches the KeyMapping — so once
            // onClose() nulls the screen, this same press marks G as down, BLib's tick poll reports a PRESS next
            // tick, and the handler would reopen the GUI: [stated] "it opens and closes at the same time".
            suppressNextOpen = true;
            onClose();

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        for (var panel = 0; panel < panels.length; panel++) {
            GauntletDigits.draw(graphics, leftPos + PANEL_X[panel], topPos + PANEL_Y, panels[panel], false);
        }
    }

    /**
     * ⚠ Only the title is drawn, positioned over the plate rather than at vanilla's 6,6 where it would sit on top of
     * the ammunition rail. The inventory label keeps its own position from the constructor.
     */
    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xB4B4BA, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB4B4BA, false);

        if (GauntletSelfDestruct.isArmed(menu.getGauntlet())) {
            // ⚠ OUTSIDE THE WINDOW, TO THE LEFT. Inside it the wrapped message sat over the panels, the inventory and
            // the slot counts ([stated] "the text covers everything please place it to the left of the gui window").
            // These are GUI-local coords, so a negative x is off the panel's left edge.
            graphics.drawWordWrap(
                font,
                Component.translatable("gauntlet.avp_predator.destruct.armed"),
                -ARMED_MESSAGE_WIDTH - ARMED_MESSAGE_GAP,
                ARMED_MESSAGE_Y,
                ARMED_MESSAGE_WIDTH,
                0xFF5555
            );
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderTooltip(graphics, mouseX, mouseY);
    }
}
