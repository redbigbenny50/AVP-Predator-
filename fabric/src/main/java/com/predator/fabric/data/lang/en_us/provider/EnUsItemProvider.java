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
        addItem(builder, PredatorItems.PLASMA_SHURIKEN, "Plasma Shuriken");
        addItem(builder, PredatorItems.PLASMA_CORE, "Plasma Core");
        addItem(builder, PredatorItems.HAND_CASTER, "Hand Caster");
        addItem(builder, PredatorItems.VERITANIUM_SCRAP, "Veritanium Scrap");
        builder.add("item.avp_predator.hand_caster.shots", "Shots: %s / %s");
        builder.add("item.avp_predator.hand_caster.no_core", "No plasma core to reload");
        addItem(builder, PredatorItems.PLASMA_SWORD, "Plasma Sword");
        addItem(builder, PredatorItems.VERITANIUM_ARROW, "Veritanium Arrow");

        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_BOOTS, "Predator Boots");
        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE, "Predator Chestplate");
        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_HELMET, "Predator Helmet");
        addItem(builder, PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS, "Predator Leggings");

        // Ingredient Items
        addItem(builder, PredatorItems.PREDATOR_MUSIC_DISC_1, "Music Disc");
        addItem(builder, PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT, "Disc Fragment");
        builder.add(PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT.get().getDescriptionId() + ".desc", "Music Disc - Hunter");
        addItem(builder, PredatorItems.VERITANIUM_SHARD, "Veritanium Shard");
        addItem(builder, PredatorItems.YAUTJA_BLOOD_BOTTLE, "Bottle of Yautja Blood");
        addItem(builder, PredatorItems.PRED_GRENADE_EXPLOSIVE, "Explosive Yautja Grenade");
        addItem(builder, PredatorItems.PRED_GRENADE_FIRE, "Fire Yautja Grenade");
        addItem(builder, PredatorItems.PRED_GRENADE_STICKY, "Sticky Yautja Grenade");
        addItem(builder, PredatorItems.PRED_GRENADE_FREEZE, "Freeze Yautja Grenade");
        addItem(builder, PredatorItems.PRED_GRENADE_IRRADIATED, "Irradiated Yautja Grenade");
        addItem(builder, PredatorItems.NET, "Net");
        addItem(builder, PredatorItems.COMBI_STICK, "Combi Stick");
        addItem(builder, PredatorItems.WHIP, "Veritanium Whip");
        addItem(builder, PredatorItems.CHAIN_WHIP, "Chain Whip");
        addItem(builder, PredatorItems.VERITANIUM_BOW, "Veritanium Bow");
        addItem(builder, PredatorItems.PLASMA_BOW, "Plasma Bow");
        addItem(builder, PredatorItems.BATTLEAXE, "Battleaxe");
        addItem(builder, PredatorItems.GAUNTLET, "Wrist Gauntlet");

        builder.add("bossbar.avp_predator.net_struggle", "Struggle!");
        addItem(builder, PredatorItems.VERITANIUM_DART, "Veritanium Dart");
        addItem(builder, PredatorItems.FIRE_PELLET, "Fire Pellet");

        // Tools & Utilities Items
        addItem(builder, PredatorItems.MUD_BUCKET, "Mud Bucket");
        addItem(builder, PredatorItems.CLOAKING_DEVICE, "Cloaking Device");

        // Cloak action-bar lines
        builder.add("message.avp_predator.cloak.activated", "Cloak activated");
        builder.add("message.avp_predator.cloak.deactivated", "Cloak deactivated");
        builder.add("message.avp_predator.cloak.overloaded", "Cloak overloaded");
        builder.add("effect.avp_predator.cloak", "Cloaked");
        builder.add("effect.avp_predator.roar_stun", "Stunned");
        builder.add("effect.avp_predator.adrenaline_rush", "Adrenaline Rush");
        builder.add("subtitles.cloak.cloak_on", "Cloak engages");
        builder.add("subtitles.cloak.cloak_off", "Cloak drops");
        builder.add("subtitles.cloak.cloak_wet_loop", "Cloak arcs in water");
        addItem(builder, PredatorItems.VERITANIUM_AXE, "Veritanium Axe");
        addItem(builder, PredatorItems.VERITANIUM_HOE, "Veritanium Hoe");
        addItem(builder, PredatorItems.VERITANIUM_PICKAXE, "Veritanium Pickaxe");
        addItem(builder, PredatorItems.VERITANIUM_SHOVEL, "Veritanium Shovel");
        addItem(builder, PredatorItems.VERITANIUM_SWORD, "Veritanium Sword");

        // Spawn Egg Items
        addItem(builder, PredatorSpawnEggItems.YAUTJA_SPAWN_EGG, "Jungle Yautja Spawn Egg");

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
