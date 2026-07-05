package com.predator.fabric.data.recipe.impl;

import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class MiscellaneousRecipeProvider {

    public static void provide(RecipeBuilder builder) {
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(9, PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT)
            .into(1, PredatorItems.PREDATOR_MUSIC_DISC_1);

        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(1, Blocks.MUD)
            .requires(1, Items.BUCKET)
            .into(1, PredatorItems.MUD_BUCKET);

        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_water_bucket")
            .requires(1, Items.WATER_BUCKET)
            .requires(1, Items.DIRT)
            .into(1, PredatorItems.MUD_BUCKET);
    }
}
