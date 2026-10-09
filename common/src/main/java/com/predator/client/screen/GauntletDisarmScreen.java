package com.predator.client.screen;

import com.predator.Predator;
import com.predator.PredatorResources;
import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import com.predator.common.network.packet.C2SDisarmGauntletPayload;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * The disarm screen on a placed, counting gauntlet ({@code wrist_disarm_gui.png}).
 * <p>
 * Top-left: the disarm code, four digits. Right: the live countdown, four panels. Bottom-left: the confirm row — starts
 * 0000, each click adds one (9 wraps to 0), and a panel tints GREEN the moment it matches the code above it. All four
 * green sends the attempt; the server verifies and disarms. Under the panels: "to move bomb shift+rightclick".
 * Positions measured off his section image: code x 10/30/50/70 y 17; countdown x 90/110/130/150 y 34; confirm y 51.
 */
public class GauntletDisarmScreen extends Screen {

    private static final ResourceLocation TEXTURE = PredatorResources.location("textures/gui/wrist_disarm_gui.png");

    private static final int IMAGE_WIDTH = 176;

    private static final int IMAGE_HEIGHT = 166;

    private static final int[] CODE_X = { 10, 30, 50, 70 };

    private static final int CODE_Y = 17;

    private static final int[] COUNTDOWN_X = { 90, 110, 130, 150 };

    private static final int COUNTDOWN_Y = 34;

    private static final int CONFIRM_Y = 51;

    private static final int BLANK = -1;

    private final BlockPos pos;

    private final int code;

    private final long deadline;

    /**
     * The confirm row. ⚠ -1 is BLANK, not zero: the panels start empty and the first click on one shows 0, so an
     * untouched panel never looks like a deliberate 0 ([stated] "i want them to be blank, right now they are all 0s").
     * A blank panel can never match, so the code is only ever sent once all four have been set.
     */
    private final int[] confirm = new int[GauntletSelfDestruct.PANELS];

    private boolean sent;

    public GauntletDisarmScreen(BlockPos pos, int code, long deadline) {
        super(Component.translatable("gauntlet.avp_predator.destruct.disarm_title"));
        java.util.Arrays.fill(confirm, BLANK);
        this.pos = pos;
        this.code = code;
        this.deadline = deadline;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        var left = (width - IMAGE_WIDTH) / 2;
        var top = (height - IMAGE_HEIGHT) / 2;

        graphics.blit(TEXTURE, left, top, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        for (var panel = 0; panel < GauntletSelfDestruct.PANELS; panel++) {
            GauntletDigits.draw(graphics, left + CODE_X[panel], top + CODE_Y, GauntletSelfDestruct.digitAt(code, panel), false);
            GauntletDigits.draw(
                graphics,
                left + CODE_X[panel],
                top + CONFIRM_Y,
                confirm[panel],
                confirm[panel] == GauntletSelfDestruct.digitAt(code, panel)
            );
        }

        var now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : deadline;
        var remaining = (int) Mth.clamp(deadline - now, 0L, (long) GauntletSelfDestruct.COUNTDOWN_TICKS);
        var digits = GauntletSelfDestruct.countdownPanels(remaining);

        for (var panel = 0; panel < GauntletSelfDestruct.PANELS; panel++) {
            GauntletDigits.draw(graphics, left + COUNTDOWN_X[panel], top + COUNTDOWN_Y, digits[panel], false);
        }

        graphics.drawWordWrap(
            font,
            Component.translatable("gauntlet.avp_predator.destruct.move_hint"),
            left + 8,
            top + 78,
            IMAGE_WIDTH - 16,
            0xB4B4BA
        );

        if (remaining <= 0) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !sent) {
            var left = (width - IMAGE_WIDTH) / 2;
            var top = (height - IMAGE_HEIGHT) / 2;

            for (var panel = 0; panel < GauntletSelfDestruct.PANELS; panel++) {
                var x = left + CODE_X[panel];
                var y = top + CONFIRM_Y;

                if (mouseX >= x && mouseX < x + GauntletDigits.WIDTH && mouseY >= y && mouseY < y + GauntletDigits.HEIGHT) {
                    confirm[panel] = confirm[panel] == BLANK ? 0 : (confirm[panel] + 1) % 10;

                    var matched = confirm[panel] == GauntletSelfDestruct.digitAt(code, panel);

                    if (minecraft != null && minecraft.player != null) {
                        // The confirm tone when a panel turns green, the plain click otherwise.
                        minecraft.player.playSound(
                            matched
                                ? PredatorSoundEvents.GAUNTLET_DESTRUCT_DISARM_CONFIRM.get()
                                : PredatorSoundEvents.GAUNTLET_DESTRUCT_DISARM_BUTTON.get(),
                            1.0F,
                            1.0F
                        );
                    }

                    if (packed() == code) {
                        sent = true;
                        Predator.MOD.networking().sendToServer(new C2SDisarmGauntletPayload(pos.asLong(), code));
                        onClose();
                    }

                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** {@return the four digits as one number, or -1 while any panel is still blank} */
    private int packed() {
        var value = 0;

        for (var digit : confirm) {
            if (digit == BLANK) {
                return -1;
            }

            value = value * 10 + digit;
        }

        return value;
    }
}
