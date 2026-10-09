package com.predator.common.registry.tag;

import com.predator.PredatorResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class PredatorItemTags {

    /**
     * Ranged weapons heavy enough that a yautja answers them with the plasma caster.
     * <p>
     * His rule was "the enemy has a powerful weapon thats ranged that it matches", named as rapid-fire caseless,
     * drum-fed weapons, rocket launchers and sentry guns. A tag rather than a code list so the line can be moved in
     * datagen, and so another mod's weapons can join it without touching avp_predator at all.
     */
    /**
     * Items whose own right click must win over the worn gauntlet.
     * <p>
     * ⚠⚠ VANILLA FALLS THROUGH TO THE OFF HAND whenever the main hand returns PASS — which a lead does on a right-click
     * at air — so the gauntlet fired a net out of a lead. [stated] "when i rightclick the whip it sometimes fires the
     * net when the gauntlets worn ... Same with leads." Anything in this tag blocks the gauntlet's fire while it is
     * held, and a datapack can extend it without touching code.
     */
    public static final TagKey<Item> BLOCKS_GAUNTLET_FIRE = create("blocks_gauntlet_fire");

    /**
     * Weapons held in BOTH hands, which raise the third-person arms into a two-handed carry.
     * <p>
     * [stated] Sep 25: the arm should be up "when holding a two handed weapon like the battleaxe or using the bow like
     * vanillas arm does". A tag rather than an {@code instanceof BattleaxeItem} check, so the combistick, a future
     * glaive, or another mod's polearm can join the pose without a code change.
     * </p>
     */
    public static final TagKey<Item> TWO_HANDED = create("two_handed");

    /**
     * Oct 7 - helmets a Gigeresque facehugger will not go for: the bio-mask covers the face. See
     * {@code MixinGigEntityUtils_FacehuggerProofMask}. Filled with the yautja masks; packs can add more.
     */
    public static final TagKey<Item> FACEHUGGER_PROOF_HELMETS = create("facehugger_proof_helmets");

    /**
     * Items a yautja can heal itself with.
     * <p>
     * ⚠ EMPTY FOR NOW — [stated] "(we will add health items)". The net's recovery rule already checks for anything in
     * this tag, so adding a health item to it is all it takes; no code has to change when they arrive.
     */
    public static final TagKey<Item> YAUTJA_HEALING_ITEMS = create("yautja_healing_items");

    /**
     * Armour a yautja may WEAR — the only items its armour slots accept in the yautja screen. [stated] "only predator
     * armor is valid in the slot". A tag rather than a hard-coded check, so predator sets added for tribes are one
     * datagen line each. The slot still requires the piece to fit ITS slot (helmet in the head, and so on).
     */
    public static final TagKey<Item> YAUTJA_ARMOR = create("yautja_armor");

    /**
     * Items a yautja must NEVER be given — its screen's rack and hand refuse them. [stated] "you can give the yautja
     * the hand caster manually. dont let it accept this weapon." — the hand caster is a player's weapon only. A tag, so
     * any later player-only item is one datagen line.
     */
    public static final TagKey<Item> YAUTJA_FORBIDDEN = create("yautja_forbidden");

    /**
     * The ONLY items a yautja's MAIN HAND accepts in its screen — a whitelist. [stated] "you can give the yautja
     * anything in its main hand. this is not good it needs a whitelist and to have only the weapons for the yautja
     * minus the handcaster ... people are giving it nether stars and all kinds of things."
     * <p>
     * Exactly the weapons its loadout holds in hand: the melee five and the four hand-held ranged weapons. NOT the
     * plasma shuriken (fired from the gauntlet, never held) and NOT the hand caster (player-only).
     */
    public static final TagKey<Item> YAUTJA_HAND_WEAPONS = create("yautja_hand_weapons");

    /**
     * What a yautja's RACK accepts in its screen — a whitelist. [stated] "yes the rack should have a white list too.
     * the weapons, the ammo it uses, and the healing items". Holds the weapons (every hand weapon, plus the
     * gauntlet-fired plasma shuriken) and its ammunition. ⚠ The HEALING items are not listed here: two of them are
     * potions, and a tag cannot tell Healing II from any other potion — the rack also accepts whatever
     * Yautja.isHealingItem recognises, so the rack and the healing behaviour can never disagree.
     */
    public static final TagKey<Item> YAUTJA_RACK_ITEMS = create("yautja_rack_items");

    public static final TagKey<Item> CASTER_WORTHY_WEAPONS = create("caster_worthy_weapons");

    /**
     * Killing someone holding one of these earns a yautja's taunting laugh — [stated] "anyone using a high dps weapon
     * sniper gatling gun smart gun high hitting pred weapons".
     */
    public static final TagKey<Item> TAUNT_WORTHY_WEAPONS = create("taunt_worthy_weapons");

    public static final TagKey<Item> HOSTILE_WEAPONS = create("hostile_weapons");

    /**
     * What the gauntlet will accept in its five ammunition slots.
     * <p>
     * ⚠ A TAG rather than a hardcoded list, so a pack — or a later feature — can add a round without touching the menu.
     * Currently darts and nets, the two things the wrist launcher fires on land.
     */
    public static final TagKey<Item> GAUNTLET_AMMUNITION = create("gauntlet_ammunition");

    /** ⚠ Separate tag for the cloak housing — it is one slot and must never accept ammunition. */
    public static final TagKey<Item> CLOAK_CORES = create("cloak_cores");

    public static final TagKey<Item> JUNGLE_PREDATOR_ARMOR = create("jungle_predator_armor");

    public static final TagKey<Item> PREDATOR_ARMORS = create("predator_armors");

    private static TagKey<Item> create(String name) {
        return TagKey.create(Registries.ITEM, PredatorResources.location(name));
    }
}
