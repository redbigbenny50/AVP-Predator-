package com.predator.common.gameplay.entity.living.yautja;

public class YautjaAnimationRefs {

    public static final String FULL_BODY_CONTROLLER_NAME = "full_body";

    // -----------------------------------------------------------------------------------------------------------
    // Locomotion. All three live on the FULL BODY track, and all three animate gLeftArm, gRightArm, gLeftLeg and
    // gRightLeg — which is why the procedural limb swing had to come out of YautjaAnimator when these were wired up.
    // -----------------------------------------------------------------------------------------------------------

    /** 7.24s loop. Long and slow — it reads as a hunter waiting rather than a mob bobbing. */
    public static final String IDLE_ANIMATION_NAME = "idle";

    /** 1.44s loop. */
    public static final String WALK_ANIMATION_NAME = "walk";

    /** 0.72s loop — exactly twice the walk cadence. */
    public static final String RUN_ANIMATION_NAME = "run";

    /**
     * 2.88s loop.
     * <p>
     * ⚠ Authored as a SURFACE stroke: the clip puts {@code gWaist} at +45 and then cranks the head back up with -22.5
     * on both {@code gNeckLower} and {@code gHead}, which cancels out to a level gaze. That is precisely the "keeps its
     * head up and kicks" pose — it is correct at the surface and wrong underwater, which is why the model is pitched to
     * its travel direction on top of it. See {@code YautjaAnimator.applySwimPitch}.
     */
    public static final String SWIM_ANIMATION_NAME = "swim";

    /**
     * 0.72s loop — the same cadence as {@link #RUN_ANIMATION_NAME}, but a much shorter reach: the arms swing 15 degrees
     * against the quick climb's 45. Authored facing the wall, so the body only needs its normal yaw.
     */
    public static final String CLIMB_SLOW_ANIMATION_NAME = "climb.normal";

    /** 0.72s loop, three times the arm reach of the slow climb. */
    public static final String CLIMB_FAST_ANIMATION_NAME = "climb.quick";

    /**
     * Holding on without climbing. ⚠ These two clips existed in the animation file but NOTHING SELECTED THEM, so a
     * yautja clinging to a wall while it fired at something behind it kept playing the reach-and-pull cycle and looked
     * like it was climbing thin air.
     */
    public static final String CLIMB_IDLE_LEFT_ANIMATION_NAME = "climb.idleleft";

    public static final String CLIMB_IDLE_RIGHT_ANIMATION_NAME = "climb.idleright";

    /** 0.4s, holds its last frame — so it reads as a pose held through the whole arc, not a flick. */
    public static final String JUMP_ANIMATION_NAME = "jump";

    /** 0.5s, holds its last frame. */
    public static final String LAND_ANIMATION_NAME = "land";

    /** 0.96s loop, authored low to the ground. */
    public static final String CRAWL_ANIMATION_NAME = "crawl";

    /** The unmasked roar. Plays once; the mask bones are already hidden by then. */
    /**
     * ⚠⚠ THE CLIP "roar" NO LONGER EXISTS — it was split into masked and maskless variants. A stale name here does not
     * fail loudly: Az looks the clip up by string, finds nothing, and the yautja simply stands there mid-fight with no
     * animation and no error.
     * <p>
     * ⚠ The roar fires ON THE MASK BREAKING, so by the time it plays the mask is already gone — {@code checkMask}
     * clears the helmet slot before the roar is dispatched, and {@code YautjaAnimator.showHelmet} has already hidden
     * {@code gArmorMask}. MASKLESS is therefore the correct one for that trigger; the masked variant is for a roar with
     * the helmet still on, which nothing currently does.
     */
    public static final String ROAR_ANIMATION_NAME = "roar.maskless";

    /** ⚠ Unused so far — kept because a taunt or a challenge roar would want it. */
    public static final String ROAR_MASKED_ANIMATION_NAME = "roar.masked";

    /** The combi stick telescoping out on the yautja's body. Pairs with the ITEM's own {@code combi.open}. */
    public static final String SPEAR_OPEN_ANIMATION_NAME = "attack.spear.open";

    public static final String SPEAR_CLOSE_ANIMATION_NAME = "attack.spear.close";

    public static final String SPEAR_SWIPE_ANIMATION_NAME = "attack.spear.swipe";

    public static final String SPEAR_STAB_ANIMATION_NAME = "attack.spear.stab";

    public static final String SPEAR_THROW_ANIMATION_NAME = "attack.spear.throw";

    /** Gauntlet: the aim is held while lining a shot up, the fire is the release. */
    public static final String WRIST_AIM_ANIMATION_NAME = "attack.wrist.aim";

    public static final String WRIST_FIRE_ANIMATION_NAME = "attack.wrist.fire";

    /** 1s, plays once — the dodge roll. */
    public static final String DODGE_ANIMATION_NAME = "evade.roll";

    /** Raising the bow. Holds its last frame, so the yautja stays at the ready between shots. */
    public static final String BOW_READY_ANIMATION_NAME = "attack.bow.ready";

    /** Loosing an arrow. Plays once, 0.75 s. */
    public static final String BOW_SHOOT_ANIMATION_NAME = "attack.bow.shoot";

    /** Lowering the bow when the shooting is done. */
    public static final String BOW_PUTAWAY_ANIMATION_NAME = "attack.bow.putaway";

    /**
     * The attack track.
     * <p>
     * ⚠ A THIRD track, separate from both {@code full_body} and {@code caster}. The attack clips animate arm and torso
     * bones the locomotion clips also drive, so they cannot share the body track without one cancelling the other — but
     * a yautja must be able to swing while walking. A separate track with a short transition lets the swing take the
     * arms for its duration and hand them back.
     */
    public static final String ATTACK_CONTROLLER_NAME = "attack";

    /**
     * The caster's own animation track, layered over {@link #FULL_BODY_CONTROLLER_NAME}.
     * <p>
     * ⚠ A SECOND track rather than a shared one, and the art is what makes that safe: every {@code caster.*} clip
     * animates {@code gCasterMount}, {@code gCasterArm} and {@code gCaster} and nothing else. No body bone appears in
     * any of the five, so the two tracks can never write the same bone and the caster can hold an aim while the body
     * walks, idles or swings a blade.
     * <p>
     * ⚠⚠ THAT SEPARATION IS A PROPERTY OF THE ART, AND IT HAS BEEN BROKEN ONCE ALREADY. The {@code swim} clip used to
     * animate {@code gCaster} too, which put a locomotion clip and a caster clip on the same bone and left the outcome
     * resting on track registration order. He corrected it in the art rather than papering over it in code. <b>Before
     * adding any locomotion clip, check it touches no {@code gCaster*} bone.</b>
     */
    public static final String CASTER_CONTROLLER_NAME = "caster";

    /** Stowed on the back, looping. */
    public static final String CASTER_IDLE_ANIMATION_NAME = "caster.idle";

    /** Idle into ready — 0.5s, holds its last frame. */
    public static final String CASTER_AIM_ANIMATION_NAME = "caster.aim";

    /** The loop it charges and fires from. */
    public static final String CASTER_READY_ANIMATION_NAME = "caster.ready";

    /** The shot itself — 0.375s, plays once. */
    public static final String CASTER_SHOOT_ANIMATION_NAME = "caster.shoot";

    /** Ready back into idle — 0.5s, holds its last frame. */
    public static final String CASTER_DISARM_ANIMATION_NAME = "caster.disarm";
}
