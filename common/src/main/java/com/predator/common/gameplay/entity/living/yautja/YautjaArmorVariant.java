package com.predator.common.gameplay.entity.living.yautja;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Which armour set a jungle yautja is wearing.
 * <h2>⚠⚠ INDEPENDENT OF {@link YautjaVariant}, WHICH IS THE SKIN</h2> His ruling: a spawn egg can produce "regular
 * brush or tiger, and it can have either the regular armor or the alt". Two separate rolls, so all six combinations
 * occur. Folding armour into the skin enum would have made brush-with-alt impossible and quietly halved the variety.
 * <p>
 * The two are genuinely different things in the art, not two takes on one: the armour sheets share ZERO opaque pixels
 * with the body sheets — measured — because the armour bones occupy their own UV regions, which is why the armour is a
 * separate render layer rather than part of the body texture.
 */
public enum YautjaArmorVariant {

    REGULAR,
    ALT1;

    private static final YautjaArmorVariant[] VALUES = values();

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * ⚠ REGULAR resolves to the empty suffix so it lands on the existing {@code yautja_jungle_armor.png} — the sheet
     * that already ships. Only the alt adds one, so nothing that exists today has to be renamed.
     */
    public String textureSuffix() {
        return this == REGULAR ? "" : "_" + serializedName();
    }

    public static YautjaArmorVariant random(RandomSource random) {
        return VALUES[random.nextInt(VALUES.length)];
    }

    public static YautjaArmorVariant byName(String name) {
        for (var variant : VALUES) {
            if (variant.serializedName().equalsIgnoreCase(name)) {
                return variant;
            }
        }

        return REGULAR;
    }
}
