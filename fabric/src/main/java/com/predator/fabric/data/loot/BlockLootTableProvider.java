package com.predator.fabric.data.loot;

import com.predator.common.registry.init.PredatorBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class BlockLootTableProvider extends FabricBlockLootTableProvider {

    private static final Set<Block> TOUCHED_ENTRIES = new HashSet<>();

    public BlockLootTableProvider(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        generateSelfDrops();
    }

    private void generateSelfDrops() {
        dropSelf(PredatorBlocks.TRIP_MINE_BLOCK);
        // ⚠ The placed gauntlet drops NOTHING as loot: mining it spills the contents from the block entity and the
        // gauntlet itself is destroyed ([stated] "it drops the items and gauntlet breaks. it doesnt drop itself").
        add(PredatorBlocks.GAUNTLET_BLOCK, block -> noDrop());
        // The skinned corpse: [stated] "if you break them down they should drop 12 rotten flesh and 6 bones" — on top
        // of
        // its CONTENTS, which spill from the block entity as before. Broken or blown up, it drops both. ⚠ Not when it
        // falls to new ground (it is moved, not broken) and not when it is lost to the void — neither runs the loot.
        add(
            PredatorBlocks.SKINNED_CORPSE,
            block -> LootTable.lootTable()
                .withPool(fixed(net.minecraft.world.item.Items.ROTTEN_FLESH, CORPSE_ROTTEN_FLESH))
                .withPool(fixed(net.minecraft.world.item.Items.BONE, CORPSE_BONES))
        );
    }

    /** What a broken skinned corpse leaves of the body itself. */
    private static final int CORPSE_ROTTEN_FLESH = 12;

    private static final int CORPSE_BONES = 6;

    /** One pool that always drops exactly {@code count} of {@code item}. */
    private static net.minecraft.world.level.storage.loot.LootPool.Builder fixed(ItemLike item, int count) {
        return net.minecraft.world.level.storage.loot.LootPool.lootPool()
            .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1))
            .add(
                net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(item)
                    .apply(
                        net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(
                            net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(count)
                        )
                    )
            );
    }

    public void add(Supplier<? extends Block> blockSupplier, Function<Block, LootTable.Builder> factory) {
        var block = blockSupplier.get();
        add(block, factory);
        TOUCHED_ENTRIES.add(block);
    }

    public void dropOther(Supplier<? extends Block> blockSupplier, Supplier<? extends ItemLike> itemLikeSupplier) {
        var block = blockSupplier.get();
        dropOther(block, itemLikeSupplier.get());
        TOUCHED_ENTRIES.add(block);
    }

    public void dropSelf(Supplier<? extends Block> blockSupplier) {
        var block = blockSupplier.get();
        dropSelf(block);
        TOUCHED_ENTRIES.add(block);
    }
}
