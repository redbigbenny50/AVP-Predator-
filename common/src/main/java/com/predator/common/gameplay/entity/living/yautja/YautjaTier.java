package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.config.YautjaTierConfig;

import java.util.Locale;

/**
 * The five yautja class ranks, and the numbers behind each.
 * <h2>⚠⚠ WHAT SPAWNS TODAY IS BLOODED — TIER 2</h2> His ruling: "the yautja_jungle is a tier 2 rank, same with
 * summoning it". So {@link #BLOODED} is the default for every natural spawn, every spawn egg and every {@code /summon},
 * and the other four exist for the hunting system to reach later. Defaulting to tier 1 would have quietly WEAKENED
 * every predator already in players' worlds.
 * <h2>Toughness falls as the tier rises, on purpose</h2> ⚠ Not a mistake in the table. At toughness 16 — where the
 * entity has been until now — a diamond sword and a netherite axe are reduced almost identically, so better gear stops
 * mattering and only patience does. Lower toughness on the higher tiers is what makes a weapon upgrade visible against
 * them.
 * <h2>The boss bonuses are INVERTED</h2> ⚠ A low tier gets the BIGGER hunter bonus (+10% stats / +30% health) and Clan
 * Leader the smallest (+2% / +10%). That is deliberate: armour and toughness multiply the same incoming damage, so a
 * small percentage at a high tier still compounds. The inversion is what makes a Hunter meaningfully harder at EVERY
 * rank rather than only at the top.
 */
public enum YautjaTier {

    /**
     * Tier 0 — the rookies, not yet blooded.
     * <p>
     * ⚠ His spec: Youngblood's stats minus 5%, HEALTH UNCHANGED. So it dies in the same number of hits as a Youngblood
     * but hits softer and is easier to get through — a rookie is inexperienced, not frail. Keeping the health is what
     * stops tier 0 being a one-punch mob and makes the difference read as skill rather than fragility.
     * <p>
     * ⚠ Its hunter bonus MATCHES Youngblood's (+10% stats / +30% health) rather than extending the inversion to
     * something larger. He gave figures for the stats and not for that, so I did not invent one — say the word if a
     * tier-0 Hunter should get more.
     */
    UNBLOODED(
        130.0, 9.5, 4.75, 9.5, 0.33, 8.55, 0.095F, 0.10, 0.30, 0.95, 0.95
    ),

    YOUNGBLOOD(
        130.0, 10.0, 5.0, 10.0, 0.35, 9.0, 0.10F, 0.10, 0.30, 1.0, 1.0
    ),
    BLOODED(
        150.0, 11.0, 6.0, 11.0, 0.50, 12.0, 0.12F, 0.08, 0.15, 1.1, 1.33
    ),
    ELITE(
        220.0, 13.0, 8.0, 13.0, 0.65, 15.0, 0.15F, 0.06, 0.10, 1.3, 1.67
    ),
    ELDER(
        350.0, 15.0, 10.0, 16.0, 0.80, 18.0, 0.18F, 0.04, 0.10, 1.6, 2.0
    ),
    CLAN_LEADER(
        550.0, 17.0, 12.0, 20.0, 1.00, 24.0, 0.20F, 0.02, 0.10, 2.0, 2.67
    );

    /** ⚠ What a spawn egg, a natural spawn and /summon all produce. Changing this rebalances every existing world. */
    /** ⚠ Still BLOODED, not the new tier 0 — his ruling that what spawns is tier 2 is unchanged. */
    public static final YautjaTier DEFAULT = BLOODED;

    private static final YautjaTier[] VALUES = values();

    private final double health;

    private final double armor;

    private final double toughness;

    private final double damage;

    private final double knockbackResistance;

    private final double casterDamage;

    private final float deflectChance;

    private final double hunterStatBonus;

    private final double hunterHealthBonus;

    /**
     * ⚠⚠ A MULTIPLIER, NOT A DAMAGE VALUE. Every thrown weapon (combi stick, shuriken, smart disc, fire pellet,
     * veritanium dart) carries its own flat damage on its projectile class, so before this an Elder threw a spear
     * exactly as hard as an Unblooded — the tier simply did not reach them. This scales all of them at once, which is
     * what gives a clan's weapon PREFERENCE something to bend. Youngblood is the 1.0 baseline; the curve follows
     * melee's (0.95 / 1.0 / 1.1 / 1.3 / 1.6 / 2.0) because a thrown blade is a physical weapon, not a plasma bolt.
     */
    private final double thrownDamage;

    /**
     * ⚠⚠ THE SECOND MULTIPLIER: TECH. [stated] "dart and pellet should be tech damage like caster and it has its own
     * multiplier this also would apply to the plasma bow item ... the other projectiles count as thrown damage aka
     * ranged."
     * <p>
     * The split is what the weapon runs on, not how far it reaches. MUSCLE-powered weapons (combi stick, shuriken,
     * smart disc) ride {@link #thrownDamage} — a stronger hunter throws harder. DEVICE-powered ones (wrist dart, fire
     * pellet, and the plasma bow when it exists) ride this — tier there means better equipment, not a better arm, which
     * is why the curve is the caster's own progression rather than melee's.
     * <p>
     * ⚠ The plasma CASTER is not multiplied by this: it carries its own absolute per-tier damage
     * ({@code casterDamage}), and multiplying that as well would scale it twice.
     */
    private final double techDamage;

