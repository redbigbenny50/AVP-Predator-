package com.predator.client.vision;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * What a block is worth as a heat source, on the same 0..1+ scale the thermal shader's gradient uses — 0 is ambient,
 * 0.5 lands mid-gradient, 1.0 is the top of the visible range.
 * <p>
 * This is deliberately <em>not</em> derived from light emission. Light and heat are different things: a sea lantern is
 * bright and cold, a lit furnace is hot behind an iron door. Deriving heat from the light level would make every
 * glowing block warm and give no way to say otherwise, which is exactly the distinction the design calls for. So the
 * rating is authored per block, and this map doubles as the whitelist and the blacklist — anything absent contributes
 * nothing.
 * <p>
 * ⚠ Blocks with a {@code lit} property (furnace, blast furnace, smoker, campfire, redstone lamp) only count while lit.
 * That covers the on/off case for every one of them without enumerating states by hand.
 * <p>
 * ⏭ Ratings live in code for now. Moving them to a datapack later is a change to this class alone — nothing else reads
 * the table — and would let modpack blocks join without a code change.
 */
public final class PredatorHeatSources {

    // Ratings follow a rule about CONSEQUENCE rather than brightness, because that is what a heat overlay is for:
    // it should tell you at a glance what will hurt you. A torch and a furnace emit the same light as lava and are
    // plainly hot, but they will not burn you, so they read warm rather than dangerous.
    //
    // The scale is a position on the thermal gradient: 0.25 blue, 0.5 green, 0.75 yellow, 1.0 red.

    /** Sets you on fire. Red — read it as a warning, not a temperature. */
    private static final float HEAT_IGNITES = 1.0F;

    /** Damages on contact but will not set you alight. Red-orange. */
    private static final float HEAT_BURNS = 0.85F;

    /** Obviously, unmistakably hot, and harmless to stand next to. Yellow. */
    private static final float HEAT_HOT = 0.75F;

    private static final Map<Block, Float> RATINGS = new HashMap<>();

    static {
        // Ignites you.
        put(Blocks.LAVA, HEAT_IGNITES);
        put(Blocks.LAVA_CAULDRON, HEAT_IGNITES);
        put(Blocks.FIRE, HEAT_IGNITES);
        put(Blocks.SOUL_FIRE, HEAT_IGNITES);
        put(Blocks.CAMPFIRE, HEAT_IGNITES);
        put(Blocks.SOUL_CAMPFIRE, HEAT_IGNITES);

        // Hurts on contact, no fire.
        put(Blocks.MAGMA_BLOCK, HEAT_BURNS);

        // Hot and harmless.
        put(Blocks.FURNACE, HEAT_HOT);
        put(Blocks.BLAST_FURNACE, HEAT_HOT);
        put(Blocks.SMOKER, HEAT_HOT);
        put(Blocks.TORCH, HEAT_HOT);
        put(Blocks.WALL_TORCH, HEAT_HOT);
        put(Blocks.SOUL_TORCH, HEAT_HOT);
        put(Blocks.SOUL_WALL_TORCH, HEAT_HOT);
        put(Blocks.LANTERN, HEAT_HOT);
        put(Blocks.SOUL_LANTERN, HEAT_HOT);
        put(Blocks.JACK_O_LANTERN, HEAT_HOT);
        put(Blocks.CANDLE, HEAT_HOT);

        // Deliberately absent, and worth stating so nobody "fixes" it later: glowstone, sea lantern, redstone lamp,
        // shroomlight, glow lichen, froglights, ochre/verdant/pearlescent — all bright, none hot.
    }

    private PredatorHeatSources() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the heat rating for this state, or 0 when it is not a heat source (or is a switchable one that is
     *         currently off).
     */
    public static float heatOf(BlockState state) {
        var rating = RATINGS.get(state.getBlock());

        if (rating == null) {
            return 0.0F;
        }

        // Switchable sources: cold until lit. Blocks without the property are always on.
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            return 0.0F;
        }

        return rating;
    }

    public static boolean isHeatSource(BlockState state) {
        return heatOf(state) > 0.0F;
    }

    private static void put(Block block, float heat) {
        RATINGS.put(block, heat);
    }
}
