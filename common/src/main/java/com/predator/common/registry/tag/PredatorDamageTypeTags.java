package com.predator.common.registry.tag;

import com.predator.PredatorResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public class PredatorDamageTypeTags {

    /**
     * Damage types that never count toward the cloak's four-heart break threshold. The field is shorted by impacts —
     * bullets, blades, a bad landing — not by attrition, so starvation, poison, wither and drowning are excluded.
     * <p>
     * A tag rather than a hardcoded list so a modpack can decide whether its own damage sources should give a hunter
     * away, without touching the mod.
     */
    public static final TagKey<DamageType> CLOAK_IGNORES = create("cloak_ignores");

    private static TagKey<DamageType> create(String path) {
        return TagKey.create(Registries.DAMAGE_TYPE, PredatorResources.location(path));
    }

    private PredatorDamageTypeTags() {
        throw new UnsupportedOperationException();
    }
}
