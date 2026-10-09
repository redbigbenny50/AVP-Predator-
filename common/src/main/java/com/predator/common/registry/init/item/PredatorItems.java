package com.predator.common.registry.init.item;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.item.CloakingDeviceItem;
import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.gameplay.item.MudBucketItem;
import com.predator.common.gameplay.item.ShurikenItem;
import com.predator.common.gameplay.item.SmartDiscItem;
import com.predator.common.gameplay.item.battleaxe.BattleaxeItem;
import com.predator.common.gameplay.item.bow.PlasmaBowItem;
import com.predator.common.gameplay.item.bow.VeritaniumBowItem;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import com.predator.common.gameplay.whip.WhipItem;
import com.predator.common.registry.init.PredatorTiers;
import com.predator.common.registry.key.PredatorJukeboxSongKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiscFragmentItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;

import java.util.function.Supplier;

public class PredatorItems {

    public static final BLibRegistry<Item> REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.ITEM);

    public static final BLibHolder<Item> PREDATOR_MUSIC_DISC_1 = create(
        "predator_music_disc_1",
        new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(PredatorJukeboxSongKeys.PREDATOR_MUSIC_1)
    );

    public static final BLibHolder<Item> PREDATOR_MUSIC_DISC_1_FRAGMENT = create(
        "predator_music_disc_1_fragment",
        () -> new DiscFragmentItem(new Item.Properties())
    );

    /** The whip / grapple. Everything it needs lives in {@code common.gameplay.whip}; see WhipTuning. */
    public static final BLibHolder<Item> WHIP = create("whip", WhipItem::new);

    /**
     * The chain whip: the gauntlet's grapple. Loaded like ammunition but never spent.
     * <p>
     * ⚠ A plain Item — it does nothing in the hand. Its behaviour lives in the gauntlet's launcher, because that is
     * what fires it.
     */
    public static final BLibHolder<Item> CHAIN_WHIP = create("chain_whip", () -> new Item(new Item.Properties().stacksTo(1)));

    /** An ordinary bow that hits 1.3x harder. Muscle-powered, so a yautja's THROWN tier multiplier applies. */
    public static final BLibHolder<Item> VERITANIUM_BOW = create("veritanium_bow", VeritaniumBowItem::new);

    /** Draws like a bow, needs no arrows, fires a bursting bolt. Device-powered, so the TECH multiplier applies. */
    public static final BLibHolder<Item> PLASMA_BOW = create("plasma_bow", PlasmaBowItem::new);

    /** A two-handed axe: harder than any vanilla axe, as slow as one, and a ground slam on right click. */
    public static final BLibHolder<Item> BATTLEAXE = create("battleaxe", BattleaxeItem::new);

    public static final BLibHolder<Item> SHURIKEN = create("shuriken", ShurikenItem::new);

    public static final BLibHolder<Item> SMART_DISC = create("smart_disc", SmartDiscItem::new);

    /** [stated] fired from the gauntlet, homes on ONE target, returns — 20% to break into two shards on a hit. */
    public static final BLibHolder<Item> PLASMA_SHURIKEN = create("plasma_shuriken", new Item.Properties().stacksTo(16).fireResistant());

    /** [stated] crafted from a gauntlet (or, later, a hand caster) alone in the grid; the plasma shuriken's core. */
    public static final BLibHolder<Item> PLASMA_CORE = create("plasma_core", new Item.Properties().fireResistant());

    /** [stated] the plasma caster, handheld, for players — hold left click to charge, release to fire. */
    public static final BLibHolder<Item> HAND_CASTER = create(
        "hand_caster",
        () -> new com.predator.common.gameplay.item.HandCasterItem(new Item.Properties())
    );

    /** [stated] "These arrows bypass armor defense." */
    public static final BLibHolder<Item> VERITANIUM_ARROW = create(
        "veritanium_arrow",
        () -> new com.predator.common.gameplay.item.VeritaniumArrowItem(new Item.Properties())
    );

    /** [stated] more damage than the sword, sets things alight, lights blocks by hitting them. */
    public static final BLibHolder<Item> PLASMA_SWORD = create(
        "plasma_sword",
        () -> new com.predator.common.gameplay.item.PlasmaSwordItem(new Item.Properties())
    );

    public static final BLibHolder<Item> MUD_BUCKET = create("mud_bucket", MudBucketItem::new);

    public static final BLibHolder<Item> CLOAKING_DEVICE = create("cloaking_device", CloakingDeviceItem::new);

    public static final BLibHolder<Item> VERITANIUM_AXE = create(
        "veritanium_axe",
        () -> new AxeItem(
            PredatorTiers.VERITANIUM,
            new Item.Properties().fireResistant().attributes(AxeItem.createAttributes(PredatorTiers.VERITANIUM, 6.0F, -3.1F))
        )
    );

    public static final BLibHolder<Item> VERITANIUM_HOE = create(
        "veritanium_hoe",
        () -> new HoeItem(
            PredatorTiers.VERITANIUM,
            new Item.Properties().fireResistant().attributes(HoeItem.createAttributes(PredatorTiers.VERITANIUM, -2.0F, -1.0F))
        )
    );

    public static final BLibHolder<Item> VERITANIUM_PICKAXE = create(
        "veritanium_pickaxe",
        () -> new PickaxeItem(
            PredatorTiers.VERITANIUM,
            new Item.Properties().fireResistant().attributes(PickaxeItem.createAttributes(PredatorTiers.VERITANIUM, 1.0F, -2.8F))
        )
    );

    public static final BLibHolder<Item> VERITANIUM_SHARD = create("veritanium_shard", new Item.Properties().fireResistant());

    /**
     * [stated] "veritanium scrap its made buy 9 veritanium shards ... also you can use it to craft 9 veritanium shards
     * so its a compression item however its also used to craft veritanium tools." — the pickaxe, shovel and hoe.
     */
    public static final BLibHolder<Item> VERITANIUM_SCRAP = create("veritanium_scrap", new Item.Properties().fireResistant());

    /** Gauntlet ammunition. ⚠ Stacks to 16 like the shuriken — a hunter carries a few, not a bandolier. */
    public static final BLibHolder<Item> NET = create("net", new Item.Properties().stacksTo(16).fireResistant());

    /** The collapsible spear. ⚠ Single stack with durability — it is a weapon, not ammunition. */
    public static final BLibHolder<CombiStickItem> COMBI_STICK = create("combi_stick", CombiStickItem::new);

    /** The player's wrist gauntlet — five ammunition slots and a cloak housing, opened by right-clicking. */
    public static final BLibHolder<GauntletItem> GAUNTLET = create("gauntlet", GauntletItem::new);

    /**
     * Ammunition for the wrist bracer.
     * <p>
     * A plain item on purpose — it is fired from the gauntlet, not thrown by hand, so it needs no use behaviour of its
     * own. The gauntlet is what will consume it.
     */
    public static final BLibHolder<Item> VERITANIUM_DART = create("veritanium_dart", new Item.Properties().fireResistant());

    /** Gauntlet incendiary round — see {@code FirePelletProjectile}. */
    public static final BLibHolder<Item> FIRE_PELLET = create("fire_pellet", new Item.Properties().fireResistant());

    public static final BLibHolder<Item> VERITANIUM_SHOVEL = create(
        "veritanium_shovel",
        () -> new ShovelItem(
            PredatorTiers.VERITANIUM,
            new Item.Properties().fireResistant().attributes(ShovelItem.createAttributes(PredatorTiers.VERITANIUM, 1.5F, -3.0F))
        )
    );

    public static final BLibHolder<SwordItem> VERITANIUM_SWORD = PredatorItems.create(
        "veritanium_sword",
        () -> new SwordItem(
            PredatorTiers.VERITANIUM,
            new Item.Properties().fireResistant().attributes(SwordItem.createAttributes(PredatorTiers.VERITANIUM, 3, -2.4F))
        )
    );

    // ---------------------------------------------------------------- yautja grenades (his Oct 3 spec)

    public static final BLibHolder<Item> PRED_GRENADE_EXPLOSIVE = grenade(
        "pred_grenade_explosive",
        com.predator.common.gameplay.item.grenade.GrenadeKind.EXPLOSIVE
    );

    public static final BLibHolder<Item> PRED_GRENADE_FIRE = grenade(
        "pred_grenade_fire",
        com.predator.common.gameplay.item.grenade.GrenadeKind.FIRE
    );

    public static final BLibHolder<Item> PRED_GRENADE_STICKY = grenade(
        "pred_grenade_sticky",
        com.predator.common.gameplay.item.grenade.GrenadeKind.STICKY
    );

    public static final BLibHolder<Item> PRED_GRENADE_FREEZE = grenade(
        "pred_grenade_freeze",
        com.predator.common.gameplay.item.grenade.GrenadeKind.FREEZE
    );

    public static final BLibHolder<Item> PRED_GRENADE_IRRADIATED = grenade(
        "pred_grenade_irradiated",
        com.predator.common.gameplay.item.grenade.GrenadeKind.IRRADIATED
    );

    private static BLibHolder<Item> grenade(String name, com.predator.common.gameplay.item.grenade.GrenadeKind kind) {
        return create(
            name,
            () -> new com.predator.common.gameplay.item.grenade.YautjaGrenadeItem(kind, new Item.Properties().stacksTo(16))
        );
    }

    /** [stated] yautja blood "can be bottled" — an empty glass bottle used on the blood trail. */
    public static final BLibHolder<Item> YAUTJA_BLOOD_BOTTLE = create(
        "yautja_blood_bottle",
        new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
    );

    private static BLibHolder<Item> create(String name, Item.Properties properties) {
        return create(name, () -> new Item(properties));
    }

    private static <T extends Item> BLibHolder<T> create(String name, Supplier<T> itemSupplier) {
        return REGISTRY.createHolder(name, itemSupplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
