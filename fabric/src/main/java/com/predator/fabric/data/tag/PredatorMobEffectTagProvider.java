package com.predator.fabric.data.tag;

import com.blib.api.common.tag.v1.BLibMobEffectTags;
import com.predator.common.registry.init.PredatorMobEffects;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;

import java.util.concurrent.CompletableFuture;

/**
 * Mob-effect tag contributions from AVP-Predator. The mud cloak is tagged into {@link BLibMobEffectTags#MILK_IMMUNE}
 * (water washes mud off, milk doesn't) and {@link BLibMobEffectTags#NO_PARTICLES} (the cloak's purpose is defeated by a
 * visible particle swirl). Other entries can be added here as new effects are introduced.
 */
public class PredatorMobEffectTagProvider extends FabricTagProvider<MobEffect> {

    public PredatorMobEffectTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, Registries.MOB_EFFECT, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        addMilkImmune();
        addNoParticles();
    }

    private void addMilkImmune() {
        getOrCreateTagBuilder(BLibMobEffectTags.MILK_IMMUNE)
            .add(PredatorMobEffects.getMudHolder().unwrapKey().orElseThrow());
    }

    private void addNoParticles() {
        getOrCreateTagBuilder(BLibMobEffectTags.NO_PARTICLES)
            .add(PredatorMobEffects.getMudHolder().unwrapKey().orElseThrow());
    }
}
