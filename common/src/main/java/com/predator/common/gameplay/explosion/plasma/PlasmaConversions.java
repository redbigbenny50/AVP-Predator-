package com.predator.common.gameplay.explosion.plasma;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.predator.Predator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the gauntlet's plasma detonation scorches blocks into at the rim of its crater. [stated] Oct 2: the same
 * data-pack method as avp_human's nuke, so other mods' blocks can be added; anything NOT listed changes exactly as it
 * always did (blackstone).
 * <h2>Files</h2> {@code data/<any namespace>/avp_predator/plasma_conversions/<any name>.json}, reloaded by
 * {@code /reload}. Every file is read; a pack overrides ours by shipping the same path ({@code avp_predator:default}).
 *
 * <pre>
 * { "conversions": [
 *     { "from": "minecraft:stone", "to": "minecraft:cobblestone" },
 *     { "from": "#minecraft:dirt", "to": "minecraft:coarse_dirt" },
 *     { "from": ["#minecraft:sand", "minecraft:sandstone"],
 *       "to": [ { "block": "minecraft:glass", "weight": 3 }, { "block": "minecraft:magma_block", "weight": 1 } ] }
 * ] }
 * </pre>
 *
 * <h2>Which rule wins</h2> An exact block beats a {@code #tag}. Tags are tried in file order, files in name order. The
 * same block listed twice: the later one wins. A block or result from a mod that is not installed is skipped with a
 * warning, so a pack can safely list other mods' blocks.
 * <h2>What stays in code</h2> Which blocks scorch at all - solid ones in the rim band, half of them at random - and the
 * crater itself (air, fire on the floor) are unchanged; this table only decides what a scorched block becomes.
 */
public final class PlasmaConversions {

    /** [stated] Oct 2: an unlisted block changes as it always did - the plasma rim scorches it to blackstone. */
    private static final Block FALLBACK = Blocks.BLACKSTONE;

    private static volatile Table table = Table.EMPTY;

    private PlasmaConversions() {}

    /** The block {@code state} becomes at the rim. Server thread. */
    public static Block convert(BlockState state, RandomSource random) {
        return table.resultFor(state).pick(random);
    }

    /** Replaces the whole table. Called by the reload listener once all files are read. */
    public static void install(List<FileEntries> files) {
        var exact = new HashMap<Block, Result>();
        var tagged = new ArrayList<TagRule>();

        for (var file : files) {
            for (var rule : file.rules()) {
                for (var block : rule.blocks()) {
                    exact.put(block, rule.result());
                }

                for (var tag : rule.tags()) {
                    tagged.add(new TagRule(tag, rule.result()));
                }
            }
        }

        table = new Table(exact, tagged);
        Predator.LOGGER.info("[Plasma] Loaded {} block and {} tag conversions", exact.size(), tagged.size());
    }

    /**
     * Reads one file. Bad entries are logged and skipped; the rest of the file still loads.
     */
    public static FileEntries parse(ResourceLocation file, JsonElement json) {
        var rules = new ArrayList<Rule>();

        if (!json.isJsonObject() || !json.getAsJsonObject().has("conversions")) {
            Predator.LOGGER.error("[Plasma] {}: expected an object with a \"conversions\" list", file);
            return new FileEntries(rules);
        }

        var list = json.getAsJsonObject().get("conversions");

        if (!list.isJsonArray()) {
            Predator.LOGGER.error("[Plasma] {}: \"conversions\" must be a list", file);
            return new FileEntries(rules);
        }

        int index = 0;

        for (var element : list.getAsJsonArray()) {
            var rule = parseRule(file, index++, element);

            if (rule != null) {
                rules.add(rule);
            }
        }

        return new FileEntries(rules);
    }

    private static @Nullable Rule parseRule(ResourceLocation file, int index, JsonElement element) {
        if (!element.isJsonObject()) {
            Predator.LOGGER.error("[Plasma] {} entry {}: expected an object", file, index);
            return null;
        }

        var object = element.getAsJsonObject();

        if (!object.has("from") || !object.has("to")) {
            Predator.LOGGER.error("[Plasma] {} entry {}: needs both \"from\" and \"to\"", file, index);
            return null;
        }

        var result = parseResult(file, index, object.get("to"));

        if (result == null) {
            return null;
        }

        var blocks = new ArrayList<Block>();
        var tags = new ArrayList<TagKey<Block>>();
        var from = object.get("from");
        var sources = from.isJsonArray() ? from.getAsJsonArray() : single(from);

        for (var source : sources) {
            if (!source.isJsonPrimitive()) {
                Predator.LOGGER.error("[Plasma] {} entry {}: \"from\" must be block ids or #tags", file, index);
                continue;
            }

            var text = source.getAsString();

            if (text.startsWith("#")) {
                var id = ResourceLocation.tryParse(text.substring(1));

                if (id == null) {
                    Predator.LOGGER.error("[Plasma] {} entry {}: bad tag \"{}\"", file, index, text);
                    continue;
                }

                // ⚠ Kept as a key and tested at blast time: block tags are not bound to blocks yet while data
                // listeners run, so resolving them here would see every tag empty.
                tags.add(TagKey.create(Registries.BLOCK, id));
            } else {
                var block = block(file, index, text);

                if (block != null) {
                    blocks.add(block);
                }
            }
        }

        return blocks.isEmpty() && tags.isEmpty() ? null : new Rule(blocks, tags, result);
    }

    private static @Nullable Result parseResult(ResourceLocation file, int index, JsonElement to) {
        var choices = new ArrayList<Block>();
        var weights = new ArrayList<Integer>();

        for (var option : to.isJsonArray() ? to.getAsJsonArray() : single(to)) {
            String id;
            int weight = 1;

            if (option.isJsonPrimitive()) {
                id = option.getAsString();
            } else if (option.isJsonObject() && option.getAsJsonObject().has("block")) {
                JsonObject object = option.getAsJsonObject();
                id = object.get("block").getAsString();
                weight = object.has("weight") ? object.get("weight").getAsInt() : 1;
            } else {
                Predator.LOGGER.error("[Plasma] {} entry {}: each \"to\" must be a block id or {{\"block\", \"weight\"}}", file, index);
                continue;
            }

            if (weight <= 0) {
                Predator.LOGGER.error("[Plasma] {} entry {}: weight for {} must be above 0", file, index, id);
                continue;
            }

            var block = block(file, index, id);

            if (block != null) {
                choices.add(block);
                weights.add(weight);
            }
        }

        if (choices.isEmpty()) {
            Predator.LOGGER.warn("[Plasma] {} entry {}: no usable result, entry skipped", file, index);
            return null;
        }

        return new Result(choices, weights);
    }

    private static @Nullable Block block(ResourceLocation file, int index, String text) {
        var id = ResourceLocation.tryParse(text);

        if (id == null) {
            Predator.LOGGER.error("[Plasma] {} entry {}: bad block id \"{}\"", file, index, text);
            return null;
        }

        // An absent mod's block: skipped quietly enough to be normal, loudly enough to spot a typo.
        var block = BuiltInRegistries.BLOCK.getOptional(id);

        if (block.isEmpty()) {
            Predator.LOGGER.warn("[Plasma] {} entry {}: no block \"{}\" (mod not installed?) - skipped", file, index, id);
            return null;
        }

        return block.get();
    }

    private static JsonArray single(JsonElement element) {
        var array = new JsonArray();
        array.add(element);
        return array;
    }

    /** One parsed file, in its entries' order. */
    public record FileEntries(List<Rule> rules) {}

    record Rule(
        List<Block> blocks,
        List<TagKey<Block>> tags,
        Result result
    ) {}

    record TagRule(
        TagKey<Block> tag,
        Result result
    ) {}

    /** One or more results, chosen by weight. */
    record Result(
        List<Block> choices,
        List<Integer> weights
    ) {

        static final Result FALLBACK_RESULT = new Result(List.of(FALLBACK), List.of(1));

        Block pick(RandomSource random) {
            if (choices.size() == 1) {
                return choices.getFirst();
            }

            int total = 0;

            for (var weight : weights) {
                total += weight;
            }

            var roll = random.nextInt(total);

            for (int i = 0; i < choices.size(); i++) {
                roll -= weights.get(i);

                if (roll < 0) {
                    return choices.get(i);
                }
            }

            return choices.getLast();
        }
    }

    private static final class Table {

        static final Table EMPTY = new Table(Map.of(), List.of());

        private final Map<Block, Result> exact;

        private final List<TagRule> tagged;

        /**
         * Block -> result, filled on first use. A blast asks about the same few blocks millions of times; tags cannot
         * change without a reload, and a reload builds a new table, so a cached answer is never stale. Concurrent
         * because some performance mods tick dimensions on separate threads.
         */
        private final Map<Block, Result> resolved = new ConcurrentHashMap<>();

        Table(Map<Block, Result> exact, List<TagRule> tagged) {
            this.exact = exact;
            this.tagged = tagged;
        }

        Result resultFor(BlockState state) {
            var block = state.getBlock();
            var cached = resolved.get(block);

            if (cached != null) {
                return cached;
            }

            var result = exact.get(block);

            if (result == null) {
                for (var rule : tagged) {
                    if (state.is(rule.tag())) {
                        result = rule.result();
                        break;
                    }
                }
            }

            if (result == null) {
                result = Result.FALLBACK_RESULT;
            }

            resolved.put(block, result);
            return result;
        }
    }
}
