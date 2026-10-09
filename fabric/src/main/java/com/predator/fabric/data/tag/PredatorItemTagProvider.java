package com.predator.fabric.data.tag;

import com.alien.common.registry.tag.AlienItemTags;
import com.blib.api.common.tag.v1.CommonItemTags;
import com.predator.Predator;
import com.predator.common.registry.init.item.PredatorArmorItems;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.tag.PredatorItemTags;
import com.predator.compatibility.avp_alien.AVPAlien;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;

import java.util.concurrent.CompletableFuture;

public class PredatorItemTagProvider extends FabricTagProvider.ItemTagProvider {

    public PredatorItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        addArmors();

        // Oct 7 - [stated] the predator mask is ignored by Gigeresque facehuggers.
        getOrCreateTagBuilder(PredatorItemTags.FACEHUGGER_PROOF_HELMETS)
            .add(PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get());
        addAutomatedTagItems();
        addCasterWorthyWeapons();
        addTauntWorthyWeapons();

        // ⚠⚠ THIS USED TO BE GATED ON AVPHuman.MOD.isLoaded() AND THEREFORE NEVER RAN. Optional mods are not on the
        // datagen runtime classpath, so the gate is always false there — the generated hostile_weapons.json in this
        // tree contains only the four vanilla entries, and no yautja has ever treated a marine with a pulse rifle as
        // armed. Built from strings instead: no avp_human class is referenced, so nothing needs gating, and
        // addOptionalTag means a world without avp_human simply resolves it to nothing.
        getOrCreateTagBuilder(PredatorItemTags.HOSTILE_WEAPONS)
            .addTag(ItemTags.AXES)
            .addTag(ItemTags.SWORDS)
            .add(
                Items.BOW,
                Items.CROSSBOW
            )
            .addOptionalTag(siblingId("avp_human", "guns"));

        // ⚠⚠ THIS IS WHAT LETS A PLAYER PUT LOYALTY ON THE COMBI STICK. Loyalty's supported_items is
        // #minecraft:enchantable/trident, so without this entry an anvil and an enchanting table both REFUSE it —
        // and since the drop no longer arrives enchanted, the spear would have had no way to return at all.
        //
        // ⚠ It admits the whole trident set, not just Loyalty: Impaling, Channeling and Riptide come with it.
        // Impaling and Channeling are fine on a spear. RIPTIDE IS THE ONE TO WATCH — it launches the player while
        // holding the weapon in rain or water, and the stick's own throw is bound to the same right-click. If that
        // conflicts in testing, the fix is a datapack override on riptide rather than removing this tag.
        // ⚠ What the gauntlet accepts. Darts and nets are what the wrist launcher fires on land; the tag exists so
        // a new round can be added without touching GauntletMenu.
        // ⚠⚠ THE CHAIN WHIP BELONGS HERE TOO. The gauntlet's slots accept only this tag, so without it the ammo type
        // could be CYCLED TO but the item could never be PUT IN — which is exactly the state it shipped in. It is not
        // consumed when fired, but it is still loaded like ammunition, so the slot filter must let it through.
        getOrCreateTagBuilder(PredatorItemTags.GAUNTLET_AMMUNITION)
            .add(PredatorItems.VERITANIUM_DART.get())
            .add(PredatorItems.FIRE_PELLET.get())
            .add(PredatorItems.NET.get())
            .add(PredatorItems.CHAIN_WHIP.get())
            // ⚠ In the tag or it cycles in the gauntlet but cannot be PLACED in its slot — the chain whip lesson.
            .add(PredatorItems.PLASMA_SHURIKEN.get());

        // [stated] "it needs a whitelist and to have only the weapons for the yautja minus the handcaster" — the only
        // items
        // the yautja screen's MAIN HAND accepts. The weapons its loadout holds in hand; no plasma shuriken
        // (gauntlet-fired),
        // no hand caster (player-only).
        getOrCreateTagBuilder(PredatorItemTags.YAUTJA_HAND_WEAPONS)
            .add(PredatorItems.VERITANIUM_SWORD.get())
            .add(PredatorItems.VERITANIUM_AXE.get())
            .add(PredatorItems.BATTLEAXE.get())
            .add(PredatorItems.COMBI_STICK.get())
            .add(PredatorItems.PLASMA_SWORD.get())
            .add(PredatorItems.VERITANIUM_BOW.get())
            .add(PredatorItems.PLASMA_BOW.get())
            .add(PredatorItems.SHURIKEN.get())
            .add(PredatorItems.SMART_DISC.get());

