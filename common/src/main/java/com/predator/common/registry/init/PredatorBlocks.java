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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    // ======================================================================================================== village
    // Oct 11 - the village stone set, for the yautja WORSHIPPER villages ([stated] "these are for the worshipper
    // villages
    // we plan to add", so the ids are plain village_*, not pred_village_*). [stated] "these all come from the red stone
    // which is a recipe of
    // granite andesite and diorite", "these are made from redstone in a stone cutter", 4 red stone per craft, slabs,
    // stairs and walls for every family, and yautja must NOT break them (all in yautja_unbreakable).
    // ⚠ Recipes are hand-written JSON under data/avp_predator/recipe/village/, not datagen. Blockstates and models are
    // hand-written resources, like the other predator blocks.

    /** The base block: granite + andesite + diorite in a crafting table. Everything else cuts from it. */
    public static final StoneFamily RED_VILLAGE_STONE = family("village_red_stone", "village_red_stone");

    public static final StoneFamily RED_VILLAGE_BRICKS = family("village_red_bricks", "village_red_brick");

    public static final StoneFamily RED_VILLAGE_SMALL_BRICKS = family("village_red_small_bricks", "village_red_small_brick");

    public static final StoneFamily RED_VILLAGE_ROUGH_BRICKS = family("village_red_rough_bricks", "village_red_rough_brick");

    public static final StoneFamily RED_VILLAGE_TILES = family("village_red_tiles", "village_red_tile");

    /** Oct 11 - polished red, his {@code village_red_stone_polished}. Also the glyph blocks' top and bottom. */
    public static final StoneFamily RED_VILLAGE_POLISHED = family("village_red_stone_polished", "village_red_stone_polished");

    /**
     * The red glyph blocks, {@code village_red_glyph_1} to {@code _5}. [stated] the glyphs "also will be a glyph per
     * side and two would most likely need to be repeated on one group", with polished red on top and bottom. His 18
     * faces in number order, four to a block (north, east, south, west in the models): 1-4, 5-8, 9-12, 13-16, and the
     * fifth block 17, 18, 17, 18. Each turns to face whoever places it, like the grey chiseled blocks.
     */
    public static final List<BLibHolder<Block>> GLYPHS = createGlyphs(5);

    // Oct 11 - the GREY village stone: [stated] "a grey version of the same stone its recipe is the same but you add
    // cobble to it" - granite + andesite + diorite + cobblestone makes 4, or one red village stone + one cobblestone
    // makes 1. Same families as the red, plus POLISHED ([stated] "rename this to polished and remove pillar" - the
    // pillar texture became the polished block and the pillar itself was dropped), and 14 named chiseled faces in place
    // of the numbered glyphs. Everything cuts from grey stone.

    /** The grey base block. */
    public static final StoneFamily GREY_VILLAGE_STONE = family("village_stone", "village_stone");

    public static final StoneFamily GREY_VILLAGE_POLISHED = family("village_polished", "village_polished");

    public static final StoneFamily GREY_VILLAGE_BRICKS = family("village_bricks", "village_brick");

    public static final StoneFamily GREY_VILLAGE_SMALL_BRICKS = family("village_small_bricks", "village_small_brick");

    public static final StoneFamily GREY_VILLAGE_ROUGH_BRICKS = family("village_rough_bricks", "village_rough_brick");

    public static final StoneFamily GREY_VILLAGE_TILES = family("village_tiles", "village_tile");

    /**
     * The chiseled grey blocks, {@code village_chiseled_<name>}. [stated] "the chiseled ones are each sides of 1 block
     * 4 per block with the pillar top as the top and bottom" - his 14 faces go four to a block (north, east, south,
     * west in the block models), the pillar-top square on top and bottom, and [stated] "for the two missing just repeat
     * them": the two three-face blocks show their front again on the back.
     * <ul>
     * <li>weapon - axe, spear, sword, shuriken</li>
     * <li>trophy - queen, warden, wither, dragon</li>
     * <li>yautja - mask, yautja, mask, triangle</li>
     * </ul>
     * [stated] "circle is the top and bottom of the chiseled groups" and "the triangle would replace teeth for the
     * yautja group" - so the circle face is every chiseled block's top and bottom, and there is no fourth (symbol)
     * block.
     */
    public static final List<String> CHISELED_NAMES = List.of("weapon", "trophy", "yautja");

    /** One facing block per name in {@link #CHISELED_NAMES}, same order. Cut from grey stone. */
    public static final List<BLibHolder<Block>> CHISELED = createChiseled();

    /**
     * [stated] "pillar top is the top and bottom of pillar base" - a column: the fluted pillar-base texture on the
     * sides, the pillar-top square on the ends. Turns with placement like a quartz pillar or a log, so it can lie on
     * its side.
     */
    public static final BLibHolder<Block> GREY_VILLAGE_PILLAR = create(
        "village_pillar",
        () -> new net.minecraft.world.level.block.RotatedPillarBlock(villageStoneProperties())
    );

    /**
     * [stated] "teeth and swirl are mislabled. those are border blocks" and "polished is its own block and the top and
     * bottom of teeth and swirl" - the pattern on all four sides, polished stone on top and bottom. {@code
     * village_border_<name>}.
     */
    public static final List<String> BORDER_NAMES = List.of("teeth", "swirl");

    /** One border block per name in {@link #BORDER_NAMES}, same order. Cut from grey stone. */
    public static final List<BLibHolder<Block>> BORDERS = BORDER_NAMES.stream()
        .map(name -> create("village_border_" + name, () -> new Block(villageStoneProperties())))
        .toList();

    /** The red families, base first. */
    public static final List<StoneFamily> RED_VILLAGE_FAMILIES = List.of(
        RED_VILLAGE_STONE,
        RED_VILLAGE_BRICKS,
        RED_VILLAGE_SMALL_BRICKS,
        RED_VILLAGE_ROUGH_BRICKS,
        RED_VILLAGE_TILES,
        RED_VILLAGE_POLISHED
    );

    /** The grey families, base first. */
    public static final List<StoneFamily> GREY_VILLAGE_FAMILIES = List.of(
        GREY_VILLAGE_STONE,
        GREY_VILLAGE_POLISHED,
        GREY_VILLAGE_BRICKS,
        GREY_VILLAGE_SMALL_BRICKS,
        GREY_VILLAGE_ROUGH_BRICKS,
        GREY_VILLAGE_TILES
    );

    /** Every family of both stones, red first. */
    public static final List<StoneFamily> VILLAGE_FAMILIES = java.util.stream.Stream.concat(
        RED_VILLAGE_FAMILIES.stream(),
        GREY_VILLAGE_FAMILIES.stream()
    ).toList();

    /**
     * Every village block in creative-tab order: the red families (base, slab, stairs, wall) and glyphs, then the grey
     * families, the chiseled blocks, the pillar and the borders.
     */
    public static final List<BLibHolder<? extends Block>> VILLAGE_BLOCKS = collectVillageBlocks();

    /**
     * One stone family. {@code base} is a full cube; the variants take vanilla's shapes.
     *
     * @param base   the full block
     * @param slab   its slab
     * @param stairs its stairs
     * @param wall   its wall
     */
    public record StoneFamily(
        BLibHolder<Block> base,
        BLibHolder<SlabBlock> slab,
        BLibHolder<StairBlock> stairs,
        BLibHolder<WallBlock> wall
    ) {

        /** {@return the four blocks, base first} */
        public List<BLibHolder<? extends Block>> all() {
            return List.of(base, slab, stairs, wall);
        }
    }

    /**
     * Village stone hardness. [stated] "these stones are as hard as steel" - "by using yautja methods of masonry by
     * combining certain stones you get a much stronger stone".
     * <p>
     * [stated] Oct 14: "steels hardness is 6" - avp_human steel's own value, matched exactly.
     */
    public static final float VILLAGE_STONE_HARDNESS = 6.0F;

    /**
     * Village stone blast resistance. [stated] "high blast resist", chosen as 20: a creeper cannot break it, a TNT
     * blast (which reaches about 17 at its centre) only chips the surface. Steel is 7, stone bricks 6, obsidian 1200.
     */
    public static final float VILLAGE_STONE_BLAST_RESISTANCE = 20.0F;

    /**
     * Every village block: VANILLA STONE BRICKS for sound, map colour and pickaxe-to-drop, with the strength above. ⚠
     * It needs an IRON pickaxe like steel - that gate is the minecraft:needs_iron_tool tag, set in the block tag
     * provider. Acid resistance is a tag too (avp_human:acid_resistant_3x, read by avp_alien's acid).
     */
    private static BlockBehaviour.Properties villageStoneProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)
            .strength(VILLAGE_STONE_HARDNESS, VILLAGE_STONE_BLAST_RESISTANCE);
    }

    private static StoneFamily family(String basePath, String variantPrefix) {
        BLibHolder<Block> base = create(basePath, () -> new Block(villageStoneProperties()));

        return new StoneFamily(
            base,
            create(variantPrefix + "_slab", () -> new SlabBlock(villageStoneProperties())),
            // ⚠ The base is created first in this registry, so it is bound by the time the stair's supplier runs.
            create(
                variantPrefix + "_stairs",
                () -> new com.predator.common.gameplay.block.PredatorStairBlock(base.get().defaultBlockState(), villageStoneProperties())
            ),
            // forceSolidOn, as vanilla's own walls do, so a wall still counts as solid for what sits on it.
            create(variantPrefix + "_wall", () -> new WallBlock(villageStoneProperties().forceSolidOn()))
        );
    }

    private static List<BLibHolder<Block>> createGlyphs(int count) {
        var glyphs = new ArrayList<BLibHolder<Block>>(count);

        for (var i = 1; i <= count; i++) {
            glyphs.add(
                create(
                    "village_red_glyph_" + i,
                    () -> new com.predator.common.gameplay.block.PredatorFacingBlock(villageStoneProperties())
                )
            );
        }

        return Collections.unmodifiableList(glyphs);
    }

    private static List<BLibHolder<Block>> createChiseled() {
        var chiseled = new ArrayList<BLibHolder<Block>>(CHISELED_NAMES.size());

        for (var name : CHISELED_NAMES) {
            chiseled.add(
                create(
                    "village_chiseled_" + name,
                    () -> new com.predator.common.gameplay.block.PredatorFacingBlock(villageStoneProperties())
                )
            );
        }

        return Collections.unmodifiableList(chiseled);
    }

    private static List<BLibHolder<? extends Block>> collectVillageBlocks() {
        var blocks = new ArrayList<BLibHolder<? extends Block>>();
        RED_VILLAGE_FAMILIES.forEach(family -> blocks.addAll(family.all()));
        blocks.addAll(GLYPHS);
        GREY_VILLAGE_FAMILIES.forEach(family -> blocks.addAll(family.all()));
        blocks.addAll(CHISELED);
        blocks.add(GREY_VILLAGE_PILLAR);
        blocks.addAll(BORDERS);
        return Collections.unmodifiableList(blocks);
    }

    private static <T extends Block> BLibHolder<T> create(String path, Supplier<T> blockSupplier) {
        return REGISTRY.createHolder(path, blockSupplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
