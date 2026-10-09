package com.predator.common.registry.init;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.block.GauntletBlock;
import com.predator.common.gameplay.block.SkinnedCorpseBlock;
import com.predator.common.gameplay.block.TripMineBlock;
import com.predator.common.gameplay.block.property.PredatorBlockProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class PredatorBlocks {

    public static final BLibRegistry<Block> REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.BLOCK);

    public static final BLibHolder<Block> TRIP_MINE_BLOCK = create(
        "trip_mine",
        () -> new TripMineBlock(PredatorBlockProperties.TRIP_MINE.build().noOcclusion())
    );

    /** A gauntlet set down on the ground. No BlockItem: GauntletItem.useOn places it. */
    public static final BLibHolder<Block> GAUNTLET_BLOCK = create(
        "gauntlet",
        () -> new GauntletBlock(
            PredatorBlockProperties.GAUNTLET.build().noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
        )
    );

    /**
     * A skinned body left by a yautja — a 45-slot container. No BlockItem: it is only ever placed by a kill or by the
     * Hunter (and by {@code /avp_predator debug corpse} for testing). Immovable, so a piston cannot dupe its contents.
     */
    public static final BLibHolder<Block> SKINNED_CORPSE = create(
        "skinned_corpse",
        () -> new SkinnedCorpseBlock(
            PredatorBlockProperties.SKINNED_CORPSE.build().noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
        )
    );

    /**
     * The wounded Hunter's blood trail. Full-bright but no light ([stated] "a glow layer so its bright but it doesnt
     * give off light itself"), no collision, breaks at a touch, drops nothing, and is swept away by a piston or water.
     */
    public static final BLibHolder<com.predator.common.gameplay.block.YautjaBloodBlock> YAUTJA_BLOOD = create(
        "yautja_blood",
        () -> new com.predator.common.gameplay.block.YautjaBloodBlock(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GREEN)
                .noCollission()
                .noOcclusion()
                .instabreak()
                .replaceable()
                .noLootTable()
                .lightLevel(state -> 0)
                .emissiveRendering((state, level, pos) -> true)
                .sound(net.minecraft.world.level.block.SoundType.HONEY_BLOCK)
                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
        )
    );

    private static <T extends Block> BLibHolder<T> create(String path, Supplier<T> blockSupplier) {
        return REGISTRY.createHolder(path, blockSupplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
