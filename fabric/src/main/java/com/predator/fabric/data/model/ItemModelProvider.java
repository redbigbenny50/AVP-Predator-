package com.predator.fabric.data.model;

import com.predator.common.registry.init.item.PredatorArmorItems;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.init.item.block.PredatorSpawnEggItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class ItemModelProvider extends FabricModelProvider {

    public ItemModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {}

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
        generateStandardItem(generators, PredatorItems.SHURIKEN);
        generateStandardItem(generators, PredatorItems.SMART_DISC);
        generateStandardItem(generators, PredatorItems.PLASMA_SHURIKEN);
        generateStandardItem(generators, PredatorItems.PLASMA_CORE);
        generateStandardItem(generators, PredatorItems.VERITANIUM_SCRAP);
        generateStandardItem(generators, PredatorItems.VERITANIUM_ARROW);

        generateStandardItem(generators, PredatorArmorItems.JUNGLE_PREDATOR_BOOTS);
        generateStandardItem(generators, PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE);
        generateStandardItem(generators, PredatorArmorItems.JUNGLE_PREDATOR_HELMET);
        generateStandardItem(generators, PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS);

        generateStandardItem(generators, PredatorItems.PREDATOR_MUSIC_DISC_1);
        generateStandardItem(generators, PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT);
        generateStandardItem(generators, PredatorItems.MUD_BUCKET);
        generateStandardItem(generators, PredatorItems.CLOAKING_DEVICE);

        generateHandheldItem(generators, PredatorItems.VERITANIUM_AXE);
        generateHandheldItem(generators, PredatorItems.VERITANIUM_HOE);
        generateHandheldItem(generators, PredatorItems.VERITANIUM_PICKAXE);
        generateHandheldItem(generators, PredatorItems.VERITANIUM_SHOVEL);
        generateHandheldItem(generators, PredatorItems.VERITANIUM_SWORD);
        generateHandheldItem(generators, PredatorItems.PLASMA_SWORD);

        generateStandardItem(generators, PredatorItems.VERITANIUM_SHARD);
        generateStandardItem(generators, PredatorItems.NET);

        // ⚠ COMBI_STICK and GAUNTLET are NOT generated. They ship hand-written models with
        // "parent": "builtin/entity", which tells vanilla to build NOTHING and hand rendering to the registered
        // item renderer. generateStandardItem would overwrite them with item/generated sprite models, and then
        // vanilla's baked sprite AND the geo model both draw — which is the jumbled mess.
        // ⚠ NOT generated — the gauntlet ships a hand-written models/item/gauntlet.json carrying only display
        // transforms, because its geometry comes from the geo models and datagen would overwrite it with a sprite.
        generateStandardItem(generators, PredatorItems.VERITANIUM_DART);
        generateStandardItem(generators, PredatorItems.FIRE_PELLET);

        PredatorSpawnEggItems.REGISTRY.getAll()
            .forEach(spawnEggItem -> generateStandardItem(generators, spawnEggItem.get()));
    }

    private void generateHandheldItem(ItemModelGenerators generators, Supplier<? extends Item> itemSupplier) {
        generateHandheldItem(generators, itemSupplier.get());
    }

    private void generateHandheldItem(ItemModelGenerators generators, Item item) {
        generateStandardItem(generators, item, ModelTemplates.FLAT_HANDHELD_ITEM);
    }

    private void generateStandardItem(ItemModelGenerators generators, Supplier<? extends Item> itemSupplier) {
        generateStandardItem(generators, itemSupplier.get());
    }

    private void generateStandardItem(ItemModelGenerators generators, Item item) {
        generateStandardItem(generators, item, ModelTemplates.FLAT_ITEM);
    }

    private void generateStandardItem(ItemModelGenerators generators, Item item, ModelTemplate modelTemplate) {
        generators.generateFlatItem(item, modelTemplate);
    }

    @Override
    public @NotNull String getName() {
        return "Item Model Definitions";
    }
}
