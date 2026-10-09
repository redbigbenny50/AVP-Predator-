package com.predator.common.registry.init.creative_mode_tab.initializer;

import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class IngredientsCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        // Decorative materials
        CreativeModeTabUtil.accept(output, PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT);

        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_SHARD);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_SCRAP);
        CreativeModeTabUtil.accept(output, PredatorItems.YAUTJA_BLOOD_BOTTLE);
    };
}
