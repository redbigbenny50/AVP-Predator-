package com.predator.fabric.data.lang.en_us.provider;

import com.predator.Predator;
import com.predator.common.registry.init.item.PredatorArmorItems;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.init.item.block.PredatorSpawnEggItems;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnUsItemProvider {

    private static final HashSet<Item> TOUCHED_ENTRIES = new HashSet<>();

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        // Combat Items
        addItem(builder, PredatorItems.SHURIKEN, "Shuriken");
        addItem(builder, PredatorItems.SMART_DISC, "Smart Disc");

        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_BOOTS, "Predator Boots");
        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE, "Predator Chestplate");
        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_HELMET, "Predator Helmet");
        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS, "Predator Leggings");

        // Ingredient Items
        addItem(builder, PredatorItems.PREDATOR_MUSIC_DISC_1, "Music Disc");
        addItem(builder, PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT, "Disc Fragment");
        builder.add(PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT.get().getDescriptionId() + ".desc", "Music Disc - Hunter");
        addItem(builder, PredatorItems.VERITANIUM_SHARD, "Veritanium Shard");

        // Tools & Utilities Items
        addItem(builder, PredatorItems.MUD_BUCKET, "Mud Bucket");
        addItem(builder, PredatorItems.VERITANIUM_AXE, "Veritanium Axe");
        addItem(builder, PredatorItems.VERITANIUM_HOE, "Veritanium Hoe");
        addItem(builder, PredatorItems.VERITANIUM_PICKAXE, "Veritanium Pickaxe");
        addItem(builder, PredatorItems.VERITANIUM_SHOVEL, "Veritanium Shovel");
        addItem(builder, PredatorItems.VERITANIUM_SWORD, "Veritanium Sword");

        // Spawn Egg Items
        addItem(builder, PredatorSpawnEggItems.YAUTJA_SPAWN_EGG, "Yautja Spawn Egg");

        var missingEntries = PredatorItems.REGISTRY.computeMissingEntries(TOUCHED_ENTRIES);
        var filteredMissingEntries = missingEntries
            .stream()
            .filter(item -> !(item instanceof BlockItem))
            .toList();

        if (!filteredMissingEntries.isEmpty()) {
            var unhandledBlocksStrings = String.join("\n", filteredMissingEntries.stream().map(Item::getDescriptionId).toList());

            Predator.LOGGER.error(
                "Detected {} unhandled entries. Entries:\n{}",
                filteredMissingEntries.size(),
                unhandledBlocksStrings
            );

            throw new IllegalStateException(
                "Item translation did not complete successfully - there are unhandled items that need to be handled."
            );
        }
    };

    private static void addItem(
        FabricLanguageProvider.TranslationBuilder translationBuilder,
        Supplier<? extends Item> itemSupplier,
        String value
    ) {
        addItem(translationBuilder, itemSupplier.get(), value);
    }

    private static void addItem(FabricLanguageProvider.TranslationBuilder translationBuilder, Item item, String value) {
        TOUCHED_ENTRIES.add(item);
        translationBuilder.add(item, value);
    }

}