    YautjaTier(
        double health,
        double armor,
        double toughness,
        double damage,
        double knockbackResistance,
        double casterDamage,
        float deflectChance,
        double hunterStatBonus,
        double hunterHealthBonus,
        double thrownDamage,
        double techDamage
    ) {
        this.health = health;
        this.armor = armor;
        this.toughness = toughness;
        this.damage = damage;
        this.knockbackResistance = knockbackResistance;
        this.casterDamage = casterDamage;
        this.deflectChance = deflectChance;
        this.hunterStatBonus = hunterStatBonus;
        this.hunterHealthBonus = hunterHealthBonus;
        this.thrownDamage = thrownDamage;
        this.techDamage = techDamage;
    }

    /**
     * {@return this tier's value for a stat, with the hunter bonus folded in when applicable}
     * <p>
     * ⚠ Health scales by its OWN bonus and everything else by the stat bonus, because they behave differently: health
     * is linear (+15% health is +15% time, always) while armour and toughness multiply the same damage stream and so
     * compound with each other AND with health. Keeping the two separate is what makes "+8% stats, +15% health"
     * predictable instead of secretly being +22%.
     */
    /**
     * {@return this tier's coded defaults}
     * <p>
     * ⚠ The config OVERRIDES these; it does not replace them. A missing file, tier or field falls back here, so a
     * malformed edit costs one number rather than the whole tier.
     */
    public YautjaTierConfig.TierStats defaults() {
        return new YautjaTierConfig.TierStats(
            health,
            armor,
            toughness,
            damage,
            knockbackResistance,
            casterDamage,
            thrownDamage,
            techDamage,
            deflectChance,
            hunterStatBonus,
            hunterHealthBonus
        );
    }

    /** {@return the multiplier this tier applies to every thrown weapon's damage} */
    public double thrownDamage(boolean hunter) {
        return scaled(YautjaTierConfig.get(this).thrownDamage(), hunter);
    }

    /**
     * {@return {@code base} scaled by the thrower's tier, or unchanged if the thrower is not a yautja}
     * <p>
     * The one call every thrown weapon makes. A player throwing a combi stick is not a yautja and gets the flat value.
     */
    public static float scaleThrown(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity thrower, float base) {
        if (thrower instanceof Yautja yautja) {
            return (float) (base * yautja.getTier().thrownDamage(yautja.isHunter()));
        }

        return base;
    }

    /** {@return the multiplier this tier applies to DEVICE-powered weapons} See {@link #techDamage}. */
    public double techDamage(boolean hunter) {
        return scaled(YautjaTierConfig.get(this).techDamage(), hunter);
    }

    /** {@return {@code base} scaled by the wielder's TECH tier, or unchanged if the wielder is not a yautja} */
    public static float scaleTech(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity wielder, float base) {
        if (wielder instanceof Yautja yautja) {
            return (float) (base * yautja.getTier().techDamage(yautja.isHunter()));
        }

        return base;
    }

    public double health(boolean hunter) {
        var stats = YautjaTierConfig.get(this);

        return hunter ? stats.health() * (1.0 + stats.hunterHealthBonus()) : stats.health();
    }

    public double armor(boolean hunter) {
        return scaled(YautjaTierConfig.get(this).armor(), hunter);
    }

    public double toughness(boolean hunter) {
        return scaled(YautjaTierConfig.get(this).toughness(), hunter);
    }

    public double damage(boolean hunter) {
        return scaled(YautjaTierConfig.get(this).damage(), hunter);
    }

    public double casterDamage(boolean hunter) {
        return scaled(YautjaTierConfig.get(this).casterDamage(), hunter);
    }

    public double knockbackResistance() {
        return YautjaTierConfig.get(this).knockbackResistance();
    }

    /** ⚠ NOT scaled by the hunter bonus — a flat chance is already a multiplier, and stacking two would double-dip. */
    /**
     * {@return whether being outnumbered ALONE makes this tier give ground} The honour code: the young back off, the
     * hardened do not. Every tier still falls back when it is losing health fast.
     */
    public boolean fallsBackWhenOutnumbered() {
        return ordinal() <= BLOODED.ordinal();
    }

    public float deflectChance() {
        return YautjaTierConfig.get(this).deflectChance();
    }

    /** ⚠ Reads the hunter bonus from the CONFIG too, or a pack could retune a tier and still get vanilla bonuses. */
    private double scaled(double value, boolean hunter) {
        return hunter ? value * (1.0 + YautjaTierConfig.get(this).hunterStatBonus()) : value;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static YautjaTier byName(String name) {
        for (var tier : VALUES) {
            if (tier.serializedName().equalsIgnoreCase(name)) {
                return tier;
            }
        }

        return DEFAULT;
    }
}
