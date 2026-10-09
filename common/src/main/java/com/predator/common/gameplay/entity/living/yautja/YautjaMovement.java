package com.predator.common.gameplay.entity.living.yautja;

import com.blib.api.common.entity.v1.PlayerStatConstants;

/**
 * One home for how fast a yautja moves and how fast its legs are drawn moving.
 * <h2>His spec, Aug 29</h2> The walk is "about 15% slower than players walk. Its a casual methodical strut." The run is
 * "a pursuit burst speed that catches up to most prey quickly even full sprint speed players."
 * <h2>⚠⚠ The clips do not natively move at those speeds, and that is not a bug to hunt</h2> Both locomotion clips are
 * Molang-driven, so the declared {@code animation_length} is decorative — the real cadence is in the expression.
 * {@code walk} swings the thigh ±30° on {@code cos(life_time*250)}, a 1.44s cycle; {@code run} swings ±60° on
 * {@code cos(life_time*500)}, a 0.72s cycle. With the hip pivot 15 units off the ground, the distance a planted foot
 * actually covers works out at roughly <b>1.26 blocks/s for the walk and 4.35 for the run</b>.
 * <p>
 * So the walk clip is built for about a THIRD of the speed asked for here, and the run clip for slightly less than a
 * player's walking pace. The rig is the reason: the legs are short for the model's height, which caps how much ground
 * one stride can cover.
 * <h2>How that is reconciled</h2> The gameplay speeds below are authoritative — they are what a player feels. The clips
 * are played back at {@link #WALK_ANIMATION_SPEED} and {@link #RUN_ANIMATION_SPEED}, both chosen by eye rather than
 * computed. ⚠ A speed and its playback rate are a PAIR: change one without the other and the feet skate.
 */
public final class YautjaMovement {

    /** A player's walking pace, in blocks per second. The reference everything here is quoted against. */
    public static final float PLAYER_WALK_BLOCKS_PER_SECOND = 4.317F;

    /** Vanilla sprinting is 1.3× walking. ⚠ NOT BLib's {@code BASE_SPRINT_SPEED}, which models it as only 1.15×. */
    public static final float PLAYER_SPRINT_BLOCKS_PER_SECOND = PLAYER_WALK_BLOCKS_PER_SECOND * 1.3F;

    /**
     * His figure: 15% below a player's walk.
     * <p>
     * ⚠ The old value was {@code BASE_WALK_SPEED * 1.2}, i.e. 20% FASTER than a player — so the yautja has been
     * strolling at 1.41× the intended pace, which is what he suspected.
     */
    /**
     * 1.0 — the yautja walks at exactly a player's walking pace, on his instruction, to see how it reads with the
     * animation left where it is.
     * <p>
     * ⚠⚠ THIS HAS NOW BEEN 0.85, THEN 1.2, NOW 1.0. It is being tuned by eye against the animation, so do not "correct"
     * it back to any of them from a written spec. The original rule was "about 15% slower than a player, a casual
     * methodical strut" (0.85); the current value is deliberately different.
     * <p>
     * ⚠ {@link #WALK_ANIMATION_SPEED} was DELIBERATELY NOT changed alongside it this time — he wants to see the same
     * playback against a different ground speed. Normally these two move together, and everywhere else in this file
     * says so; this is the exception, and it is the whole point of the change.
     */
    public static final float WALK_FRACTION_OF_PLAYER_WALK = 1.0F;

    /** The movement-speed attribute. Everything else is a modifier on top of this. */
    public static final float WALK_SPEED = PlayerStatConstants.BASE_WALK_SPEED * WALK_FRACTION_OF_PLAYER_WALK;

    /**
     * The pursuit burst, as a multiplier on the walk.
     * <p>
     * 1.9 puts the run at about 6.97 blocks/s, which is <b>1.24× a sprinting player</b> — it closes on a fleeing
     * sprinter at roughly 1.36 blocks/s, so a ten-block head start is gone in about seven seconds. ⚠ A player who
     * sprint-JUMPS is faster still (~7.1 blocks/s) and will hold a gap; raising this to 2.1 covers that too, at the
     * cost of a run that outpaces its own animation further.
     */
    /**
     * ⚠ 1.4, chosen against the real figure rather than by feel. A player sprints at 5.61 blocks/second; this gives
     * 6.04, so the yautja closes 0.43 b/s — about 13 blocks over a thirty-second sustained sprint. His rule: "just
     * slightly higher than the players so a sustained run the predator gets him."
     * <p>
     * ⚠⚠ It was 1.9, which is 1.46x a sprinting player and closes 2.6 b/s — a chase nobody outruns for more than a few
     * seconds.
     * <p>
     * ⚠ A sprint-JUMPING player manages roughly 7.1 b/s and still escapes at this setting. That is deliberate: it costs
     * hunger and cannot be sustained, which is what makes it an escape rather than a stalemate.
     */
    public static final double CHASE_SPEED_MODIFIER = 1.4;

