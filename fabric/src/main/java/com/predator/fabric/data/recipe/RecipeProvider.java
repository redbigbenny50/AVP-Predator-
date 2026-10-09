package com.predator.fabric.data.recipe;

import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import com.predator.Predator;
import com.predator.fabric.data.recipe.impl.CloakRecipeProvider;
import com.predator.fabric.data.recipe.impl.MiscellaneousRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeOutput;

import java.util.concurrent.CompletableFuture;

public class RecipeProvider extends FabricRecipeProvider {

    public RecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void buildRecipes(RecipeOutput recipeOutput) {
        var builder = RecipeBuilder.with(Predator.MOD, recipeOutput, this::withConditions);
        MiscellaneousRecipeProvider.provide(builder);
        CloakRecipeProvider.provide(builder);
    }

}
