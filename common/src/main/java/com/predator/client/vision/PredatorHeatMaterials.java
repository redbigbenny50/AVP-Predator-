package com.predator.client.vision;

import com.predator.common.gameplay.effect.PredatorMud;
import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;

/**
 * How warm a creature runs, and how sharply that warmth falls off across its body, packed into the one material-ID byte
 * the thermal shader reads.
 * <h2>Two dials in one byte</h2> The material-ID attachment is R8 and only six tier values were ever in use, so the
 * byte carries both:
 * <ul>
 * <li><b>low nibble</b> — the heat TIER. What the shader turns into a heat bonus, and which band it clamps the result
 * into.</li>
 * <li><b>high nibble</b> — the SIZE step, 1-15, driving how gently heat falls from core to extremity. <b>0 means
 * "nothing was pushed"</b> and the shader treats it as a large body, so an unpatched or non-consumer draw behaves
 * exactly as it did before this existed.</li>
 * </ul>
 * No new attachment, no new uniform, no BLib change.
 * <h2>Where each dial comes from</h2>
 * <ul>
 * <li>TIER — entity-type tags under {@code avp_predator:heat/}. A datapack moves any mob between tiers, or places a
 * modded one, without a code change.</li>
 * <li>SIZE — <b>derived live from the bounding box every render</b>, so every mob in the game already has a sensible
 * value with nobody authoring anything, and a baby gets small-body treatment for free. The
 * {@code avp_predator:heat/size/*} tags override it only where the box lies about the body.</li>
 * <li>STATE SHIFT — the trigger is detected here (generically, by superclass), the target tier defaults to
 * {@link #BURNING}, and a {@code heat/state_shift/<trigger>/<tier>} tag can retarget it per mob.</li>
 * </ul>
 * ⚠ A mob in no tier reads at ambient. That is the correct answer for the undead, arthropods, constructs and xenomorphs
 * — absence is a decision, not a gap.
 */
public final class PredatorHeatMaterials {

    /** Large mammal body heat — the hottest ordinary signature in the game. Tier 3, +0.55. */
    public static final int WARM_BLOODED_LARGE = 3;

    /** Ordinary mammal and bird warmth. Tier 1, +0.45. */
    public static final int WARM_BLOODED = 1;

    /** Small-bodied warmth: less mass, less signature. Tier 2, +0.30. */
    public static final int WARM_BLOODED_SMALL = 2;

    /** Not warm-blooded, not dead — a faint reading rather than none. Tier 5, +0.25. */
    public static final int COLD_BLOODED = 5;

    /** The faintest reading there is. Tier 4, +0.20 — below {@link #COLD_BLOODED}, the bottom of the scale. */
    public static final int COLD_BLOODED_AQUATIC = 4;

    /** Made of fire. Tier 6, +1.10 — past the top of the gradient, so these read white-hot. */
    public static final int BURNING = 6;

    /**
     * Bounding-box size at or below which a body counts as fully small, in blocks.
     * <p>
     * ⚠⚠ MEASURED AS {@code max(width, height)}, AND THE CHOICE MATTERS. A player is 0.6 wide, so measuring by width —
     * or by the smaller of the two — would file players as a small body and change how the most familiar creature in
     * the game looks. By the larger dimension a chicken is 0.7, a cow 1.4 and a player 1.8, so every large creature
     * sits at the far end of the ramp and comes out at exactly its previous value.
     */
    private static final float SIZE_SMALL = 0.7F;

    /** Bounding-box size at or above which a body counts as fully large — the previous behaviour, unchanged. */
    private static final float SIZE_LARGE = 1.4F;

    /** Tier names as they appear in {@code heat/state_shift/<trigger>/<tier>} paths, indexed by tier value. */
    private static final String[] TIER_NAMES = {
        "ambient",
        "warm_blooded",
        "warm_blooded_small",
        "warm_blooded_large",
        "cold_blooded_aquatic",
        "cold_blooded",
        "burning"
    };

    private PredatorHeatMaterials() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the packed material ID for this entity — tier in the low nibble, size step in the high nibble — or 0 when
     *         it has no thermal signature worth showing.
     */
    public static int materialIdFor(Entity entity) {
        var tier = tierFor(entity);

        if (tier == 0) {
            // Ambient. There is no band to clamp and no bonus to apply, so the size dial has nothing to act on;
            // sending a bare 0 keeps these entities on exactly the behaviour they had before the dials existed.
            return 0;
        }

        return tier | (sizeStepFor(entity) << 4);
    }

