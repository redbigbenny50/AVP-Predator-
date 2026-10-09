package com.predator.common.gameplay.entity.living.yautja;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Body markings a jungle yautja can spawn with.
 * <p>
 * Cosmetic only for now — every variant shares one armour set and one armour texture, and none of them changes stats or
 * behaviour. The enum exists rather than a raw string so that when variants DO start to differ, the hook is already in
 * place and every call site is already switching on a closed set instead of comparing strings.
 * <p>
 * ⚠ {@link #serializedName()} is written to disk and accepted from {@code /summon} NBT, so these names are a
 * compatibility surface. Renaming one silently turns existing yautja back to {@link #NORMAL}.
 */
public enum YautjaVariant {

    NORMAL,
    TIGER,
    BRUSH;

    private static final YautjaVariant[] VALUES = values();

    /** {@return the lowercase name used in NBT and in {@code /summon ... {Variant:"tiger"}}} */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@return the texture file suffix, empty for {@link #NORMAL} so it keeps the plain body texture name} */
    public String textureSuffix() {
        return this == NORMAL ? "" : "_" + serializedName();
    }

    /** {@return an even roll across all variants} Used by natural spawns and the spawn egg. */
    public static YautjaVariant random(RandomSource random) {
        return VALUES[random.nextInt(VALUES.length)];
    }

    /** {@return the variant with this serialized name, or {@link #NORMAL} if it does not match one} */
    public static YautjaVariant byName(String name) {
        for (var variant : VALUES) {
            if (variant.serializedName().equalsIgnoreCase(name)) {
                return variant;
            }
        }

        return NORMAL;
    }
}
