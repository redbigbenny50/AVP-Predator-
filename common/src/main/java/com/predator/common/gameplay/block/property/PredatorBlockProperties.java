package com.predator.common.gameplay.block.property;

import com.blib.api.common.block.v1.BlockPropertyBuilder;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public class PredatorBlockProperties {

    public static final Supplier<BlockPropertyBuilder> TRIP_MINE_SUPPLIER = () -> BlockPropertyBuilder.of()
        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
        .mapColor(MapColor.SAND)
        .requiresCorrectToolForDrops()
        .sound(SoundType.COPPER)
        .strength(7, 8);

    public static final BlockPropertyBuilder TRIP_MINE = TRIP_MINE_SUPPLIER.get();

    /**
     * The placed gauntlet. ⚠ Hardness 40 with a pickaxe as the tool is [stated] "about 10 seconds with an iron pick"
     * (iron 10 s, diamond 7.5 s, netherite 6.7 s, bare hands ~200 s). Resistance 1200 so no explosion removes it and
     * mining stays the only way through; the block is also made immovable by pistons where it is registered.
     */
    public static final Supplier<BlockPropertyBuilder> GAUNTLET_SUPPLIER = () -> BlockPropertyBuilder.of()
        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
        .mapColor(MapColor.COLOR_GRAY)
        .requiresCorrectToolForDrops()
        .sound(SoundType.NETHERITE_BLOCK)
        .strength(40, 1200);

    public static final BlockPropertyBuilder GAUNTLET = GAUNTLET_SUPPLIER.get();

    /**
     * The skinned corpse. [stated] "when mined either by hand or tool" — so no correct-tool requirement and a hardness
     * a bare hand gets through in well under a second. Soft and fleshy to the touch.
     */
    public static final Supplier<BlockPropertyBuilder> SKINNED_CORPSE_SUPPLIER = () -> BlockPropertyBuilder.of()
        .mapColor(MapColor.COLOR_RED)
        .sound(new com.predator.common.gameplay.block.SkinnedCorpseSoundType())
        .strength(0.5F, 0.5F);

    public static final BlockPropertyBuilder SKINNED_CORPSE = SKINNED_CORPSE_SUPPLIER.get();
}
