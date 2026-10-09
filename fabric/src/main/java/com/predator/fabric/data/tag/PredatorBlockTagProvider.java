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
        getOrCreateTagBuilder(PredatorBlockTags.YAUTJA_UNBREAKABLE);

        getOrCreateTagBuilder(PredatorBlockTags.XENOMORPH_THREAT_BLOCKS)
            .add(PredatorBlocks.GAUNTLET_BLOCK.get());

        getOrCreateTagBuilder(BlockTags.NEEDS_IRON_TOOL)
            .add(
                PredatorBlocks.TRIP_MINE_BLOCK.get()
            );
    }
}
