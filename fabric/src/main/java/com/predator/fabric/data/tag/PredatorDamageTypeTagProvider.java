package com.predator.fabric.data.tag;

import com.predator.common.registry.init.PredatorDamageTypes;
import com.predator.common.registry.tag.PredatorDamageTypeTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.concurrent.CompletableFuture;

/**
 * Damage types that never count toward the cloak's four-heart break threshold.
 * <p>
 * The dividing line is impact versus attrition. A bullet, a blade or a bad landing shorts the field; starving, being
 * poisoned or slowly drowning does not. That matches how a cloaked hunter behaves on screen — hit, and still walking —
 * without making the field immune to being shot off.
 */
public class PredatorDamageTypeTagProvider extends FabricTagProvider<DamageType> {

    public PredatorDamageTypeTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, Registries.DAMAGE_TYPE, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        // The self-destruct kills outright: nothing soaks it, nothing dodges it, nothing is immune.
        // ⚠ addOptional, not add: plasma_detonation is a HAND-WRITTEN damage-type JSON, and the datagen tag provider
        // validates plain entries against a registry lookup that only knows datagen'd types — "Couldn't define tag
        // minecraft:bypasses_armor as it is missing following references". An optional entry skips that check and
        // resolves at runtime exactly like a required one, where the JSON does exist.
        var plasma = PredatorDamageTypes.PLASMA_DETONATION.location();

        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_ARMOR).addOptional(plasma);
        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_INVULNERABILITY).addOptional(plasma);
        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_EFFECTS).addOptional(plasma);
        getOrCreateTagBuilder(DamageTypeTags.IS_EXPLOSION).addOptional(plasma);

        getOrCreateTagBuilder(PredatorDamageTypeTags.CLOAK_IGNORES)
            .add(DamageTypes.STARVE)
            .add(DamageTypes.MAGIC)
            .add(DamageTypes.WITHER)
            .add(DamageTypes.DROWN)
            .add(DamageTypes.IN_WALL)
            .add(DamageTypes.CRAMMING)
            .add(DamageTypes.DRY_OUT)
            .add(DamageTypes.OUTSIDE_BORDER)
            .add(DamageTypes.GENERIC_KILL)
            .add(DamageTypes.FELL_OUT_OF_WORLD);
    }
}
