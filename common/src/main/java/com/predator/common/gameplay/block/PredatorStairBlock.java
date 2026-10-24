package com.predator.common.gameplay.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A plain stair. ⚠ Exists only because vanilla's StairBlock constructor is PROTECTED in the 1.21.1 jar (checked with
 * javap). NeoForge's access transformer widens it, so {@code new StairBlock(...)} would compile in the neoforge module
 * and then fail in {@code :common} - the same trap the Projectile constructor set. A subclass reaches it on every
 * loader.
 */
public class PredatorStairBlock extends StairBlock {

    public PredatorStairBlock(BlockState baseState, Properties properties) {
        super(baseState, properties);
    }
}
