package com.predator.common.config;

import com.blib.api.BLibAPI;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.predator.Predator;
import com.predator.common.gameplay.entity.living.yautja.YautjaTier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-tier stats, overridable from {@code config/avp_predator-tiers.json}.
 * <h2>His ask: "add these tiers and stats to the config so people can change the values"</h2> Every number behind every
 * rank — health, armour, toughness, damage, knockback resistance, caster damage, deflect chance and both hunter
 * bonuses, for all six tiers. A pack that wants tougher or softer predators edits one file.
 * <h2>⚠ JSON and Gson, matching avp_alien</h2> Deliberately the same shape as {@code HiveConfigFile}: same
 * {@code config/} directory, same {@code <mod>-<thing>.json} naming, same "write a fresh file with every value at its
 * default when none exists" behaviour, same Gson. A modpack author who has edited one should find nothing surprising in
 * the other.
 * <h2>⚠⚠ IT WRITES A REAL FILE ON FIRST RUN</h2> With every key and its current value already in it, so it can be found
 * and edited without knowing the schema.
 * <h2>⚠ Defaults live in the ENUM</h2> This only overrides. A missing file, tier or field falls back to the coded
 * value, so a malformed edit costs one number rather than the whole tier and can never stop the game loading.
 */
public final class YautjaTierConfig {

    private static final String FILE_NAME = "avp_predator-tiers.json";

    private static final String CONFIG_DIR = "config";

    private static final Map<YautjaTier, TierStats> OVERRIDES = new EnumMap<>(YautjaTier.class);

    private YautjaTierConfig() {
        throw new UnsupportedOperationException();
    }

    /** One tier's numbers. ⚠ A record, so a partially-read tier cannot be half-applied. */
    public record TierStats(
        double health,
        double armor,
        double toughness,
        double damage,
        double knockbackResistance,
        double casterDamage,
        double thrownDamage,
        double techDamage,
        float deflectChance,
        double hunterStatBonus,
        double hunterHealthBonus
    ) {}

    /** {@return the configured stats for a tier, or its coded defaults} */
    public static TierStats get(YautjaTier tier) {
        return OVERRIDES.getOrDefault(tier, tier.defaults());
    }

    /**
     * Reads the config, writing defaults first if it does not exist.
     * <p>
     * ⚠ Called at mod init, BEFORE any yautja can spawn — attributes are written at spawn, so a config loaded later
     * would leave every already-loaded predator on the old numbers until its chunk reloaded.
     */
    public static void load() {
        var path = path();

        try {
            if (Files.notExists(path)) {
                save(path);
                Predator.LOGGER.info("Tier config: wrote a fresh {} with every value at its default", FILE_NAME);

                return;
            }

            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

            for (var tier : YautjaTier.values()) {
                if (json.has(tier.serializedName())) {
                    OVERRIDES.put(tier, read(json.getAsJsonObject(tier.serializedName()), tier.defaults()));
                }
            }
        } catch (Exception exception) {
            // ⚠ Swallowed on purpose. A typo in a config file must not stop the game booting — every tier falls back
            // to its coded defaults, which are the balanced ones.
            Predator.LOGGER.warn("Could not read {}; using default yautja tier stats.", FILE_NAME, exception);
        }
    }

    /** ⚠ Field by field, so an edit that omits or misspells one key loses only that value. */
    private static TierStats read(JsonObject json, TierStats defaults) {
        return new TierStats(
            number(json, "health", defaults.health()),
            number(json, "armor", defaults.armor()),
            number(json, "toughness", defaults.toughness()),
            number(json, "damage", defaults.damage()),
            number(json, "knockback_resistance", defaults.knockbackResistance()),
            number(json, "caster_damage", defaults.casterDamage()),
            number(json, "thrown_damage", defaults.thrownDamage()),
            number(json, "tech_damage", defaults.techDamage()),
            (float) number(json, "deflect_chance", defaults.deflectChance()),
            number(json, "hunter_stat_bonus", defaults.hunterStatBonus()),
            number(json, "hunter_health_bonus", defaults.hunterHealthBonus())
        );
    }

    /** ⚠ A value that will not parse falls back rather than throwing — one bad field, one lost number. */
    private static double number(JsonObject json, String key, double fallback) {
        if (!json.has(key)) {
            return fallback;
        }

        try {
            return json.get(key).getAsDouble();
        } catch (RuntimeException exception) {
            Predator.LOGGER.warn("{} in {} is not a number; using {}.", key, FILE_NAME, fallback);

            return fallback;
        }
    }

    private static void save(Path path) throws IOException {
        var root = new JsonObject();

        root.addProperty(
            "_comment",
            "Yautja class ranks. deflect_chance is 0..1; hunter_* are the extra a HUNTER gets on top, as a fraction."
                + " What actually spawns is blooded (tier 2). Delete this file to restore defaults."
        );

        for (var tier : YautjaTier.values()) {
            var stats = tier.defaults();
            var entry = new JsonObject();

            entry.addProperty("health", stats.health());
            entry.addProperty("armor", stats.armor());
            entry.addProperty("toughness", stats.toughness());
            entry.addProperty("damage", stats.damage());
            entry.addProperty("knockback_resistance", stats.knockbackResistance());
            entry.addProperty("caster_damage", stats.casterDamage());
            entry.addProperty("thrown_damage", stats.thrownDamage());
            entry.addProperty("tech_damage", stats.techDamage());
            entry.addProperty("deflect_chance", stats.deflectChance());
            entry.addProperty("hunter_stat_bonus", stats.hunterStatBonus());
            entry.addProperty("hunter_health_bonus", stats.hunterHealthBonus());

            root.add(tier.serializedName(), entry);
        }

        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    /**
     * ⚠ {@code BLibAPI.getGameDirectory()}, the same resolver avp_alien's property file uses — NOT a bare relative
     * path. It works identically on both loaders and does not assume the working directory.
     * <p>
     * ⚠ Loaded at mod INIT, so unlike the hive config it cannot take a {@code MinecraftServer}: tier attributes are
     * needed before a server exists on the client.
     */
    private static Path path() {
        return BLibAPI.getGameDirectory().resolve(CONFIG_DIR).resolve(FILE_NAME);
    }
}
