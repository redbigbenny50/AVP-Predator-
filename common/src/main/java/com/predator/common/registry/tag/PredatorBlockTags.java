package com.predator.common.registry.tag;

import com.predator.Predator;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class PredatorBlockTags {

    private PredatorBlockTags() {
        throw new UnsupportedOperationException();
    }

    /**
     * Blocks a xenomorph should seek out and break — the way it treats a sentry gun. avp_predator lists the placed
     * gauntlet here from day one; the alien-side goal that reads it is a separate build.
     */
    /**
     * Blocks a yautja must never break even though they sit inside its hardness window.
     * <p>
     * ⚠ The tag is only the HAND-PICKED part of the blacklist. Metal (by sound), anything with a block entity, doors,
     * and every avp_human block are refused in code — see YautjaPathing.isBlacklisted — so nothing needs listing here
     * for those. Add to it in PredatorBlockTagProvider.
     */
    public static final TagKey<Block> YAUTJA_UNBREAKABLE = TagKey.create(
        Registries.BLOCK,
        Predator.MOD.resources().createLocation("yautja_unbreakable")
    );

    public static final TagKey<Block> XENOMORPH_THREAT_BLOCKS = TagKey.create(
        Registries.BLOCK,
        Predator.MOD.resources().createLocation("xenomorph_threat_blocks")
    );
}
