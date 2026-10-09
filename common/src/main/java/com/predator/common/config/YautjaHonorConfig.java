package com.predator.common.config;

import com.blib.api.BLibAPI;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.predator.Predator;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Yautja honor: what each advancement is worth, and how much honor makes a player worthy of being hunted.
 * {@code config/avp_predator-honor.json}, same shape and behaviour as {@code avp_predator-tiers.json}.
 * <h2>His rulings (Oct 3-4)</h2>
 * <ul>
 * <li>Advancements across vanilla, avp_alien, avp_human and avp_predator give honor — gear at the low end, bosses at
 * the top, nothing above 50.</li>
 * <li>No single advancement unlocks a hunt; honor accumulates to {@link #unlockThreshold()} (150).</li>
 * <li>Honor is a currency for the planned faction work, so it is kept even after the unlock.</li>
 * </ul>
 * <h2>Cross-mod entries are just ids</h2> An avp_alien or avp_human id is a string. When that mod is not installed the
 * advancement does not exist, nothing can award it, and the entry simply never pays out — no class reference, no load
 * error.
 * <h2>⚠ The file REPLACES the list</h2> Unlike the tier config, which overrides field by field, an {@code advancements}
 * block in the file is the whole list, so a pack can REMOVE entries as well as add or retune them. Leave the block out
 * to keep the built-in list. A malformed file falls back to the defaults and never stops the game loading.
 */
public final class YautjaHonorConfig {

    private static final String FILE_NAME = "avp_predator-honor.json";

    private static final String CONFIG_DIR = "config";

    /** [stated] "this seems like a good total". */
    public static final int DEFAULT_UNLOCK_THRESHOLD = 150;

    private static final Map<String, Integer> DEFAULTS = buildDefaults();

    private static Map<String, Integer> values = DEFAULTS;

    private static int unlockThreshold = DEFAULT_UNLOCK_THRESHOLD;

    // ---------------------------------------------------------------- Hunter tier ladder (the "hunters" block)

    /**
     * Honor at which each tier joins the roll, Youngblood..Clan Leader. Youngblood is always in. [stated] he approved
     * the climb and asked for room above the ~1,100 the advancement table tops out at, since Hunter kills keep paying.
     */
    private static final int[] DEFAULT_TIER_UNLOCKS = { 0, 250, 600, 1000, 1500 };

    /**
     * Percentage points that move up each rung per hunt won. [stated] "you can increase the percentages to 10 or 15".
     */
    private static final int DEFAULT_LADDER_STEP = 10;

    /** Honor for killing a Hunter, by its tier, Youngblood..Clan Leader. Capped at 50 like every other honor source. */
    private static final int[] DEFAULT_KILL_HONOR = { 20, 25, 30, 40, 50 };

    private static final String[] LADDER_NAMES = { "youngblood", "blooded", "elite", "elder", "clan_leader" };

    private static int[] tierUnlocks = DEFAULT_TIER_UNLOCKS.clone();

    private static int ladderStep = DEFAULT_LADDER_STEP;

    private static int[] killHonor = DEFAULT_KILL_HONOR.clone();

    /** {@return the honor at which ladder rung {@code rung} (0 = Youngblood .. 4 = Clan Leader) joins the roll} */
    public static int tierUnlock(int rung) {
        return tierUnlocks[rung];
    }

    public static int ladderStep() {
        return ladderStep;
    }

    /** {@return the honor for killing a Hunter on ladder rung {@code rung}} */
    public static int killHonor(int rung) {
        return killHonor[rung];
    }

    private YautjaHonorConfig() {
        throw new UnsupportedOperationException();
    }

    /** {@return the honor an advancement is worth, or 0 if it is not on the list} */
    public static int honorFor(ResourceLocation advancement) {
        return values.getOrDefault(advancement.toString(), 0);
    }

    /** {@return every advancement on the list and its worth, in file order} */
    public static Map<String, Integer> all() {
        return Collections.unmodifiableMap(values);
    }

    /** {@return the honor at which a player becomes worthy of being hunted} */
    public static int unlockThreshold() {
        return unlockThreshold;
    }

    private static Map<String, Integer> buildDefaults() {
        var defaults = new LinkedHashMap<String, Integer>();
        // 5
        put(defaults, "minecraft:adventure/kill_a_mob", 5);
        put(defaults, "minecraft:adventure/avoid_vibration", 5);
        put(defaults, "minecraft:nether/obtain_blaze_rod", 5);
        put(defaults, "minecraft:nether/summon_wither", 5);
        put(defaults, "minecraft:end/respawn_dragon", 5);
        put(defaults, "avp_alien:aliens/kill_an_alien", 5);
        put(defaults, "avp_alien:aliens/block_spitter_spit_with_head_shield", 5);
        put(defaults, "avp_human:humans/has_gun", 5);
        put(defaults, "avp_predator:hunt/live_fire", 5);
        // 10
        put(defaults, "minecraft:story/shiny_gear", 10);
        put(defaults, "minecraft:adventure/voluntary_exile", 10);
        put(defaults, "minecraft:end/elytra", 10);
        put(defaults, "minecraft:nether/get_wither_skull", 10);
        put(defaults, "minecraft:adventure/totem_of_undying", 10);
        put(defaults, "avp_alien:aliens/chitin_armor", 10);
        put(defaults, "avp_alien:aliens/remove_embryo_with_chorus_fruit", 10);
        put(defaults, "avp_human:humans/hire_marine", 10);
        // 15
        put(defaults, "minecraft:nether/netherite_armor", 15);
        put(defaults, "avp_alien:aliens/plated_chitin_armor", 15);
        put(defaults, "avp_alien:aliens/kill_all_normal_aliens", 15);
        put(defaults, "avp_alien:aliens/kill_all_irradiated_aliens", 15);
        put(defaults, "avp_alien:aliens/kill_all_nether_aliens", 15);
        put(defaults, "avp_alien:aliens/kill_all_aberrant_aliens", 15);
        put(defaults, "avp_predator:hunt/wy_ape_set", 15);
        // 20
        put(defaults, "minecraft:adventure/sniper_duel", 20);
        put(defaults, "minecraft:adventure/arbalistic", 20);
        put(defaults, "minecraft:adventure/two_birds_one_arrow", 20);
        put(defaults, "minecraft:adventure/blowback", 20);
        put(defaults, "minecraft:nether/return_to_sender", 20);
        put(defaults, "minecraft:nether/loot_bastion", 20);
        put(defaults, "minecraft:adventure/overoverkill", 20);
        put(defaults, "avp_alien:aliens/withstand_attack_party", 20);
        put(defaults, "avp_alien:aliens/kill_a_royal_alien", 20);
        // 35
        put(defaults, "minecraft:adventure/hero_of_the_village", 35);
        put(defaults, "minecraft:adventure/revaulting", 35);
        put(defaults, "minecraft:nether/uneasy_alliance", 35);
        put(defaults, "minecraft:adventure/kill_all_mobs", 35);
        put(defaults, "avp_alien:aliens/defeat_a_raid", 35);
        put(defaults, "avp_alien:aliens/dual_variant_raids", 35);
        put(defaults, "avp_alien:aliens/lead_raid_to_enemy_hive", 35);
        put(defaults, "avp_alien:aliens/kill_a_harbinger", 35);
        put(defaults, "avp_alien:aliens/kill_a_hive", 35);
        put(defaults, "avp_predator:hunt/blooded", 35);
        put(defaults, "avp_predator:hunt/kill_elder_guardian", 35);
        // 50
        put(defaults, "minecraft:end/kill_dragon", 50);
        put(defaults, "avp_alien:aliens/kill_an_empress", 50);
        put(defaults, "avp_alien:aliens/kill_a_lineage", 50);
        put(defaults, "avp_alien:aliens/broken_throne", 50);
        put(defaults, "avp_alien:aliens/kill_all_aliens", 50);
        put(defaults, "avp_predator:hunt/kill_wither", 50);
        put(defaults, "avp_predator:hunt/kill_warden", 50);
        // Post-hunt: only reachable once hunted, so they never decide an unlock.
        put(defaults, "avp_predator:hunt/survive_the_hunt", 50);
        put(defaults, "avp_predator:hunt/hunters_trophy", 50);
        put(defaults, "avp_predator:hunt/veritanium_set", 40);
        return defaults;
    }

    private static void put(Map<String, Integer> map, String id, int honor) {
        map.put(id, honor);
    }

    /** Reads the config, writing the defaults first if it does not exist. Called at mod init. */
    public static void load() {
        var path = path();

        try {
            if (Files.notExists(path)) {
                save(path);
                Predator.LOGGER.info("Honor config: wrote a fresh {} with the built-in honor list", FILE_NAME);

                return;
            }

            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

            if (json.has("unlock_threshold")) {
                unlockThreshold = Math.max(0, json.get("unlock_threshold").getAsInt());
            }

            if (json.has("advancements")) {
                var read = new LinkedHashMap<String, Integer>();

                for (var entry : json.getAsJsonObject("advancements").entrySet()) {
                    try {
                        if (ResourceLocation.tryParse(entry.getKey()) == null) {
                            Predator.LOGGER.warn("{}: '{}' is not a valid advancement id; skipped.", FILE_NAME, entry.getKey());
                            continue;
                        }

                        read.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
                    } catch (RuntimeException exception) {
                        Predator.LOGGER.warn("{}: honor for '{}' is not a number; skipped.", FILE_NAME, entry.getKey());
                    }
                }

                values = read;
            }

            // ⚠ The hunters block is handled on its own, so a typo in it — or a read-only file that cannot take the
            // added block — never throws the whole config back to defaults and loses a pack's advancement list.
            try {
                if (json.has("hunters")) {
                    readHunters(json.getAsJsonObject("hunters"));
                } else {
                    // A file written before the ladder existed: the block is added with the defaults, and nothing
                    // else in the file is touched.
                    json.add("hunters", huntersJson());
                    Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json));
                    Predator.LOGGER.info("Honor config: added the hunters block to {}", FILE_NAME);
                }
            } catch (Exception exception) {
                tierUnlocks = DEFAULT_TIER_UNLOCKS.clone();
                ladderStep = DEFAULT_LADDER_STEP;
                killHonor = DEFAULT_KILL_HONOR.clone();
                Predator.LOGGER.warn("{}: could not read or add the hunters block; using its defaults.", FILE_NAME, exception);
            }
        } catch (Exception exception) {
            // ⚠ Swallowed on purpose: a typo in a config must not stop the game booting.
            values = DEFAULTS;
            unlockThreshold = DEFAULT_UNLOCK_THRESHOLD;
            tierUnlocks = DEFAULT_TIER_UNLOCKS.clone();
            ladderStep = DEFAULT_LADDER_STEP;
            killHonor = DEFAULT_KILL_HONOR.clone();
            Predator.LOGGER.warn("Could not read {}; using the built-in honor list.", FILE_NAME, exception);
        }
    }

    private static void save(Path path) throws IOException {
        var root = new JsonObject();
        root.addProperty(
            "_comment",
            "Yautja honor. Each advancement id is worth this much honor, once, when a player earns it."
                + " A player who reaches unlock_threshold becomes worthy of being hunted."
                + " The advancements block REPLACES the built-in list, so entries can be removed."
                + " Ids from mods that are not installed are simply never awarded. Delete this file to restore defaults."
        );
        root.addProperty("unlock_threshold", DEFAULT_UNLOCK_THRESHOLD);

        var list = new JsonObject();
        DEFAULTS.forEach(list::addProperty);
        root.add("advancements", list);
        root.add("hunters", huntersJson());

        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    /** Reads the hunters block field by field; anything missing or malformed keeps its default. */
    private static void readHunters(JsonObject hunters) {
        if (hunters.has("ladder_step")) {
            ladderStep = Math.max(1, Math.min(50, hunters.get("ladder_step").getAsInt()));
        }

        if (hunters.has("tier_unlock_honor")) {
            var unlocks = hunters.getAsJsonObject("tier_unlock_honor");

            // Youngblood is always unlocked; it is not read.
            for (var rung = 1; rung < LADDER_NAMES.length; rung++) {
                if (unlocks.has(LADDER_NAMES[rung])) {
                    tierUnlocks[rung] = Math.max(0, unlocks.get(LADDER_NAMES[rung]).getAsInt());
                }
            }
        }

        if (hunters.has("kill_honor")) {
            var kills = hunters.getAsJsonObject("kill_honor");

            for (var rung = 0; rung < LADDER_NAMES.length; rung++) {
                if (kills.has(LADDER_NAMES[rung])) {
                    killHonor[rung] = Math.max(0, Math.min(50, kills.get(LADDER_NAMES[rung]).getAsInt()));
                }
            }
        }
    }

    private static JsonObject huntersJson() {
        var hunters = new JsonObject();
        hunters.addProperty(
            "_comment",
            "The Hunter tier ladder. The first hunt is always a Youngblood. A tier joins the roll at its tier_unlock_honor"
                + " (entering at ladder_step percent, taken from the tier below), and every hunt won moves ladder_step"
                + " percent up each rung, so the lower tiers fade out over time. kill_honor is paid for each Hunter killed."
        );
        hunters.addProperty("ladder_step", DEFAULT_LADDER_STEP);

        var unlocks = new JsonObject();
        var kills = new JsonObject();

        for (var rung = 0; rung < LADDER_NAMES.length; rung++) {
            if (rung > 0) {
                unlocks.addProperty(LADDER_NAMES[rung], DEFAULT_TIER_UNLOCKS[rung]);
            }

            kills.addProperty(LADDER_NAMES[rung], DEFAULT_KILL_HONOR[rung]);
        }

        hunters.add("tier_unlock_honor", unlocks);
        hunters.add("kill_honor", kills);

        return hunters;
    }

    private static Path path() {
        return BLibAPI.getGameDirectory().resolve(CONFIG_DIR).resolve(FILE_NAME);
    }
}
