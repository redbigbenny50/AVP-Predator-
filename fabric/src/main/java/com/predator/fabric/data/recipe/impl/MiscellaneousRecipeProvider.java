package com.predator.fabric.data.recipe.impl;

import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class MiscellaneousRecipeProvider {

    public static void provide(RecipeBuilder builder) {
        // ⚠ His layout exactly: an iron ingot in the CENTRE with a veritanium shard north, south, east and west.
        // The corners stay empty, so the shape reads as a throwing star rather than a full ring.
        // ⚠ His layout exactly: a checker of veritanium shards, corners and centre filled, edges empty — it reads
        // as the mesh it makes. Yields 2.
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .pattern("V V")
            .pattern(" V ")
            .pattern("V V")
            .into(2, PredatorItems.NET);

        // [stated] "veritanium arrow ... crafted with a veritanium shard at the top ... then a stick in the middle then
        // a
        // feather, it makes 4 arrows" — [stated] "should mimic the vanilla recipe the tip is just a different
        // material".
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .define('S', Items.STICK)
            .define('F', Items.FEATHER)
            .pattern("V")
            .pattern("S")
            .pattern("F")
            .into(4, PredatorItems.VERITANIUM_ARROW);

        // [stated] "it can be crafted using veritanium shards surrounding a plasma core ... it will give you 8".
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .define('C', PredatorItems.PLASMA_CORE)
            .pattern("VVV")
            .pattern("VCV")
            .pattern("VVV")
            .into(8, PredatorItems.PLASMA_SHURIKEN);

        // [stated] "a plasma core ... you get from putting a gauntlet or hand caster ... in a crafting table". The hand
        // caster's own recipe arrives with the hand caster.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(1, PredatorItems.GAUNTLET)
            .into(1, PredatorItems.PLASMA_CORE);

        // ---------------------------------------------------------------- veritanium scrap
        // [stated] "its made buy 9 veritanium shards in a crafting table".
        builder.shaped()
            .withCategory(RecipeCategory.MISC)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .pattern("VVV")
            .pattern("VVV")
            .pattern("VVV")
            .into(1, PredatorItems.VERITANIUM_SCRAP);

        // [stated] "also you can use it to craft 9 veritanium shards". ⚠ Named, or it would claim the id
        // "veritanium_shard" — the shard's own name.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_veritanium_scrap")
            .requires(1, PredatorItems.VERITANIUM_SCRAP)
            .into(9, PredatorItems.VERITANIUM_SHARD);

        // [stated] "fill the top row with veritanium scrap and then two sticks in the center and bottom center (like
        // the
        // pick recipe) and you get the veritanium pick".
        builder.shaped()
            .withCategory(RecipeCategory.TOOLS)
            .define('X', PredatorItems.VERITANIUM_SCRAP)
            .define('#', Items.STICK)
            .pattern("XXX")
            .pattern(" # ")
            .pattern(" # ")
            .into(1, PredatorItems.VERITANIUM_PICKAXE);

        // [stated] "one veritanium scrap in the center top row then two sticks under it in the same column and you get
        // a
        // veritanium shovel". One column wide, so any column works, as vanilla's shovel does.
        builder.shaped()
            .withCategory(RecipeCategory.TOOLS)
            .define('X', PredatorItems.VERITANIUM_SCRAP)
            .define('#', Items.STICK)
            .pattern("X")
            .pattern("#")
            .pattern("#")
            .into(1, PredatorItems.VERITANIUM_SHOVEL);

        // [stated] "two veritanium scrap in the top left and top center slots then two sticks one in the center and one
        // under that you get the veritanium hoe" — vanilla's hoe shape.
        builder.shaped()
            .withCategory(RecipeCategory.TOOLS)
            .define('X', PredatorItems.VERITANIUM_SCRAP)
            .define('#', Items.STICK)
            .pattern("XX")
            .pattern(" #")
            .pattern(" #")
            .into(1, PredatorItems.VERITANIUM_HOE);

        // ---------------------------------------------------------------- weapon breakdown
        // [stated] "the weapons we want to make break downable into shards or scrap base on the weapons and size" /
        // "crafting table i think is best" / "no difference for worn" / "gauntlet and caster return their core other
        // things
        // made with a core like the plasma shuriken is only the shards".
        // The weapon ALONE in the grid. Worn or not, the same return (a plain ingredient matches the item, not its
        // damage).
        // ⚠ A recipe has ONE result, so each returns ALL SCRAP when its value is a whole number of scrap, ALL SHARDS
        // otherwise. ⚠ Crafted items give back LESS than they cost, so nothing can be crafted and broken down for
        // profit.
        // Each has its own name ("<result>_from_<weapon>") or it would collide with the scrap/shard recipes.

        // veritanium_pickaxe: 13 veritanium shard — costs 3 scrap (27): about half back.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_veritanium_pickaxe")
            .requires(1, PredatorItems.VERITANIUM_PICKAXE)
            .into(13, PredatorItems.VERITANIUM_SHARD);

        // veritanium_hoe: 1 veritanium scrap — costs 2 scrap (18): half back.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_veritanium_hoe")
            .requires(1, PredatorItems.VERITANIUM_HOE)
            .into(1, PredatorItems.VERITANIUM_SCRAP);

        // veritanium_shovel: 4 veritanium shard — costs 1 scrap (9): about half back.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_veritanium_shovel")
            .requires(1, PredatorItems.VERITANIUM_SHOVEL)
            .into(4, PredatorItems.VERITANIUM_SHARD);

        // chain_whip: 1 veritanium shard — costs 3 shards: a third back.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_chain_whip")
            .requires(1, PredatorItems.CHAIN_WHIP)
            .into(1, PredatorItems.VERITANIUM_SHARD);

        // shuriken: 1 veritanium shard — costs 1 shard + a quarter of an iron ingot: the shard back, the iron lost.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_shuriken")
            .requires(1, PredatorItems.SHURIKEN)
            .into(1, PredatorItems.VERITANIUM_SHARD);

        // plasma_shuriken: 1 veritanium shard — costs 1 shard + an eighth of a plasma core: the shard back, no core.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_plasma_shuriken")
            .requires(1, PredatorItems.PLASMA_SHURIKEN)
            .into(1, PredatorItems.VERITANIUM_SHARD);

        // battleaxe: 22 veritanium shard — drop-only; large, two-handed.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_battleaxe")
            .requires(1, PredatorItems.BATTLEAXE)
            .into(22, PredatorItems.VERITANIUM_SHARD);

        // combi_stick: 2 veritanium scrap — drop-only; large, two-handed.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_combi_stick")
            .requires(1, PredatorItems.COMBI_STICK)
            .into(2, PredatorItems.VERITANIUM_SCRAP);

        // veritanium_axe: 13 veritanium shard — drop-only; medium, axe-sized like the pickaxe.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_veritanium_axe")
            .requires(1, PredatorItems.VERITANIUM_AXE)
            .into(13, PredatorItems.VERITANIUM_SHARD);

        // veritanium_sword: 12 veritanium shard — drop-only; medium.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_veritanium_sword")
            .requires(1, PredatorItems.VERITANIUM_SWORD)
            .into(12, PredatorItems.VERITANIUM_SHARD);

        // plasma_sword: 12 veritanium shard — drop-only; medium (no core).
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_plasma_sword")
            .requires(1, PredatorItems.PLASMA_SWORD)
            .into(12, PredatorItems.VERITANIUM_SHARD);

        // veritanium_bow: 1 veritanium scrap — drop-only; medium.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_veritanium_bow")
            .requires(1, PredatorItems.VERITANIUM_BOW)
            .into(1, PredatorItems.VERITANIUM_SCRAP);

        // plasma_bow: 1 veritanium scrap — drop-only; medium (no core).
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_plasma_bow")
            .requires(1, PredatorItems.PLASMA_BOW)
            .into(1, PredatorItems.VERITANIUM_SCRAP);

        // smart_disc: 6 veritanium shard — drop-only; small.
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_smart_disc")
            .requires(1, PredatorItems.SMART_DISC)
            .into(6, PredatorItems.VERITANIUM_SHARD);

        // [stated] "... or hand caster ... you will get a plasma core". ⚠ A second recipe for the SAME result needs its
        // own name, or the two would collide on the id "plasma_core".
        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .withCustomName(name -> name + "_from_hand_caster")
            .requires(1, PredatorItems.HAND_CASTER)
            .into(1, PredatorItems.PLASMA_CORE);

        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .define('I', Items.IRON_INGOT)
            .pattern(" V ")
            .pattern("VIV")
            .pattern(" V ")
            .into(4, PredatorItems.SHURIKEN);

        // The chain whip: three veritanium shards in a column — [stated] "3 veritanium shards in a column in a
        // crafting table". Shapeless would have collided with nothing, but shaped keeps it reading as the chain it
        // makes, and leaves the other two columns free for a future variant.
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .pattern("V")
            .pattern("V")
            .pattern("V")
            .into(1, PredatorItems.CHAIN_WHIP);

        // ⚠ NO RECIPE FOR THE VERITANIUM WHIP. [stated] "veritanium whip shouldnt be craftable only the chain whip".
        // Its
        // generated recipe and unlock advancement are listed for deletion (DELETIONS.txt) — datagen does not remove
        // them.

        // Two shards stacked over an iron ingot — a column, so it reads as the dart it makes.
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .define('I', Items.IRON_INGOT)
            .pattern("V")
            .pattern("V")
            .pattern("I")
            .into(8, PredatorItems.VERITANIUM_DART);

        // Shard, fire charge, shard — the same column as the dart with the payload in the middle.
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('V', PredatorItems.VERITANIUM_SHARD)
            .define('F', Items.FIRE_CHARGE)
            .pattern("V")
            .pattern("F")
            .pattern("V")
            .into(8, PredatorItems.FIRE_PELLET);

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
