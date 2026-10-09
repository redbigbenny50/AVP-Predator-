package com.predator.common.gameplay.entity.living.yautja;

import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.function.Function;

/**
 * What every yautja drops — natural spawns, event spawns and Hunters alike. [stated] Oct 4: "hunters can drop ammo and
 * shards and one weapon max. one armor piece max as well ... natural spawned pred or event preds drop weapons still
 * only one. and the hunters drop armor. the idea of this is fighting hunters allows you to get the better gear."
 * <ul>
 * <li><b>Shards</b> — 2-3, plus Looting.</li>
 * <li><b>One weapon at most</b> — a single roll: the veritanium sword, the veritanium axe, or (rarely) the combi stick,
 * or nothing. ⚠ The combi stick used to be a separate 5% pool on top, which could make two weapons; it is now in the
 * same roll at the same 5%. Vanilla's own held-item drop is switched off (Yautja.getEquipmentDropChance), or the weapon
 * in its hand could be a second.</li>
 * <li><b>Ammo</b> — sometimes a handful of what a yautja fires: darts, fire pellets or veritanium arrows.</li>
 * <li><b>NO ARMOUR</b> — it used to be in the weapon roll, so any yautja could drop a piece. Armour now comes only from
 * Hunters: one random piece each (Yautja.dropHunterArmourPiece), and vanilla's worn-armour drop is off too.</li>
 * </ul>
 * The combi stick drops plain — his members did not want the free Loyalty III it once carried.
 */
public class YautjaLootTable {

    /**
     * Out of {@link #WEAPON_TOTAL}: the sword and the axe a sixth each, the combi stick 5%, nothing the rest (~62%).
     */
    private static final int SWORD_WEIGHT = 10;

    private static final int AXE_WEIGHT = 10;

    private static final int COMBI_STICK_WEIGHT = 3;

    private static final int NO_WEAPON_WEIGHT = 37;

    /** Ammo: darts most often; something about two kills in three. */
    private static final int DART_WEIGHT = 2;

    private static final int PELLET_WEIGHT = 1;

    private static final int ARROW_WEIGHT = 1;

    private static final int NO_AMMO_WEIGHT = 2;

    public static final Function<HolderLookup.Provider, LootTable.Builder> LOOT_TABLE = provider -> LootTable.lootTable()
        .withPool(
            LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(
                    LootItem.lootTableItem(PredatorItems.VERITANIUM_SHARD.get())
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 3)))
                        .apply(EnchantedCountIncreaseFunction.lootingMultiplier(provider, UniformGenerator.between(0, 1)))
                )
        )
        .withPool(
            LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(PredatorItems.VERITANIUM_SWORD.get()).setWeight(SWORD_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.VERITANIUM_AXE.get()).setWeight(AXE_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.COMBI_STICK.get()).setWeight(COMBI_STICK_WEIGHT))
                .add(EmptyLootItem.emptyItem().setWeight(NO_WEAPON_WEIGHT))
        )
        .withPool(
            LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(
                    LootItem.lootTableItem(PredatorItems.VERITANIUM_DART.get())
                        .setWeight(DART_WEIGHT)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(4, 8)))
                )
                .add(
                    LootItem.lootTableItem(PredatorItems.FIRE_PELLET.get())
                        .setWeight(PELLET_WEIGHT)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 4)))
                )
                .add(
                    LootItem.lootTableItem(PredatorItems.VERITANIUM_ARROW.get())
                        .setWeight(ARROW_WEIGHT)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(4, 8)))
                )
                .add(EmptyLootItem.emptyItem().setWeight(NO_AMMO_WEIGHT))
        );

    private YautjaLootTable() {
        throw new UnsupportedOperationException();
    }
}
