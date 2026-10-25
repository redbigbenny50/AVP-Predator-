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
 * same roll at the same 5%. 🚨 Oct 24 - [stated] "predators should drop casters ... the caster is a very rare drop from
 * them" and "yes the weapons should drop from the pred why on earth would we not want it to." Only the sword, axe and
 * combi stick were ever in this roll; every other weapon a yautja carries (its loadout: the battleaxe, plasma sword,
 * both bows, smart disc, chain whip) and the hand caster had NO way to drop, because the
 * held-item drop is off (below). Now every one is in this same single roll - still one weapon at most - weighted so the
 * plain veritanium gear is common, the plasma gear uncommon and the hand caster the rarest of all.
 * <p>
 * [stated] Oct 24 - "each kill should drop a weapon not half only because how difficult they are and extra weapons can
 * be broken down into veritanium anyway": there is no "nothing" result any more - every kill gives one weapon. And
 * "unblooded and spawned young blood tiers dont drop casters": the hand caster's entry only counts for Blooded and
 * above. It is checked on the yautja's own saved {@code Tier} (Yautja saves it under that name), so for an Unblooded or
 * a Youngblood the caster simply drops out of the roll and the other weapons share its tiny weight.
 * </p> Vanilla's own held-item drop is switched off (Yautja.getEquipmentDropChance), or the weapon
 * in its hand could be a second.</li>
 * <li><b>Ammo</b> — sometimes a handful of what a yautja fires: darts, fire pellets or veritanium arrows.</li>
 * <li><b>NO ARMOUR</b> — it used to be in the weapon roll, so any yautja could drop a piece. Armour now comes only from
 * Hunters: one random piece each (Yautja.dropHunterArmourPiece), and vanilla's worn-armour drop is off too.</li>
 * </ul>
 * The combi stick drops plain — his members did not want the free Loyalty III it once carried.
 */
public class YautjaLootTable {

    /**
     * The one weapon roll - every kill gives one - out of 49: veritanium sword and axe ~20% each, battleaxe and
     * veritanium bow ~12% each, smart disc ~8%, combi stick, plasma sword, plasma bow and chain whip ~6% each, the hand
     * caster ~2% (his "very rare drop"; Blooded and up only). [stated] Oct 24 - "shurikens are ammo not a weapon. smart
     * disc would be a weapon": shurikens and plasma shurikens are in the AMMO roll below, not here.
     */
    private static final int SWORD_WEIGHT = 10;

    private static final int AXE_WEIGHT = 10;

    private static final int BATTLEAXE_WEIGHT = 6;

    private static final int VERITANIUM_BOW_WEIGHT = 6;

    private static final int SMART_DISC_WEIGHT = 4;

    private static final int COMBI_STICK_WEIGHT = 3;

    private static final int PLASMA_SWORD_WEIGHT = 3;

    private static final int PLASMA_BOW_WEIGHT = 3;

    private static final int CHAIN_WHIP_WEIGHT = 3;

    /** [stated] Oct 24 - "the caster is a very rare drop from them". */
    private static final int HAND_CASTER_WEIGHT = 1;



    /** Ammo: darts most often; something about two kills in three. */
    private static final int DART_WEIGHT = 2;

    private static final int PELLET_WEIGHT = 1;

    private static final int ARROW_WEIGHT = 1;

    /** [stated] Oct 24 - shurikens (thrown) and plasma shurikens (fired from the gauntlet) are ammo. */
    private static final int SHURIKEN_WEIGHT = 1;

    private static final int PLASMA_SHURIKEN_WEIGHT = 1;

    /** Kept at a third of the roll, as before: ammo about two kills in three. */
    private static final int NO_AMMO_WEIGHT = 3;

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
                .add(LootItem.lootTableItem(PredatorItems.BATTLEAXE.get()).setWeight(BATTLEAXE_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.VERITANIUM_BOW.get()).setWeight(VERITANIUM_BOW_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.SMART_DISC.get()).setWeight(SMART_DISC_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.COMBI_STICK.get()).setWeight(COMBI_STICK_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.PLASMA_SWORD.get()).setWeight(PLASMA_SWORD_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.PLASMA_BOW.get()).setWeight(PLASMA_BOW_WEIGHT))
                .add(LootItem.lootTableItem(PredatorItems.CHAIN_WHIP.get()).setWeight(CHAIN_WHIP_WEIGHT))
                .add(
                    LootItem.lootTableItem(PredatorItems.HAND_CASTER.get())
                        .setWeight(HAND_CASTER_WEIGHT)
                        .when(notTier("unblooded").and(notTier("youngblood")))
                )
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
                .add(
                    LootItem.lootTableItem(PredatorItems.SHURIKEN.get())
                        .setWeight(SHURIKEN_WEIGHT)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 4)))
                )
                .add(
                    LootItem.lootTableItem(PredatorItems.PLASMA_SHURIKEN.get())
                        .setWeight(PLASMA_SHURIKEN_WEIGHT)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))
                )
                .add(EmptyLootItem.emptyItem().setWeight(NO_AMMO_WEIGHT))
        );

    /** "This yautja's saved Tier is not {@code tier}" - read from its own data, as the loot table runs at its death. */
    private static net.minecraft.world.level.storage.loot.predicates.LootItemCondition.Builder notTier(String tier) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Tier", tier);
        return net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition.invert(
            net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition.hasProperties(
                net.minecraft.world.level.storage.loot.LootContext.EntityTarget.THIS,
                net.minecraft.advancements.critereon.EntityPredicate.Builder.entity()
                    .nbt(new net.minecraft.advancements.critereon.NbtPredicate(tag))
            )
        );
    }

    private YautjaLootTable() {
        throw new UnsupportedOperationException();
    }
}
