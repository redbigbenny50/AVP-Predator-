package com.predator.client.screen;

import com.predator.PredatorResources;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** His 16x22 digit art (yautja_0..9 + blank), drawn into the GUI panels, optionally tinted green for "accepted". */
public final class GauntletDigits {

    public static final int WIDTH = 16;

    public static final int HEIGHT = 22;

    private static final ResourceLocation[] DIGITS = new ResourceLocation[10];

    /**
     * ⚠ A GREEN SET, NOT A TINT. GuiGraphics tinting is multiplicative, so a green tint over his RED segments can only
     * make them darker — "the number gets dull, it doesn't turn bright green". These are his files with the red
     * segments hue-shifted to green (same pixels, same shapes), so an accepted digit lights up instead of dimming.
     */
    private static final ResourceLocation[] DIGITS_GREEN = new ResourceLocation[10];

    private static final ResourceLocation BLANK = PredatorResources.location("textures/gui/gauntlet/yautja_blank.png");

    private static final ResourceLocation BLANK_GREEN = PredatorResources.location("textures/gui/gauntlet/yautja_blank_green.png");

    static {
        for (var digit = 0; digit < 10; digit++) {
            DIGITS[digit] = PredatorResources.location("textures/gui/gauntlet/yautja_" + digit + ".png");
            DIGITS_GREEN[digit] = PredatorResources.location("textures/gui/gauntlet/yautja_" + digit + "_green.png");
        }
    }

    private GauntletDigits() {
        throw new UnsupportedOperationException();
    }

    /** Draws digit {@code value} (0-9) or the blank for anything else; {@code accepted} uses the green set. */
    public static void draw(GuiGraphics graphics, int x, int y, int value, boolean accepted) {
        var isDigit = value >= 0 && value <= 9;
        var texture = accepted ? (isDigit ? DIGITS_GREEN[value] : BLANK_GREEN) : (isDigit ? DIGITS[value] : BLANK);

        graphics.blit(texture, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
    }
}
