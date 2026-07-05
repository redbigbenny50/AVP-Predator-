package com.predator.common.registry.init.creative_mode_tab.initializer;

import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class ToolsAndUtilitiesCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_AXE);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_HOE);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_PICKAXE);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_SHOVEL);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_SWORD);

        CreativeModeTabUtil.accept(output, PredatorItems.PREDATOR_MUSIC_DISC_1);
        CreativeModeTabUtil.accept(output, PredatorItems.MUD_BUCKET);
    };
}
