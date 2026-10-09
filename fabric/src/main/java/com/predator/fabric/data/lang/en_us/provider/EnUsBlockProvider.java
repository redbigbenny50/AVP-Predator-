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
