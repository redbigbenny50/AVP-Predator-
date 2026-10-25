package com.predator.common.data.loot;

import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.List;

/**
 * What avp_predator adds to VANILLA chests, in one place for both loaders (Fabric adds the pools through its loot event,
 * NeoForge through its loot-load event).
 * <p>
 * [stated] Oct 24 - "add the music disc fragments for alien and pred to ancient city chests as well as dungeon chests
 * so vanilla sources have them." Until now the fragments only came from avp_human's chests, so a pack without
 * avp_human could never make the disc. Nine fragments make the disc:
 * </p>
 * <ul>
 * <li><b>Ancient city chests</b> - a {@value #ANCIENT_CITY_CHANCE} chance of 1-3 fragments per chest (rare, deep and
 * dangerous - like vanilla's own disc 5 fragments there).</li>
 * <li><b>Dungeon chests</b> - a {@value #DUNGEON_CHANCE} chance of 1-2.</li>
 * </ul>
 * A pool of its own, so the chest's normal loot is untouched - chests may hold any number of items.
 */
public final class PredatorVanillaLoot {

    public static final float ANCIENT_CITY_CHANCE = 0.15F;

    public static final float DUNGEON_CHANCE = 0.10F;

    private PredatorVanillaLoot() {}

    /** The extra pools for this table, or none. */
    public static List<LootPool.Builder> extraPools(ResourceKey<LootTable> key) {
        if (key.equals(BuiltInLootTables.ANCIENT_CITY)) {
            return List.of(fragments(ANCIENT_CITY_CHANCE, 1, 3));
        }
        if (key.equals(BuiltInLootTables.SIMPLE_DUNGEON)) {
            return List.of(fragments(DUNGEON_CHANCE, 1, 2));
        }
        return List.of();
    }

    private static LootPool.Builder fragments(float chance, int min, int max) {
        return LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1))
            .when(LootItemRandomChanceCondition.randomChance(chance))
            .add(
                LootItem.lootTableItem(PredatorItems.PREDATOR_MUSIC_DISC_1_FRAGMENT.get())
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
            );
    }
}