        // [stated] "the rack should have a white list too. the weapons, the ammo it uses, and the healing items" —
        // every
        // hand weapon (by tag), the gauntlet-fired plasma shuriken, and its ammunition. Healing items are accepted
        // through
        // Yautja.isHealingItem (the potions cannot be told apart by a tag).
        getOrCreateTagBuilder(PredatorItemTags.YAUTJA_RACK_ITEMS)
            .addTag(PredatorItemTags.YAUTJA_HAND_WEAPONS)
            .add(PredatorItems.PLASMA_SHURIKEN.get())
            .add(Items.ARROW)
            .add(PredatorItems.VERITANIUM_ARROW.get())
            .add(PredatorItems.VERITANIUM_DART.get())
            .add(PredatorItems.NET.get())
            .add(PredatorItems.FIRE_PELLET.get())
            .add(com.predator.common.registry.init.PredatorBlocks.TRIP_MINE_BLOCK.get().asItem())
            .add(PredatorItems.CHAIN_WHIP.get());

        // [stated] "dont let it accept this weapon" — what the yautja's screen refuses: player-only weapons.
        getOrCreateTagBuilder(PredatorItemTags.YAUTJA_FORBIDDEN)
            .add(PredatorItems.HAND_CASTER.get());

        // Every bow draws from minecraft:arrows — this is what lets vanilla and veritanium bows fire it.
        getOrCreateTagBuilder(net.minecraft.tags.ItemTags.ARROWS)
            .add(PredatorItems.VERITANIUM_ARROW.get());

        getOrCreateTagBuilder(PredatorItemTags.CLOAK_CORES)
            .add(PredatorItems.CLOAKING_DEVICE.get());

        getOrCreateTagBuilder(ItemTags.TRIDENT_ENCHANTABLE)
            .add(PredatorItems.COMBI_STICK.get());

        // ⚠ Durability too — the stick has 512 uses, and without this Unbreaking and Mending are refused on an item
        // that can visibly wear out, which reads as a bug rather than a rule.
        getOrCreateTagBuilder(ItemTags.DURABILITY_ENCHANTABLE)
            .add(PredatorItems.COMBI_STICK.get());

        // [stated] "let it be enchantable with sword fire enchantment." SWORD_ENCHANTABLE alone is not enough:
        // Fire Aspect and Sharpness are gated on their own tags, so the whip joins the whole melee family.
        // Items whose own right click must beat the worn gauntlet's. Vanilla's lead PASSES on a right-click at air,
        // which handed the click to the gauntlet and fired a net out of it.
        // ⚠ THE WHIP IS NO LONGER HERE. It blocked the gauntlet's right click because it used to grapple on one;
        // now that the grapple IS the gauntlet's, the whip must let that click through.
        // Items a yautja can heal with. ⚠ ITEMS ONLY: the Healing II and Regeneration II potions it also counts are
        // recognised by potion TYPE in Yautja.isHealingItem, because every potion is the same item, minecraft:potion,
        // and tagging that would make water bottles and poison count too.
        getOrCreateTagBuilder(PredatorItemTags.YAUTJA_HEALING_ITEMS)
            .add(Items.ENCHANTED_GOLDEN_APPLE);

        // [stated] "only predator armor is valid in the slot" — what the yautja screen's armour slots accept.
        getOrCreateTagBuilder(PredatorItemTags.YAUTJA_ARMOR)
            .add(com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get())
            .add(com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE.get())
            .add(com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS.get())
            .add(com.predator.common.registry.init.item.PredatorArmorItems.JUNGLE_PREDATOR_BOOTS.get());

        // ⚠⚠ THE POSE READS THIS TAG AND NOTHING ELSE. MixinHumanoidModel_BattleaxePose used to check
        // `instanceof BattleaxeItem`; it now checks #avp_predator:two_handed, so an EMPTY tag means the two-handed
        // carry silently stops working for everything. Anything held in both hands belongs here.
        getOrCreateTagBuilder(PredatorItemTags.TWO_HANDED)
            .add(PredatorItems.BATTLEAXE.get())
            .add(PredatorItems.COMBI_STICK.get());

        getOrCreateTagBuilder(PredatorItemTags.BLOCKS_GAUNTLET_FIRE)
            .add(Items.LEAD)
            .add(PredatorItems.COMBI_STICK.get())
            // ⚠ Two hands on the haft, and right click is the slam — [stated] "when equiped the gauntlet cant be
            // fired". The worn gauntlet still SHOWS in third person; it just cannot be used.
            .add(PredatorItems.BATTLEAXE.get());

