package com.predator.fabric.data;

import com.predator.compatibility.avp_alien.AVPAlien;
import com.predator.compatibility.avp_human.AVPHuman;
import com.predator.fabric.data.gene_bonus_data.GeneBonusDataSubProvider;
import com.predator.fabric.data.infections.InfectionSubProvider;
import com.predator.fabric.data.jukebox_song.PredatorJukeboxSongsProvider;
import com.predator.fabric.data.lang.en_us.EnglishLanguageProvider;
import com.predator.fabric.data.loot.BlockLootTableProvider;
import com.predator.fabric.data.loot.EntityLootTableProvider;
import com.predator.fabric.data.model.BlockModelProvider;
import com.predator.fabric.data.model.ItemModelProvider;
import com.predator.fabric.data.recipe.RecipeProvider;
import com.predator.fabric.data.tag.PredatorBlockTagProvider;
import com.predator.fabric.data.tag.PredatorDamageTypeTagProvider;
import com.predator.fabric.data.tag.PredatorEntityTypeTagProvider;
import com.predator.fabric.data.tag.PredatorItemTagProvider;
import com.predator.fabric.data.tag.PredatorMobEffectTagProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;

public class PredatorDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        var pack = fabricDataGenerator.createPack();
        // Language providers
        pack.addProvider(EnglishLanguageProvider::new);

        // Model providers
        pack.addProvider(BlockModelProvider::new);
        pack.addProvider(ItemModelProvider::new);

        // Recipe providers
        pack.addProvider(RecipeProvider::new);

        // Tag providers
        pack.addProvider(PredatorBlockTagProvider::new);
        pack.addProvider(PredatorDamageTypeTagProvider::new);
        pack.addProvider(PredatorEntityTypeTagProvider::new);
        pack.addProvider(PredatorItemTagProvider::new);
        pack.addProvider(PredatorMobEffectTagProvider::new);

        // Loot providers
        pack.addProvider(BlockLootTableProvider::new);
        pack.addProvider(EntityLootTableProvider::new);

        // Jukebox Song Providers
        pack.addProvider(PredatorJukeboxSongsProvider::new);

        // Custom Providers — each gated on the optional mod that owns the data type. When the mod isn't loaded its
        // classes aren't on the runtime classpath, so referencing the SubProvider class (let alone instantiating it)
        // would NoClassDefFoundError. The if-gate ensures the line never runs.
        if (AVPHuman.MOD.isLoaded()) {
            pack.addProvider(GeneBonusDataSubProvider::new);
        }

        if (AVPAlien.MOD.isLoaded()) {
            pack.addProvider(InfectionSubProvider::new);
        }
    }

    @Override
    public void buildRegistry(RegistrySetBuilder registryBuilder) {}
}
