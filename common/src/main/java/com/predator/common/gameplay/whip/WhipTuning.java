package com.predator.common.gameplay.whip;

/**
 * Every number the whip uses, in one class.
 * <h2>⚠ COMPARTMENTALISED ON PURPOSE</h2> [stated] "compartmentalize this ... so i can say remove it if we need to."
 * The whole weapon lives under {@code common.gameplay.whip} plus its two client renderers; pulling it out means
 * deleting those, the three registry lines (item, two entity types), the two client renderer registrations, its datagen
 * entries and its four textures. Nothing else in the mod reads anything in this package.
 * <h2>Reference</h2> Eternal Starlight's Chain of Souls uses a 64-block grapple; [stated] "i think it was pretty far in
 * starlight. we want to be able to reach a good distance away but not make the attack super long" — so the grapple is
 * half that and the lash is a separate, much shorter number.
 */
public final class WhipTuning {

    private WhipTuning() {
        throw new UnsupportedOperationException();
    }

    // ------------------------------------------------------------------ the lash (left click)

    /** How far the tip reaches at full extension, blocks. */
    public static double LASH_REACH = 6.0D;

    /**
     * How far the whip's sounds wander from their recorded pitch, either way.
     * <p>
     * [stated] "for variety if possible you can adjust the pitch slightly so it sounds a bit different with repeated
     * hits". ⚠ 0.12 is roughly two semitones of spread — enough that two cracks in a row are audibly different, small
     * enough that the whip never sounds like a different weapon.
     */
    public static float SOUND_PITCH_SPREAD = 0.12F;

    /**
     * Ticks the whole swing takes: out, snap, recoil.
     * <p>
     * ⚠⚠ THIS MUST MATCH THE ARM. Vanilla's swing is SIX ticks (LivingEntity.getCurrentSwingDuration, 6 unless hasted).
     * At 9 the arm had finished and returned to rest while the cord was still recoiling, so the two read as separate
     * motions — [stated] "it comes up faster than the whip returns or its not aligned with it".
     */
    public static int LASH_TICKS = 6;

    /**
     * Fraction of the swing spent extending; the rest is the return. The snap is at this instant.
     * <p>
     * ⚠ The arm reaches its furthest point around halfway through its swing, so the crack lands with it.
     */
    public static float LASH_SNAP_AT = 0.5F;

    /** Radius around the cord that counts as a hit, blocks. */
    public static double LASH_HIT_RADIUS = 0.7D;

    public static float LASH_DAMAGE = 6.0F;

    /** Extra damage at the tip: the crack is the fast part, so a full extension hits harder than a flick. */
    public static float LASH_TIP_BONUS = 3.0F;

    /**
     * Ticks before it can be swung again.
     * <p>
     * ⚠ [stated] "if i swing it too fast the chain doesnt fire" — that is this. A swing inside the cooldown is silently
     * dropped, so mashing looks broken rather than rate-limited. 10 ticks is half a second, which keeps the whip from
     * being spam while matching what a fast click actually expects.
     */
    public static int LASH_COOLDOWN_TICKS = 10;

    /** How much wider the tip draws at the moment of the snap — [stated] "have the tip taper up on the hit". */
    public static float TIP_FLARE = 2.6F;

    // ------------------------------------------------------------------ the grapple (right click)

    /** Blocks. Half of Chain of Souls' 64. */
    public static double GRAPPLE_RANGE = 32.0D;

    /**
     * How fast the hook flies out, blocks per tick.
     * <p>
     * ⚠ [stated] "The chain swing for the grapple also moves too slow when you swing it to attach." 2.2 crossed 32
     * blocks in nearly three quarters of a second, which reads as lobbed rather than fired. 3.6 halves that.
     */
    public static float HOOK_SPEED = 3.6F;

    /**
     * Reel speed per block of remaining distance, blocks per tick. ⚠ This is a SPEED now, not an acceleration: the reel
     * writes the velocity outright rather than adding to it, because adding compounded every tick and threw the player
     * past the anchor.
     */
    public static double REEL_STRENGTH = 0.14D;

    /** Hard cap on reel speed, blocks per tick. Roughly 16 blocks a second. */
    public static double REEL_MAX_SPEED = 0.8D;

    /** Floor, so the last metre does not crawl. */
    public static double REEL_MIN_SPEED = 0.18D;

    /** Crouching slows the reel to this fraction, so you can control the approach. */
    public static double REEL_CROUCH_SCALE = 0.45D;

    /** Within this distance of the anchor the reel stops and the ledge boost fires, blocks. ⚠ Widened at speed. */
    public static double ARRIVAL_DISTANCE = 2.0D;

    /**
     * Upward kick on arrival so you clear the lip and land ON the block.
     * <p>
     * ⚠ [stated] "we need to increase its vertical push up on ledges. because i can barely go up." 0.52 was about a
     * two-block hop, which a one-block lip ate. 0.86 clears roughly four blocks, so a grapple onto a cliff edge
     * actually puts you on top of it.
     */
    public static double LEDGE_BOOST = 0.86D;

    /** Forward nudge with the boost, so you clear the face rather than sliding back down it. */
    public static double LEDGE_FORWARD = 0.34D;

    /** How hard a hooked mob is yanked toward the player. */
    public static double YANK_STRENGTH = 1.35D;

    /** Knockback resistance at or above which a mob is too heavy to yank and pulls YOU instead. */
    public static double YANK_RESIST_THRESHOLD = 0.6D;

    public static float HOOK_DAMAGE = 3.0F;

    /**
     * Upward help added while the reel is pressing you into a wall, blocks per tick.
     * <p>
     * ⚠ [stated] "when i try to grapple from two sources about the same height i dont get lifted really i just kind get
     * stuck against." A level grapple has no vertical component at all, so the reel simply pinned the player against
     * the face. This lifts them along it until the arrival boost can put them over the lip.
     */
    public static double REEL_WALL_CLIMB = 0.32D;

    /** Ticks of fall-damage immunity after the grapple ends — [stated] "no fall damage while grappling". */
    public static int FALL_GRACE_TICKS = 30;

    public static int GRAPPLE_COOLDOWN_TICKS = 10;
}
