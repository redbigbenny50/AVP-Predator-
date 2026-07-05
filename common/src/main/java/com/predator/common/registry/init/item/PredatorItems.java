package com.predator.common.registry.init.item;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.gameplay.item.MudBucketItem;
import com.predator.common.gameplay.item.ShurikenItem;
import com.predator.common.gameplay.item.SmartDiscItem;
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

    public static final BLibHolder<Item> SHURIKEN = create("shuriken", ShurikenItem::new);

    public static final BLibHolder<Item> SMART_DISC = create("smart_disc", SmartDiscItem::new);

    public static final BLibHolder<Item> MUD_BUCKET = create("mud_bucket", MudBucketItem::new);

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