    /**
     * Walk playback rate — his figure, found by playing the clip in Blockbench: "speed up the animation by 50%".
     * <p>
     * ⚠⚠ A FIXED RATE, and going back to one is deliberate. The live measured version replaced it because a hard-coded
     * 2.92 made the feet skate — but 2.92 was wrong because it was computed from an ASSUMED ground speed, not because
     * fixed rates are wrong. This is not assumed: it is what he watched and chose. An eye on the real thing beats
     * arithmetic on a guessed constant.
     * <p>
     * ⚠ 1.5 was the first value that ACTUALLY TOOK EFFECT — everything before it was inert, because the clips ran on
     * {@code query.life_time}, which ignores playback speed. 1.8 is the first real adjustment on top of a working
     * baseline.
     */
    public static final double WALK_ANIMATION_SPEED = 1.8;

    /**
     * Run playback rate, unchanged.
     * <p>
     * ⚠ Left alone because the tester reported the run as one of the animations that already looked right. It has NOT
     * been re-derived for the faster walk speed above, so if the run now skates this is the number to move — feet too
     * fast means lower it.
     */
    /**
     * ⚠ Moved WITH the chase speed, because the two are a pair — a ground speed and its playback rate always are, and
     * leaving this at 1.6 while the chase dropped from 1.9 to 1.4 would have made the run skate.
     * <p>
     * Derived from the walk he signed off rather than guessed: the walk runs 1.0x player-walk at 1.8 playback, the run
     * clip cycles twice as fast natively (0.72s against 1.44s), and the chase covers 1.4x the walk's ground — so 1.8 x
     * 1.4 / 2 = 1.26.
     */
    public static final double RUN_ANIMATION_SPEED = 1.26;

    /**
     * Playback for every body clip that is not distance-driven.
     * <p>
     * ⚠⚠ THIS EXISTS BECAUSE SPEED IS A TRACK PROPERTY, NOT A CLIP PROPERTY. Whatever the last command set stays set
     * until something changes it, so a slowed roar would have left the idle, swim, crawl and climb clips running at
     * half speed for the rest of the yautja's life. Every body command now states its rate explicitly, so none of them
     * can inherit another's.
     */
    public static final double NORMAL_ANIMATION_SPEED = 1.0;

    /** ⚠ Half speed, his call — the roar read as too quick at its authored rate. */
    public static final double ROAR_ANIMATION_SPEED = 0.5;

    /**
     * ⚠ 0.8, his figure — the spear deploy and stow read as too quick at their authored rate.
     * <p>
     * ⚠⚠ THE ATTACK TRACK CARRIES THIS, AND EVERY OTHER ATTACK CLIP MUST NOW STATE ITS OWN. Playback is a TRACK
     * property, not a clip property — whatever was set last stays set — so a spear deploy at 0.8 would otherwise leave
     * the NEXT swipe, stab, throw or wrist shot running at 80% too. Same trap the roar hit on the body track.
     */
    public static final double SPEAR_DEPLOY_ANIMATION_SPEED = 0.8;

    /**
     * Converts a mob's movement-speed attribute into blocks travelled per tick.
     * <p>
     * ⚠ A CALIBRATION CONSTANT, not a law: it comes from BLib's own scale, where {@code BASE_WALK_SPEED} of 0.315 is
     * defined as the attribute matching a player's walk. It is only ever used for the run/walk threshold below, which
     * sits midway between two speeds that differ by 1.9× — so it tolerates being 40% wrong before the animation picks
     * the wrong clip.
     */
    private static final double ATTRIBUTE_TO_BLOCKS_PER_TICK = 0.685;

    /** Midway between walking and chasing. Above this the body is drawn running. */
    private static final double RUN_THRESHOLD_RATIO = (1.0 + CHASE_SPEED_MODIFIER) / 2.0;

    /** Below this the yautja counts as stationary. Small enough that a genuine amble still reads as walking. */
    private static final double MOVING_THRESHOLD_BLOCKS_PER_TICK = 0.004;

    private YautjaMovement() {
        throw new UnsupportedOperationException();
    }

    /** {@return how far this yautja actually travels per tick at a walk, in BLOCKS} */
    public static double walkBlocksPerTick(double movementSpeedAttribute) {
        return movementSpeedAttribute * ATTRIBUTE_TO_BLOCKS_PER_TICK;
    }

    /**
     * {@return how far this yautja actually travels per tick while chasing, in BLOCKS}
     * <p>
     * ⚠ The movement-speed attribute is NOT blocks per tick — BLib scales it by 3.15 — so anything doing distance maths
     * with the raw attribute is out by a third. That bug was live in the chase intercept.
     */
    public static double chaseBlocksPerTick(double movementSpeedAttribute) {
        return movementSpeedAttribute * ATTRIBUTE_TO_BLOCKS_PER_TICK * CHASE_SPEED_MODIFIER;
    }

    /** {@return blocks per tick this yautja has to be exceeding for the run clip to be the right one} */
    public static double runThresholdBlocksPerTick(double movementSpeedAttribute) {
        return movementSpeedAttribute * ATTRIBUTE_TO_BLOCKS_PER_TICK * RUN_THRESHOLD_RATIO;
    }

    public static double movingThresholdBlocksPerTick() {
        return MOVING_THRESHOLD_BLOCKS_PER_TICK;
    }
}
