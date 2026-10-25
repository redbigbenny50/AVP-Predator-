package com.predator.client.compat;

import com.blib.api.client.viewer.v1.BLibViewers;
import com.blib.api.client.viewer.v1.ConversionViewerPages;
import com.blib.api.client.viewer.v1.ViewerPage;
import com.blib.api.client.viewer.v1.ViewerSlot;
import com.predator.Predator;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * avp_predator in JEI, REI and EMI - through BLib's viewer pages, so avp_predator needs no viewer dependency of its own.
 * <ul>
 * <li>Phase 1 - every avp_predator mob's and block's drops, read from its loot tables.</li>
 * <li>Phase 3 - <b>World Interactions</b>: bottling yautja blood, and what plasma blasts turn blocks into (read from
 * the plasma conversion table - empty by default, so it shows whatever a data pack adds).</li>
 * <li>Phase 4 - <b>Information</b> pages for the yautja gear nothing else explains: the Hunter-only armour and the
 * gauntlet a dead Hunter leaves armed. (The weapons yautja DROP are in their loot table, so Mob Drops shows them - the
 * hand caster among them since Oct 24. Their held weapon never drops: vanilla's equipment drop is switched off.)</li>
 * </ul>
 */
public final class PredatorViewers {

    private PredatorViewers() {}

    public static void register() {
        BLibViewers.registerLootDrops(Predator.MOD_ID, Predator.class);

        BLibViewers.worldInteractions();
        BLibViewers.registerPages(PredatorViewers::worldInteractions);

        info(
            "hunter_armour",
            List.of("jungle_predator_helmet", "jungle_predator_chestplate", "jungle_predator_leggings", "jungle_predator_boots"),
            "Only Hunters drop yautja armour: one random piece each time a Hunter dies."
        );
        info(
            "disc_fragment",
            List.of("predator_music_disc_1_fragment"),
            "Found in ancient city chests (15% chance, 1-3) and dungeon chests (10%, 1-2) - and in avp_human's marine chests when it is installed. Nine make the disc."
        );
        info(
            "hand_caster",
            List.of("hand_caster"),
            "A very rare drop from Blooded and higher yautja - never from Unblooded or Youngbloods."
        );
        info(
            "gauntlet",
            List.of("gauntlet"),
            "Only Hunters carry one: a dead Hunter leaves its gauntlet set down ARMED - it counts down and explodes. Right-click it and match its four-digit code to disarm it, or break it to destroy it with no blast."
        );
    }

    private static void info(String id, List<String> names, String text) {
        BLibViewers.registerInfo(
            new com.blib.api.client.viewer.v1.ViewerInfo(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Predator.MOD_ID, id),
                () -> {
                    var stacks = new ArrayList<ItemStack>();
                    for (var name : names) {
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Predator.MOD_ID, name))
                            .filter(item -> item != Items.AIR)
                            .ifPresent(item -> stacks.add(new ItemStack(item)));
                    }
                    return stacks;
                },
                List.of(Component.literal(text))
            )
        );
    }

    private static List<ViewerPage> worldInteractions() {
        var pages = new ArrayList<ViewerPage>();
        pages.add(
            new ViewerPage(
                BLibViewers.WORLD_INTERACTIONS,
                List.of(
                    ViewerSlot.of(new ItemStack(Items.GLASS_BOTTLE))
                        .withTooltip(Component.literal("Use it on spilled yautja blood").withStyle(ChatFormatting.GOLD))
                ),
                List.of(ViewerSlot.of(new ItemStack(PredatorItems.YAUTJA_BLOOD_BOTTLE.get()))),
                List.of(
                    Component.literal("Bottling yautja blood"),
                    Component.literal("A wounded yautja leaves blood; it dries in 5 minutes").withStyle(ChatFormatting.DARK_GRAY)
                ),
                0
            )
        );
        pages.addAll(
            ConversionViewerPages.pages(
                Predator.class,
                "/data/avp_predator/avp_predator/plasma_conversions/default.json",
                Component.literal("Plasma blast"),
                Component.literal("What a plasma blast leaves behind").withStyle(ChatFormatting.DARK_GRAY),
                ItemStack.EMPTY
            )
        );
        return pages;
    }
}
