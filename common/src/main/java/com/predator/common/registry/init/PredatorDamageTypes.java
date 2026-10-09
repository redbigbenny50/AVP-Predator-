package com.predator.common.registry.init;

import com.predator.Predator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * Data-driven damage types. Definitions are the hand-written JSON files under {@code data/avp_predator/damage_type/};
 * these are only the keys.
 */
public final class PredatorDamageTypes {

    private PredatorDamageTypes() {
        throw new UnsupportedOperationException();
    }

    /** The gauntlet self-destruct. Kills outright; the death message is "atomized by heated plasma". */
    public static final ResourceKey<DamageType> PLASMA_DETONATION = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        Predator.MOD.resources().createLocation("plasma_detonation")
    );
}