        // ⚠⚠ TAGS ARE ADDITIVE, SO "EVERY BOW ENCHANTMENT EXCEPT TWO" IS NOT EXPRESSIBLE. Power, Punch, Flame and
        // Infinity all come from #minecraft:enchantable/bow; Mending and Unbreaking from
        // #minecraft:enchantable/durability. There is no way to join a tag and subtract one enchantment.
        // veritanium bow -> both tags: everything, as an ordinary bow.
        // plasma bow -> bow only: no Mending, as he asked. ⚠ That also costs it Unbreaking, and it still
        // accepts Infinity — which is harmless, since it already never consumes ammo.
        getOrCreateTagBuilder(ItemTags.BOW_ENCHANTABLE)
            .add(PredatorItems.VERITANIUM_BOW.get())
            .add(PredatorItems.PLASMA_BOW.get());

        getOrCreateTagBuilder(ItemTags.DURABILITY_ENCHANTABLE).add(PredatorItems.VERITANIUM_BOW.get());

        getOrCreateTagBuilder(ItemTags.SWORD_ENCHANTABLE).add(PredatorItems.WHIP.get());
        getOrCreateTagBuilder(ItemTags.SHARP_WEAPON_ENCHANTABLE).add(PredatorItems.WHIP.get());
        getOrCreateTagBuilder(ItemTags.FIRE_ASPECT_ENCHANTABLE).add(PredatorItems.WHIP.get());
        getOrCreateTagBuilder(ItemTags.WEAPON_ENCHANTABLE).add(PredatorItems.WHIP.get());
        getOrCreateTagBuilder(ItemTags.DURABILITY_ENCHANTABLE).add(PredatorItems.WHIP.get());

        getOrCreateTagBuilder(ItemTags.FREEZE_IMMUNE_WEARABLES)
            .addTag(PredatorItemTags.PREDATOR_ARMORS);

