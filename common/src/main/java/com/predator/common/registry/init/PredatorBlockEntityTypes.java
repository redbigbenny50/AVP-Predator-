package com.predator.common.registry.init;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.block.entity.GauntletBlockEntity;
import com.predator.common.gameplay.block.entity.SkinnedCorpseBlockEntity;
import com.predator.common.gameplay.block.entity.TripMineBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public class PredatorBlockEntityTypes {

    private static final BLibRegistry<BlockEntityType<?>> REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.BLOCK_ENTITY_TYPE);

    public static final BLibHolder<BlockEntityType<TripMineBlockEntity>> TRIP_MINE = create(
        "trip_mine",
        () -> BlockEntityType.Builder.of(TripMineBlockEntity::new, PredatorBlocks.TRIP_MINE_BLOCK.get())
    );

    public static final BLibHolder<BlockEntityType<GauntletBlockEntity>> GAUNTLET = create(
        "gauntlet",
        () -> BlockEntityType.Builder.of(GauntletBlockEntity::new, PredatorBlocks.GAUNTLET_BLOCK.get())
    );

    public static final BLibHolder<BlockEntityType<SkinnedCorpseBlockEntity>> SKINNED_CORPSE = create(
        "skinned_corpse",
        () -> BlockEntityType.Builder.of(SkinnedCorpseBlockEntity::new, PredatorBlocks.SKINNED_CORPSE.get())
    );

    private static <T extends BlockEntity> BLibHolder<BlockEntityType<T>> create(
        String path,
        Supplier<BlockEntityType.Builder<T>> builder
    ) {
        return REGISTRY.createHolder(path, () -> builder.get().build(null));
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
