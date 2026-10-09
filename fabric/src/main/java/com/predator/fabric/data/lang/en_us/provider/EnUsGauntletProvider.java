package com.predator.fabric.data.lang.en_us.provider;

import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import java.util.function.Consumer;

/**
 * Gauntlet and keybind strings.
 * <p>
 * ⚠ These were crammed into {@code EnUsItemProvider} alongside the item names, which is not where a container title, an
 * ammunition message or a key label belongs. One provider per concern, as the rest of this package already does.
 */
public final class EnUsGauntletProvider {

    private EnUsGauntletProvider() {
        throw new UnsupportedOperationException();
    }

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        builder.add("container.avp_predator.gauntlet", "Wrist Gauntlet");

        builder.add("gauntlet.avp_predator.ammo.dart", "Dart");
        builder.add("gauntlet.avp_predator.ammo.fire_pellet", "Fire Pellet");
        builder.add(
            "gauntlet.avp_predator.destruct.armed",
            "Place the gauntlet on the ground to begin self destruct, or click a number to cancel."
        );
        builder.add("gauntlet.avp_predator.destruct.cancelled", "Self destruct cancelled.");
        builder.add("gauntlet.avp_predator.destruct.started", "Self destruct sequence initiated.");
        builder.add("gauntlet.avp_predator.destruct.cannot_wear", "A gauntlet counting down cannot be worn.");
        builder.add("gauntlet.avp_predator.destruct.disarmed", "Self destruct disarmed.");
        builder.add("gauntlet.avp_predator.destruct.disabled", "Destruct is disabled, please enable with gamerule prednuke");
        builder.add("gamerule.prednuke", "Gauntlet self-destruct");
        // ⚠ The SAME key and text avp_alien writes for this rule — it is shared between the two mods, so whichever lang
        // file wins, the screen reads the same.
        builder.add("gamerule.gunBalancing", "Gun Mod Balancing");
        builder.add(
            "gamerule.gunBalancing.description",
            "Scales TACZ and Point Blank damage against xenomorphs and yautja to match AVP: Human's guns. Turn off for their full, unmodified damage."
        );
        builder.add(
            "gamerule.prednuke.description",
            "Whether the yautja gauntlet can be armed to self-destruct. Default on; off on dedicated servers until enabled."
        );
        builder.add("gauntlet.avp_predator.destruct.disarm_title", "Disarm");
        builder.add("subtitles.gauntlet.destruct_countdown", "Self destruct counts down");
        builder.add("subtitles.gauntlet.destruct_armed", "Self destruct arms");
        builder.add("subtitles.gauntlet.open", "Wrist gauntlet opens");
        builder.add("subtitles.gauntlet.dart", "Dart fires");
        builder.add("subtitles.gauntlet.pellet", "Fire pellet fires");
        builder.add("subtitles.gauntlet.net", "Net launches");
        builder.add("subtitles.grapple.chain_deploy", "Grapple chain fires");
        builder.add("subtitles.grapple.chain_retract", "Grapple chain winds in");
        builder.add("subtitles.whip.attack", "Whip lashes out");
        builder.add("subtitles.whip.crack", "Whip cracks");
        builder.add("subtitles.whip.equip", "Whip coils");
        builder.add("subtitles.smart_disc.equip", "Smart disc spins up");
        builder.add("subtitles.smart_disc.throw", "Smart disc thrown");
        builder.add("subtitles.smart_disc.loop", "Smart disc whirs");
        builder.add("subtitles.smart_disc.return", "Smart disc returns");
        builder.add("subtitles.smart_disc.hit_general", "Smart disc strikes");
        builder.add("subtitles.smart_disc.hit_wood", "Smart disc bites wood");
        builder.add("subtitles.smart_disc.hit_metal", "Smart disc rings off metal");
        builder.add("subtitles.smart_disc.hit_stone", "Smart disc chips stone");
        builder.add("subtitles.smart_disc.hit_entity", "Smart disc cuts");
        builder.add("subtitles.shuriken.equip", "Shuriken readied");
        builder.add("subtitles.shuriken.throw", "Shuriken thrown");
        builder.add("subtitles.shuriken.charge", "Shuriken wound up");
        builder.add("subtitles.shuriken.charge_end", "Shuriken ready");
        builder.add("subtitles.plasma_bow.equip", "Plasma bow powers up");
        builder.add("subtitles.plasma_bow.draw", "Plasma bow drawn");
        builder.add("subtitles.plasma_bow.hold", "Plasma charge hums");
        builder.add("subtitles.plasma_bow.fire", "Plasma bolt fired");
        builder.add("subtitles.plasma_bow.hit", "Plasma bolt bursts");
        builder.add("gamerule.yautjaDebugInventory", "Yautja debug inventory (creative only)");
        builder.add(
            "gamerule.yautjaDebugInventory.description",
            "Whether a creative player can right-click a yautja empty-handed to edit its inventory. Development tool; default off."
        );
        builder.add("screen.avp_predator.yautja_debug.equipped", "%s — holding: %s");
        builder.add("screen.avp_predator.yautja_debug.bare_handed", "bare handed");
        builder.add("screen.avp_predator.yautja_debug.main_hand", "Main Hand");
        builder.add("screen.avp_predator.yautja_debug.yautja_inventory", "Yautja Inventory");
        builder.add("screen.avp_predator.yautja_debug.hotbar", "Hotbar");
        builder.add("subtitles.gauntlet.empty", "Gauntlet clicks empty");
        builder.add("subtitles.projectile.net_catch", "Net snares prey");
        builder.add("subtitles.projectile.pellet_hit", "Fire pellet bursts");
        builder.add("subtitles.gauntlet.close", "Wrist gauntlet closes");
        builder.add("subtitles.gauntlet.destruct_armed_cancel", "Self destruct stands down");
        builder.add("subtitles.gauntlet.destruct_disarm_button", "Keypad beeps");
        builder.add("subtitles.gauntlet.destruct_disarm_button_confirm", "Keypad accepts");
        builder.add("gauntlet.avp_predator.destruct.move_hint", "To move bomb: shift + right click");
        builder.add("death.attack.avp_predator.plasma_detonation", "%1$s was atomized by heated plasma");
        builder.add("death.attack.avp_predator.plasma_detonation.player", "%1$s was atomized by %2$s's heated plasma");
        builder.add("gauntlet.avp_predator.ammo.net", "Net");
        builder.add("gauntlet.avp_predator.ammo.chain_whip", "Chain Whip");
        builder.add("gauntlet.avp_predator.ammo.plasma_shuriken", "Plasma Shuriken");
        builder.add("gauntlet.avp_predator.no_ammo", "%s - no ammo");
        builder.add("gauntlet.avp_predator.not_equipped", "%s - not equipped");
        builder.add("gauntlet.avp_predator.no_cloak", "No cloaking device fitted");
        builder.add("gauntlet.avp_predator.cloak_already_fitted", "A cloak is already fitted to your bracer - press %s to activate");
        builder.add("gauntlet.avp_predator.cloak_fitted", "Fitted to your gauntlet - press %s to activate");

        // ⚠⚠ BLib builds a keybind's NAME as "key.<namespace>.<path>" and its CATEGORY as
        // "keybind.category.<namespace>.<category>". I had the names and never the category, so the whole
        // avp_predator section in Controls would have shown the raw string.
        builder.add("keybind.category.avp_predator.predator", "AVP: Predator");

        builder.add("key.avp_predator.open_gauntlet", "Open Gauntlet");
        builder.add("key.avp_predator.toggle_gauntlet_cloak", "Toggle Cloak");

        // ⚠ Pre-existing gap, not mine: toggle_vision has been registered since before this work and has never had
        // a name, so it shows as "key.avp_predator.toggle_vision" in Controls today.
        builder.add("key.avp_predator.toggle_vision", "Toggle Vision Mode");
    };
}
