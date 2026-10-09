package com.predator.common.gameplay.entity.projectile;

import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which impact sound a block deserves.
 * <h2>⚠⚠ CLASSIFIED BY VANILLA'S OWN SoundType, NOT BY A BLOCK LIST</h2> Every block already declares what it sounds
 * like, and that declaration is what vanilla uses for footsteps and breaking. Matching on it means a modded copper roof
 * or a stone variant from another mod is classified correctly without anyone updating a list here — and a list of block
 * names would be wrong the day someone adds a block.
 * <p>
 * Anything that is not clearly wood, metal or stone falls to GENERAL — [stated] "if we dont know what type of block it
 * is then it does general".
 */
public final class ImpactMaterial {

    private ImpactMaterial() {
        throw new UnsupportedOperationException();
    }

    /** {@return the sound the disc makes striking this block} */
    public static SoundEvent forBlock(BlockState state) {
        var sound = state.getSoundType();

        if (isWood(sound)) {
            return PredatorSoundEvents.SMART_DISC_HIT_WOOD.get();
        }

        if (isMetal(sound)) {
            return PredatorSoundEvents.SMART_DISC_HIT_METAL.get();
        }

        if (isStone(sound)) {
            return PredatorSoundEvents.SMART_DISC_HIT_STONE.get();
        }

        return PredatorSoundEvents.SMART_DISC_HIT_GENERAL.get();
    }

    /** ⚠ Bamboo and cherry are wood despite their own sound types; ladders are wooden too. */
    private static boolean isWood(SoundType sound) {
        return sound == SoundType.WOOD
            || sound == SoundType.BAMBOO_WOOD
            || sound == SoundType.CHERRY_WOOD
            || sound == SoundType.NETHER_WOOD
            || sound == SoundType.LADDER;
    }

    /** ⚠ Copper, netherite and chains all read as metal to a thrown blade, whatever vanilla calls them. */
    private static boolean isMetal(SoundType sound) {
        return sound == SoundType.METAL
            || sound == SoundType.COPPER
            || sound == SoundType.COPPER_BULB
            || sound == SoundType.COPPER_GRATE
            || sound == SoundType.NETHERITE_BLOCK
            || sound == SoundType.ANCIENT_DEBRIS
            || sound == SoundType.CHAIN;
    }

    /** ⚠ Ore counts as stone: an iron ore block is stone with metal in it, and it breaks like stone. */
    private static boolean isStone(SoundType sound) {
        return sound == SoundType.STONE
            || sound == SoundType.DEEPSLATE
            || sound == SoundType.DEEPSLATE_BRICKS
            || sound == SoundType.DEEPSLATE_TILES
            || sound == SoundType.POLISHED_DEEPSLATE
            || sound == SoundType.CALCITE
            || sound == SoundType.TUFF
            || sound == SoundType.BASALT
            || sound == SoundType.NETHER_ORE
            || sound == SoundType.GILDED_BLACKSTONE;
    }
}
