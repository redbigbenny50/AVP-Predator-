package com.predator.common.registry.init.creative_mode_tab.initializer;

import com.predator.common.registry.init.item.PredatorArmorItems;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class CombatCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        CreativeModeTabUtil.accept(output, PredatorItems.SHURIKEN);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_DART);
        CreativeModeTabUtil.accept(output, PredatorItems.FIRE_PELLET);
        CreativeModeTabUtil.accept(output, PredatorItems.SMART_DISC);
        CreativeModeTabUtil.accept(output, PredatorItems.PLASMA_SHURIKEN);
        CreativeModeTabUtil.accept(output, PredatorItems.PLASMA_CORE);
        CreativeModeTabUtil.accept(output, PredatorItems.HAND_CASTER);
        CreativeModeTabUtil.accept(output, PredatorItems.PLASMA_SWORD);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_ARROW);

        // ⚠ These three were registered and had lang, models and recipes, but were never added to a tab — so in
        // survival-creative they simply did not exist. Nothing errors when an item has no tab; it is invisible.
        CreativeModeTabUtil.accept(output, PredatorItems.COMBI_STICK);
        CreativeModeTabUtil.accept(output, PredatorItems.WHIP);
        CreativeModeTabUtil.accept(output, PredatorItems.CHAIN_WHIP);
        CreativeModeTabUtil.accept(output, PredatorItems.VERITANIUM_BOW);
        CreativeModeTabUtil.accept(output, PredatorItems.PLASMA_BOW);
        CreativeModeTabUtil.accept(output, PredatorItems.BATTLEAXE);
        CreativeModeTabUtil.accept(output, PredatorItems.NET);

        // ⚠ The gauntlet sits with the weapons rather than in Tools because it is the launcher for the two above,
        // and a player looking for the net will look where the net is.
        CreativeModeTabUtil.accept(output, PredatorItems.GAUNTLET);

        CreativeModeTabUtil.accept(output, PredatorArmorItems.JUNGLE_PREDATOR_HELMET);
        CreativeModeTabUtil.accept(output, PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE);
        CreativeModeTabUtil.accept(output, PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS);
        CreativeModeTabUtil.accept(output, PredatorArmorItems.JUNGLE_PREDATOR_BOOTS);

        CreativeModeTabUtil.accept(output, PredatorItems.PRED_GRENADE_EXPLOSIVE);
        CreativeModeTabUtil.accept(output, PredatorItems.PRED_GRENADE_FIRE);
        CreativeModeTabUtil.accept(output, PredatorItems.PRED_GRENADE_STICKY);
        CreativeModeTabUtil.accept(output, PredatorItems.PRED_GRENADE_FREEZE);
        CreativeModeTabUtil.accept(output, PredatorItems.PRED_GRENADE_IRRADIATED);
    };
}