        addCompatibilityTags();
    }

    /**
     * The weapons that justify bringing the plasma caster out.
     * <p>
     * His rule, verbatim: "rapid fire caseless bullet weapons, drum ammo weapons, rocket launchers, and sentry guns".
     * The line is drawn at <b>reach</b>, because the caster is his answer to being outgunned at range — a hunter that
     * can close the distance does not need it.
     * <ul>
     * <li><b>In:</b> pulse rifle and F903WE (caseless, 64 blocks), M4RA (64), smartgun and Old Painless (drum-fed, a
     * shot every tick), rocket launcher (100), sniper (128).</li>
     * <li><b>Out:</b> both shotguns and the flamethrower — 20 blocks and 16 blocks, so they are a melee problem; and
     * the combat pistol, at 3 damage.</li>
     * </ul>
     * ⚠ Entries are optional and string-built for the reason spelled out above. The sentry turret is NOT here — it
     * carries no item, and the caster recognises it by entity id in {@code PlasmaCaster.isSentryTurret}.
     */
    /**
     * Weapons whose wielder is a trophy worth a laugh: avp_human's sniper, Old Painless and smartgun (optional ids),
     * and the heavy-hitting predator weapons.
     */
    private void addTauntWorthyWeapons() {
        var tauntWorthy = getOrCreateTagBuilder(PredatorItemTags.TAUNT_WORTHY_WEAPONS);

        for (var gun : new String[] { "m42a3_sniper_rifle", "old_painless", "m56_smartgun" }) {
            tauntWorthy.addOptional(siblingId("avp_human", gun));
        }

        tauntWorthy
            .add(PredatorItems.BATTLEAXE.get())
            .add(PredatorItems.COMBI_STICK.get())
            .add(PredatorItems.PLASMA_BOW.get())
            .add(PredatorItems.PLASMA_SWORD.get())
            .add(PredatorItems.HAND_CASTER.get());
    }

    private void addCasterWorthyWeapons() {
        var casterWorthy = getOrCreateTagBuilder(PredatorItemTags.CASTER_WORTHY_WEAPONS);

        for (
            var gun : new String[] {
                "f903we_rifle",
                "m41a_pulse_rifle",
                "m42a3_sniper_rifle",
                "m4ra_battle_rifle",
                "m56_smartgun",
                "m6b_rocket_launcher",
                "old_painless"
            }
        ) {
            casterWorthy.addOptional(siblingId("avp_human", gun));
        }
    }

    /**
     * {@return an id in another mod's namespace, built from strings — used for both tag ids and item ids}
     * <p>
     * ⚠ The whole point is that no class from that mod is touched. Sibling mods here are {@code modCompileOnly}: a hard
     * class reference inside a datagen provider compiles fine and then throws {@code NoClassDefFoundError} at
     * generation time, taking the ENTIRE provider down — every tag it ships, not just the cross-mod one — while the
     * build still reports success.
     */
    private static ResourceLocation siblingId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private void addAutomatedTagItems() {
        // Armor
        var headArmorTagProvider = getOrCreateTagBuilder(ItemTags.HEAD_ARMOR);
        var chestArmorTagProvider = getOrCreateTagBuilder(ItemTags.CHEST_ARMOR);
        var legArmorTagProvider = getOrCreateTagBuilder(ItemTags.LEG_ARMOR);
        var footArmorTagProvider = getOrCreateTagBuilder(ItemTags.FOOT_ARMOR);

        // Blocks
        var buttonTagProvider = getOrCreateTagBuilder(ItemTags.BUTTONS);
        var doorTagProvider = getOrCreateTagBuilder(ItemTags.DOORS);
        var fenceTagProvider = getOrCreateTagBuilder(ItemTags.FENCES);
        var slabTagProvider = getOrCreateTagBuilder(ItemTags.SLABS);
        var stairsTagProvider = getOrCreateTagBuilder(ItemTags.STAIRS);
        var trapdoorTagProvider = getOrCreateTagBuilder(ItemTags.TRAPDOORS);
        var wallTagBuilder = getOrCreateTagBuilder(ItemTags.WALLS);

        // Tools
        var axeTagProvider = getOrCreateTagBuilder(ItemTags.AXES);
        var hoeTagProvider = getOrCreateTagBuilder(ItemTags.HOES);
        var pickaxeTagProvider = getOrCreateTagBuilder(ItemTags.PICKAXES);
        var shovelTagProvider = getOrCreateTagBuilder(ItemTags.SHOVELS);

        // Weapons
        var swordTagProvider = getOrCreateTagBuilder(ItemTags.SWORDS);

        Predator.MOD.registries()
            .getAllHolders(BuiltInRegistries.ITEM)
            .forEach(holder -> {
                var item = holder.get();

                if (item instanceof ArmorItem armorItem) {
                    switch (armorItem.getType()) {
                        case HELMET -> headArmorTagProvider.add(item);
                        case CHESTPLATE -> chestArmorTagProvider.add(item);
                        case LEGGINGS -> legArmorTagProvider.add(item);
                        case BOOTS -> footArmorTagProvider.add(item);
                        case BODY -> { /* NO-OP */ }
                    }
                }

                if (item instanceof BlockItem blockItem) {
                    var block = blockItem.getBlock();

                    if (block instanceof ButtonBlock) {
                        buttonTagProvider.add(item);
                    }

                    if (block instanceof DoorBlock) {
                        doorTagProvider.add(item);
                    }

                    if (block instanceof FenceBlock) {
                        fenceTagProvider.add(item);
                    }

                    if (block instanceof SlabBlock) {
                        slabTagProvider.add(item);
                    }

                    if (block instanceof StairBlock) {
                        stairsTagProvider.add(item);
                    }

                    if (block instanceof TrapDoorBlock) {
                        trapdoorTagProvider.add(item);
                    }

                    if (block instanceof WallBlock) {
                        wallTagBuilder.add(item);
                    }
                }

                if (item instanceof AxeItem) {
                    axeTagProvider.add(item);
                }

                if (item instanceof HoeItem) {
                    hoeTagProvider.add(item);
                }

                if (item instanceof PickaxeItem) {
                    pickaxeTagProvider.add(item);
                }

                if (item instanceof ShovelItem) {
                    shovelTagProvider.add(item);
                }

                if (item instanceof SwordItem) {
                    swordTagProvider.add(item);
                }
            });
    }

    private void addArmors() {
        getOrCreateTagBuilder(PredatorItemTags.JUNGLE_PREDATOR_ARMOR)
            .add(
                PredatorArmorItems.JUNGLE_PREDATOR_BOOTS.get(),
                PredatorArmorItems.JUNGLE_PREDATOR_CHESTPLATE.get(),
                PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get(),
                PredatorArmorItems.JUNGLE_PREDATOR_LEGGINGS.get()
            );

        getOrCreateTagBuilder(PredatorItemTags.PREDATOR_ARMORS)
            .addTag(PredatorItemTags.JUNGLE_PREDATOR_ARMOR);
    }

    private void addCompatibilityTags() {
        getOrCreateTagBuilder(CommonItemTags.MUSIC_DISCS)
            .add(PredatorItems.PREDATOR_MUSIC_DISC_1.get());

        if (AVPAlien.MOD.isLoaded()) {
            getOrCreateTagBuilder(AlienItemTags.FACEHUGGER_RESISTANT_HELMETS)
                .add(PredatorArmorItems.JUNGLE_PREDATOR_HELMET.get());
        }
    }
}