    /** The heat tier alone, before packing. State triggers are checked first — they override the entity's own tier. */
    private static int tierFor(Entity entity) {
        // Mud is this mod's thermal cloak and wins over everything else. The classification pass already pushes a
        // mud-covered mob into the background under thermal; zeroing the ID too means the cloak holds no matter which
        // branch of the shader evaluates the pixel.
        if (entity instanceof LivingEntity living && PredatorMud.isMuddy(living)) {
            return 0;
        }

        // ⚠ Aug 28 — the engaged cloak field is a heat barrier before it is anything else (his spec: "the cloak
        // blocks thermals but it gives off an em signal"). Same belt-and-braces as mud above: the classification
        // already pushes a concealed wearer to BACKGROUND under thermal; zeroing the tier too means the flip holds
        // no matter which shader branch evaluates the pixel. EM visibility is the classification's job, not this
        // file's — material tiers are thermal-only.
        if (entity instanceof LivingEntity concealedCheck && com.predator.common.gameplay.cloak.PredatorCloak.isConcealed(concealedCheck)) {
            return 0;
        }

        // Anything already alight — a flaming arrow while it burns, a mob that caught fire. Goes cold when it stops.
        if (entity.isOnFire()) {
            return stateShiftTier(entity, "on_fire");
        }

        // A creeper is vegetation and reads as nothing, right up until it lights. The swell is the only warning
        // thermal gives, and it drops back if the creeper de-swells. Any Creeper SUBCLASS qualifies, so a modded
        // creeper variant is covered without anyone listing it.
        if (entity instanceof Creeper creeper && creeper.getSwelling(0.0F) > 0.0F) {
            return stateShiftTier(entity, "swelling");
        }

        // A ghast is a gasbag until it lights one. isCharging is synced for the red-eyes model, so it is free to read.
        if (entity instanceof Ghast ghast && ghast.isCharging()) {
            return stateShiftTier(entity, "charging");
        }

        // Players are not in a tag: a datapack cannot sensibly remove the player, and they are always the reference
        // point the rest of the scale is judged against.
        if (entity instanceof Player) {
            return WARM_BLOODED_LARGE;
        }

        var type = entity.getType();

        if (type.is(PredatorEntityTypeTags.HEAT_BURNING)) {
            return BURNING;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_WARM_BLOODED_LARGE)) {
            return WARM_BLOODED_LARGE;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_WARM_BLOODED)) {
            return WARM_BLOODED;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_WARM_BLOODED_SMALL)) {
            return WARM_BLOODED_SMALL;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_COLD_BLOODED)) {
            return COLD_BLOODED;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_COLD_BLOODED_AQUATIC)) {
            return COLD_BLOODED_AQUATIC;
        }

        return 0;
    }

    /**
     * Which tier a triggered mob shifts to. {@link #BURNING} unless a {@code heat/state_shift/<trigger>/<tier>} tag
     * says otherwise — so today's behaviour is the default and the tags are pure opt-in.
     * <p>
     * ⭐ The loop only runs while a trigger is actually firing, which for the overwhelming majority of entities is
     * never. Nothing here costs anything on an ordinary frame.
     */
    private static int stateShiftTier(Entity entity, String trigger) {
        var type = entity.getType();

        for (var tier = 0; tier < TIER_NAMES.length; tier++) {
            if (type.is(PredatorEntityTypeTags.heatStateShift(trigger, TIER_NAMES[tier]))) {
                return tier;
            }
        }

        return BURNING;
    }

    /**
     * 1-15, how large this body reads. 15 is a cow or a player and behaves exactly as before; 1 is a chicken and gets a
     * much gentler core-to-extremity falloff.
     * <p>
     * ⚠ NEVER RETURNS 0 — that value is reserved to mean "nothing was pushed" so an unpatched draw keeps the old look.
     */
    private static int sizeStepFor(Entity entity) {
        var factor = sizeFactorFor(entity);

        return 1 + Math.round(factor * 14.0F);
    }

    /** 0 fully small, 1 fully large. Tag overrides first, then the live bounding box. */
    private static float sizeFactorFor(Entity entity) {
        var type = entity.getType();

        if (type.is(PredatorEntityTypeTags.HEAT_SIZE_TINY)) {
            return 0.0F;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_SIZE_SMALL)) {
            return 1.0F / 3.0F;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_SIZE_MEDIUM)) {
            return 2.0F / 3.0F;
        }

        if (type.is(PredatorEntityTypeTags.HEAT_SIZE_LARGE)) {
            return 1.0F;
        }

        var largest = Math.max(entity.getBbWidth(), entity.getBbHeight());

        return Math.max(0.0F, Math.min(1.0F, (largest - SIZE_SMALL) / (SIZE_LARGE - SIZE_SMALL)));
    }
}
