package com.predator.common.registry.tag;

import com.predator.PredatorResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PredatorEntityTypeTags {

    public static final TagKey<EntityType<?>> PREDATORS = create("predators");

    /**
     * ⚠ Aug 28 — the hated-enemy roster beyond the built-in xenomorph rule. Xenomorphs are hated by NAMESPACE (every
     * living avp_alien entity, present and future — see YautjaPredicates), so this tag exists for EXTENSIONS: add any
     * non-alien entity type here to make yautja hunt it on sight. Ships empty.
     */
    public static final TagKey<EntityType<?>> HATED_ENEMIES = TagKey.create(
        Registries.ENTITY_TYPE,
        PredatorResources.location("hated_enemies")
    );

    /**
     * Observers the predator cloak simply does not work on. Membership means the field is not a factor at all — not
     * reduced, not harder to see through, absent.
     * <p>
     * Default population is every xenomorph (they have no eyes for a refractive field to fool, so a cloaked hunter
     * reads to them exactly as an uncloaked one) plus the warden. The warden is in the tag for completeness rather than
     * necessity: {@code Warden#canTargetEntity} never tests visibility in the first place, and sculk sensors and
     * shriekers listen to {@code GameEvent} vibrations, which the cloak does not suppress. Sound-based hunting sees
     * through the field for free.
     */
    public static final TagKey<EntityType<?>> CLOAK_IMMUNE = create("cloak_immune");

    /**
     * Humanoids a yautja skins when it kills one unwatched, leaving a skinned corpse that holds the victim's drops.
     * [stated] "a marine colonist other human like creatures". Vanilla villagers, traders and illagers, avp_human's
     * marine and colonist, and Villagers Reborn's human variants (the last two optional, so the tag loads without those
     * mods). Babies are excluded in code, not here.
     */
    public static final TagKey<EntityType<?>> SKINNABLE = create("skinnable");

    /**
     * Kills worth a yautja's taunting laugh — [stated] "praetorians predaliens crushers queens empress harbinger",
     * through avp_alien's own caste tags, plus the vanilla bosses. Anything big and armoured that is not listed still
     * counts through YautjaTaunts' health-and-armour rule.
     */
    public static final TagKey<EntityType<?>> TAUNT_WORTHY_KILLS = create("taunt_worthy_kills");

    /**
     * Things a capture net cannot hold.
     * <p>
     * ⚠ A TAG, not a hardcoded list, so modpacks add their own bosses without touching this mod — and so avp_alien
     * contributes its harbingers, queens and empresses from ITS side, where the strain tags live and stay correct as
     * castes are added.
     * <p>
     * ⚠⚠ FAILS OPEN. An empty tag means every boss is netable, so if datagen ever silently drops this file the symptom
     * is a netted wither rather than an error. Verify the generated json exists after a datagen run.
     */
    /**
     * Escape-chance tiers for a netted creature. Tags first, bounding box as the fallback, so a datapack can place any
     * mob — modded included — exactly where it belongs without code. See NetEscape.
     */
    public static final TagKey<EntityType<?>> NET_ESCAPE_NONE = create("net_escape_none");

    public static final TagKey<EntityType<?>> NET_ESCAPE_MEDIUM = create("net_escape_medium");

    public static final TagKey<EntityType<?>> NET_ESCAPE_MEDIUM_LARGE = create("net_escape_medium_large");

    public static final TagKey<EntityType<?>> NET_ESCAPE_LARGE = create("net_escape_large");

    public static final TagKey<EntityType<?>> NET_ESCAPE_HUGE = create("net_escape_huge");

    public static final TagKey<EntityType<?>> NET_IMMUNE = create("net_immune");

    /**
     * Entity types that should be visible in the predator thermal-vision post-effect. Entities not in this tag are
     * skipped during the gbuffer pass while thermal is active — their pixels show the cold world heat behind them, so
     * they appear "invisible" in IR.
     * <p>
     * Empty by default — modders/datapacks add the entity types they want detectable. Common case: warm-blooded mobs
     * (cows, sheep, players, villagers, hostile mobs) but not e.g. silverfish, endermites, or constructed/non-
     * biological entities.
     */
    public static final TagKey<EntityType<?>> THERMAL_VISIBLE = create("thermal_visible");

    /**
     * Entity types that read as "naturally hot" in thermal vision — every bone's effective block-light coord is floored
     * at maximum, so the entity always reads as if lit by a full-intensity light source regardless of its actual world
     * lighting. Combined with the warm-color heuristic (lava/magma/blaze textures are red/orange), the result is the
     * entity displays as bright orange/red/white in IR.
     * <p>
     * Membership implies {@link #THERMAL_VISIBLE} effectively — but they should still be added to both tags explicitly.
     * An entity in THERMAL_HOT but not in THERMAL_VISIBLE will be skipped from rendering entirely.
     */
    public static final TagKey<EntityType<?>> THERMAL_HOT = create("thermal_hot");

    /**
     * Entity types that should be visible in the predator electromagnetic-vision post-effect. Entities not in this tag
     * are skipped during the gbuffer pass while EM is active. Default population (via the fabric tag provider) targets
     * end-realm beings (enderman, ender dragon, endermite).
     */
    public static final TagKey<EntityType<?>> EM_VISIBLE = create("em_visible");

    /**
     * Thermal heat tiers. Membership decides how warm a creature reads, and a datapack can move any mob between them or
     * add a modded one without a code change — which is the whole point: these lists were hardcoded and every new
     * creature meant a rebuild.
     * <p>
     * ⚠ A mob in NO tier reads at ambient, which is correct for the undead, arthropods, constructs and xenomorphs.
     * Being absent is a real answer here, not an oversight.
     */
    public static final TagKey<EntityType<?>> HEAT_WARM_BLOODED_LARGE = create("heat/warm_blooded_large");

    public static final TagKey<EntityType<?>> HEAT_WARM_BLOODED = create("heat/warm_blooded");

    public static final TagKey<EntityType<?>> HEAT_WARM_BLOODED_SMALL = create("heat/warm_blooded_small");

    public static final TagKey<EntityType<?>> HEAT_COLD_BLOODED = create("heat/cold_blooded");

    /** The faintest reading there is — below {@link #HEAT_COLD_BLOODED}. Fish, squid, guardians. */
    public static final TagKey<EntityType<?>> HEAT_COLD_BLOODED_AQUATIC = create("heat/cold_blooded_aquatic");

    /**
     * Made of fire, or currently on fire. Reads past the top of the gradient — white-hot while a player reads red.
     * <p>
     * ⚠ State-dependent cases are NOT in this tag and cannot be: a swelling creeper, a charging ghast and anything
     * burning are decided in code because they change from frame to frame.
     */
    public static final TagKey<EntityType<?>> HEAT_BURNING = create("heat/burning");

    /**
     * SIZE OVERRIDES for the body gradient. Optional, and empty by default ON PURPOSE.
     * <p>
     * The default is DERIVED LIVE from the entity's bounding box every render, so every mob in the game — including one
     * from a mod nobody has heard of, and including a baby whose box is half size — already gets a sensible answer with
     * nobody authoring anything. These tags exist only for the cases where the box lies about the body: something long
     * and flat, or a large model inside a small hitbox.
     * <p>
     * ⚠ This is the SOFTENING dial only — how much a creature's heat varies between its core and its extremities. It
     * cannot make a creature hotter or colder overall; that is the tier's job, and the tier clamps the result.
     */
    public static final TagKey<EntityType<?>> HEAT_SIZE_TINY = create("heat/size/tiny");

    public static final TagKey<EntityType<?>> HEAT_SIZE_SMALL = create("heat/size/small");

    public static final TagKey<EntityType<?>> HEAT_SIZE_MEDIUM = create("heat/size/medium");

    public static final TagKey<EntityType<?>> HEAT_SIZE_LARGE = create("heat/size/large");

    /**
     * STATE-CHANGE TIER SHIFT. {@code heat/state_shift/<trigger>/<tier>} — a mob listed there jumps to that tier while
     * the named trigger is active, instead of the built-in default of {@link #HEAT_BURNING}.
     * <p>
     * ⚠⚠ THE TRIGGER LIST IS FIXED AND THAT IS THE WHOLE DESIGN CONSTRAINT. A datapack can say WHICH TIER a mob shifts
     * to; it cannot invent a new way to DETECT the shift, because for an arbitrary modded mob we have no idea what its
     * "about to explode" state looks like from outside. The triggers are detected generically in
     * {@code PredatorHeatMaterials}:
     * <ul>
     * <li>{@code on_fire} — {@code isOnFire()}, works for literally any entity.</li>
     * <li>{@code swelling} — any {@code Creeper} SUBCLASS, so modded creeper variants are covered for free.</li>
     * <li>{@code charging} — any {@code Ghast} subclass, same.</li>
     * </ul>
     * Adding a detector here is the extension point. A modded mob that implements its own explosion from scratch cannot
     * be covered without that mod exposing something we can see.
     * <p>
     * Tier names match the {@code heat/} tag paths: {@code warm_blooded_large}, {@code warm_blooded},
     * {@code warm_blooded_small}, {@code cold_blooded}, {@code cold_blooded_aquatic}, {@code burning}, {@code ambient}.
     */
    public static TagKey<EntityType<?>> heatStateShift(String trigger, String tierName) {
        return STATE_SHIFT_CACHE.computeIfAbsent(
            trigger + "/" + tierName,
            path -> create("heat/state_shift/" + path)
        );
    }

    private static final Map<String, TagKey<EntityType<?>>> STATE_SHIFT_CACHE = new ConcurrentHashMap<>();

    private static TagKey<EntityType<?>> create(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, PredatorResources.location(name));
    }
}
