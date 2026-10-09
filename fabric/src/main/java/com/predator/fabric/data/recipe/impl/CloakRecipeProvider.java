package com.predator.fabric.data.recipe.impl;

import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Items;

/**
 * Crafting for the cloaking device.
 * <p>
 * Generated as an ordinary shaped recipe JSON, which means it is fully datapackable: a pack can override
 * {@code data/avp_predator/recipe/cloaking_device.json} to change or remove it, and nothing here is hardcoded in Java
 * at runtime. That is inherent to how recipes work in 1.21.1 — no special support was needed to make it overridable.
 * <h2>The cost</h2> Two nether stars and a conduit is deliberately steep. The cloak is the strongest utility item in
 * the mod — it makes you unacquirable by every mob that hunts by sight — so it sits behind two wither fights and an
 * ocean monument rather than behind a mining trip.
 */
public class CloakRecipeProvider {

    public static void provide(RecipeBuilder builder) {
        builder.shaped()
            .withCategory(RecipeCategory.TOOLS)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .define('N', Items.NETHER_STAR)
            .define('C', Items.CONDUIT)
            .pattern("VVV")
            .pattern("NCN")
            .pattern("VVV")
            .into(1, PredatorItems.CLOAKING_DEVICE);
    }
}
