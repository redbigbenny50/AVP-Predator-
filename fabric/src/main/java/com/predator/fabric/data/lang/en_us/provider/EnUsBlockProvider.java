package com.predator.fabric.data.lang.en_us.provider;

import com.predator.Predator;
import com.predator.common.registry.init.PredatorBlocks;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnUsBlockProvider {

    private static final HashSet<Block> TOUCHED_ENTRIES = new HashSet<>();

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        addBlock(builder, PredatorBlocks.TRIP_MINE_BLOCK, "Trip Mine");
        addBlock(builder, PredatorBlocks.GAUNTLET_BLOCK, "Wrist Gauntlet");
        addBlock(builder, PredatorBlocks.SKINNED_CORPSE, "Skinned Corpse");
        addBlock(builder, PredatorBlocks.YAUTJA_BLOOD, "Yautja Blood");

        // Oct 11 - the village stone set.
        addFamily(builder, PredatorBlocks.RED_VILLAGE_STONE, "Red Village Stone", "Red Village Stone");
        addFamily(builder, PredatorBlocks.RED_VILLAGE_BRICKS, "Red Village Bricks", "Red Village Brick");
        addFamily(builder, PredatorBlocks.RED_VILLAGE_SMALL_BRICKS, "Red Village Small Bricks", "Red Village Small Brick");
        addFamily(builder, PredatorBlocks.RED_VILLAGE_ROUGH_BRICKS, "Red Village Rough Bricks", "Red Village Rough Brick");
        addFamily(builder, PredatorBlocks.RED_VILLAGE_TILES, "Red Village Tiles", "Red Village Tile");
        addFamily(builder, PredatorBlocks.RED_VILLAGE_POLISHED, "Polished Red Village Stone", "Polished Red Village Stone");

        for (var i = 0; i < PredatorBlocks.GLYPHS.size(); i++) {
            addBlock(builder, PredatorBlocks.GLYPHS.get(i), "Red Village Glyph Stone " + (i + 1));
        }

        // The grey village stone.
        addFamily(builder, PredatorBlocks.GREY_VILLAGE_STONE, "Grey Village Stone", "Grey Village Stone");
        addFamily(builder, PredatorBlocks.GREY_VILLAGE_POLISHED, "Polished Grey Village Stone", "Polished Grey Village Stone");
        addFamily(builder, PredatorBlocks.GREY_VILLAGE_BRICKS, "Grey Village Bricks", "Grey Village Brick");
        addFamily(builder, PredatorBlocks.GREY_VILLAGE_SMALL_BRICKS, "Grey Village Small Bricks", "Grey Village Small Brick");
        addFamily(builder, PredatorBlocks.GREY_VILLAGE_ROUGH_BRICKS, "Grey Village Rough Bricks", "Grey Village Rough Brick");
        addFamily(builder, PredatorBlocks.GREY_VILLAGE_TILES, "Grey Village Tiles", "Grey Village Tile");

        for (var i = 0; i < PredatorBlocks.CHISELED.size(); i++) {
            var name = PredatorBlocks.CHISELED_NAMES.get(i);
            addBlock(
                builder,
                PredatorBlocks.CHISELED.get(i),
                "Chiseled Grey Village Stone (" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + ")"
            );
        }

        addBlock(builder, PredatorBlocks.GREY_VILLAGE_PILLAR, "Grey Village Pillar");

        for (var i = 0; i < PredatorBlocks.BORDERS.size(); i++) {
            var name = PredatorBlocks.BORDER_NAMES.get(i);
            addBlock(
                builder,
                PredatorBlocks.BORDERS.get(i),
                "Grey Village " + Character.toUpperCase(name.charAt(0)) + name.substring(1) + " Border"
            );
        }

        var missingEntries = PredatorBlocks.REGISTRY.computeMissingEntries(TOUCHED_ENTRIES);

        if (!missingEntries.isEmpty()) {
            var unhandledBlocksStrings = String.join("\n", missingEntries.stream().map(Block::getDescriptionId).toList());

            Predator.LOGGER.error(
                "Detected {} unhandled entries. Entries:\n{}",
                missingEntries.size(),
                unhandledBlocksStrings
            );

            throw new IllegalStateException(
                "Block translation did not complete successfully - there are unhandled blocks that need to be handled."
            );
        }
    };

    /** The base under its own name, the variants as "{@code variantName} Slab / Stairs / Wall". */
    private static void addFamily(
        FabricLanguageProvider.TranslationBuilder builder,
        PredatorBlocks.StoneFamily family,
        String baseName,
        String variantName
    ) {
        addBlock(builder, family.base(), baseName);
        addBlock(builder, family.slab(), variantName + " Slab");
        addBlock(builder, family.stairs(), variantName + " Stairs");
        addBlock(builder, family.wall(), variantName + " Wall");
    }

    private static void addBlock(
        FabricLanguageProvider.TranslationBuilder translationBuilder,
        Supplier<? extends Block> blockSupplier,
        String value
    ) {
        addBlock(translationBuilder, blockSupplier.get(), value);
    }

    private static void addBlock(FabricLanguageProvider.TranslationBuilder translationBuilder, Block block, String value) {
        TOUCHED_ENTRIES.add(block);
        translationBuilder.add(block, value);
    }
}
