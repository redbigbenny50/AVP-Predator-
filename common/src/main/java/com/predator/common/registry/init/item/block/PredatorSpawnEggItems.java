package com.predator.common.registry.init.item.block;

import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.predator.Predator;
import com.predator.common.registry.init.PredatorEntityTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class PredatorSpawnEggItems {

    public static final BLibRegistry<Item> REGISTRY = Predator.MOD.registries().create(BuiltInRegistries.ITEM);

    /**
     * ⚠ The path here is the ENTITY name, not the item name — {@link #create} appends {@code _spawn_egg}. It must track
     * the entity type id: the entity became {@code yautja_jungle} but this was left as {@code yautja}, so the item
     * stayed {@code avp_predator:yautja_spawn_egg} while the migration rewrote saved stacks to
     * {@code yautja_jungle_spawn_egg} — an id nothing registered. The model generator iterates this registry, so it
     * kept emitting the old name and the egg rendered as a missing texture, creative tab icon included.
     */
    public static final BLibHolder<SpawnEggItem> YAUTJA_SPAWN_EGG =
        create("yautja_jungle", PredatorEntityTypes.YAUTJA);

    private static <E extends Mob> BLibHolder<SpawnEggItem> create(String path, BLibHolder<EntityType<E>> holder) {
        return REGISTRY.createHolder(
            path + "_spawn_egg",
            Predator.MOD.factories().createSpawnEggSupplier(holder, 0xFFFFFF, 0xFFFFFF, new Item.Properties())
        );
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
