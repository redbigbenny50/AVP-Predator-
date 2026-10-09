package com.predator.fabric.data.lang.en_us;

import com.predator.common.registry.init.PredatorMobEffects;
import com.predator.fabric.data.lang.en_us.provider.EnUsAdvancementProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsBlockProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsBlockTagProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsConfigProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsCreativeModeTabProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsEntityProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsEntityTypeTagProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsGauntletProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsItemProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsItemTagProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsSoundEventProvider;
import com.predator.fabric.data.lang.en_us.provider.EnUsTooltipProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;

import java.util.concurrent.CompletableFuture;

public class EnglishLanguageProvider extends FabricLanguageProvider {

    public EnglishLanguageProvider(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider registryLookup, TranslationBuilder builder) {
        // Blocks
        EnUsBlockProvider.CONSUMER.accept(builder);

        // Creative Mode Tabs
        EnUsCreativeModeTabProvider.CONSUMER.accept(builder);

        // Entities
        EnUsEntityProvider.CONSUMER.accept(builder);

        // Items
        EnUsItemProvider.CONSUMER.accept(builder);

        // Gauntlet, its messages, and the keybinds
        EnUsGauntletProvider.CONSUMER.accept(builder);

        // Sounds
        EnUsSoundEventProvider.CONSUMER.accept(builder);

        // Jukebox Sounds
        builder.add("jukebox_song.avp_predator.predator_music_1", "Rotch Gwylt - Hunter");

        // Mob Effects
        builder.add(PredatorMobEffects.getMudHolder().value(), "Mud");
        builder.add(PredatorMobEffects.getFrozenSolidHolder().value(), "Frozen Solid");

        // Advancements
        EnUsAdvancementProvider.CONSUMER.accept(builder);

        // Configs
        EnUsConfigProvider.CONSUMER.accept(builder);

        // Tags
        EnUsBlockTagProvider.CONSUMER.accept(builder);
        EnUsItemTagProvider.CONSUMER.accept(builder);
        EnUsEntityTypeTagProvider.CONSUMER.accept(builder);

        // Tooltips
        EnUsTooltipProvider.CONSUMER.accept(builder);
    }
}
