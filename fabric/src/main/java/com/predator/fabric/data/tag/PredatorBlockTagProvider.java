package com.predator.fabric.data.tag;

import com.predator.common.registry.init.PredatorBlocks;
import com.predator.common.registry.tag.PredatorBlockTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;

import java.util.concurrent.CompletableFuture;

public class PredatorBlockTagProvider extends FabricTagProvider.BlockTagProvider {

    public PredatorBlockTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, completableFuture);
    }

    /** A block tag in another mod's namespace, built from strings so that mod's classes are never touched. */
    private static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> foreignBlockTag(String namespace, String path) {
        return net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path)
        );
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_PICKAXE)
            .add(
                PredatorBlocks.TRIP_MINE_BLOCK.get(),
                PredatorBlocks.GAUNTLET_BLOCK.get()
            );

        // ⚠ The placed gauntlet takes ANY pickaxe: hardness 40 is the whole gate ([stated] ~10 s with iron).
        // ⚠ EMPTY ON PURPOSE — the blacklist's code rules already cover metal, containers, doors and avp_human. This is
        // the place for anything else inside the hardness window a yautja should leave alone: one .add(...) each.
        var unbreakable = getOrCreateTagBuilder(PredatorBlockTags.YAUTJA_UNBREAKABLE);

        // Oct 11 - [stated] the village blocks are protected: no yautja digs through its own village.
        PredatorBlocks.VILLAGE_BLOCKS.forEach(block -> unbreakable.add(block.get()));

        // Mined like stone bricks: any pickaxe. Slabs, stairs and walls join vanilla's shape tags - ⚠ #walls is what
        // makes a wall CONNECT to its neighbours, so leaving it out would give lone posts.
        var pickaxe = getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_PICKAXE);
        PredatorBlocks.VILLAGE_BLOCKS.forEach(block -> pickaxe.add(block.get()));

        // Oct 14 - [stated] "as hard as steel ... high acid resist": iron pickaxe like steel, and acid takes three
        // times
        // longer, the same as avp_human's composite. Both acid tags are named by string so neither sibling mod is
        // needed:
        // without avp_alien / Gigeresque installed, nothing reads them.
        var ironTool = getOrCreateTagBuilder(BlockTags.NEEDS_IRON_TOOL);
        var acidResistant = getOrCreateTagBuilder(foreignBlockTag("avp_human", "acid_resistant_3x"));
        var gigeresqueAcidResistant = getOrCreateTagBuilder(foreignBlockTag("gigeresque", "acid_resistant"));

        PredatorBlocks.VILLAGE_BLOCKS.forEach(block -> {
            ironTool.add(block.get());
            acidResistant.add(block.get());
            gigeresqueAcidResistant.add(block.get());
        });

        var slabs = getOrCreateTagBuilder(BlockTags.SLABS);
        var stairs = getOrCreateTagBuilder(BlockTags.STAIRS);
        var walls = getOrCreateTagBuilder(BlockTags.WALLS);

        PredatorBlocks.VILLAGE_FAMILIES.forEach(family -> {
            slabs.add(family.slab().get());
            stairs.add(family.stairs().get());
            walls.add(family.wall().get());
        });

        getOrCreateTagBuilder(PredatorBlockTags.XENOMORPH_THREAT_BLOCKS)
            .add(PredatorBlocks.GAUNTLET_BLOCK.get());

        getOrCreateTagBuilder(BlockTags.NEEDS_IRON_TOOL)
            .add(
                PredatorBlocks.TRIP_MINE_BLOCK.get()
            );
    }
}
