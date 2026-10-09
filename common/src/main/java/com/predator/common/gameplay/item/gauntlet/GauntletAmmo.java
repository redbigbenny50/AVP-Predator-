package com.predator.common.gameplay.item.gauntlet;

import java.util.Locale;

/**
 * What a gauntlet can be set to fire.
 * <h2>His spec</h2> "cycle through the loaded ammo with shift+rightclick... it will tell you on screen which is
 * selected — dart, net, shield. If its missing the ammo it will give you an error: 'no ammo' for net and dart, 'not
 * equipped' for shield."
 * <h2>⚠ SHIELD IS NOT IN THE CYCLE</h2> [stated] "replace shield with pellet we arent ready for the shield yet." The
 * cycle is dart, fire pellet, net. The consumable flag and the "not equipped" message are kept so a device entry can
 * come back with no rewiring; a gauntlet saved with "shield" selected falls back to DART via {@link #byName}.
 * <p>
 * ⚠ The failure message is a property of the ENTRY, not one shared string: a missing consumable and a missing device
 * are different problems and should not read the same.
 */
public enum GauntletAmmo {

    /** Consumable — lives in the ammunition slots, reports "no ammo" when the strip is empty. */
    DART(true, "veritanium_dart"),
    /** Incendiary rounds. Cycles right after the dart, as the other "bullet". */
    FIRE_PELLET(true, "fire_pellet"),

    NET(true, "net"),

    /**
     * The chain whip: a grapple fired from the gauntlet, NOT consumed.
     * <p>
     * [stated] "its a gauntlet item not consumed. It fires from the gauntlet." The consumable flag was already here for
     * exactly this, and it also picks the right refusal message — "not equipped" rather than "no ammo", which is the
     * truthful one for a device you either have loaded or do not.
     */
    CHAIN_WHIP(false, "chain_whip"),

    /** [stated] fired from the gauntlet; spent to fire, handed back when it returns. */
    PLASMA_SHURIKEN(true, "plasma_shuriken");

    private static final GauntletAmmo[] VALUES = values();

    private final boolean consumable;

    private final String itemPath;

    GauntletAmmo(boolean consumable, String itemPath) {
        this.consumable = consumable;
        this.itemPath = itemPath;
    }

    public boolean consumable() {
        return consumable;
    }

    /** {@return the registry path this fires, or null when nothing has been built for it yet} */
    public String itemPath() {
        return itemPath;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return "gauntlet.avp_predator.ammo." + serializedName();
    }

    public String missingKey() {
        return consumable ? "gauntlet.avp_predator.no_ammo" : "gauntlet.avp_predator.not_equipped";
    }

    public GauntletAmmo next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static GauntletAmmo byName(String name) {
        for (var value : VALUES) {
            if (value.serializedName().equalsIgnoreCase(name)) {
                return value;
            }
        }

        return DART;
    }
}
