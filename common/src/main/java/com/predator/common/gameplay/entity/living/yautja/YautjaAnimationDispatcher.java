package com.predator.common.gameplay.entity.living.yautja;

import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.AzTarget;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

/**
 * Plays the yautja's animation clips.
 * <p>
 * ⚠ Every method here is a no-op on the server. Az commands are dispatched client-side, which is why the caster's
 * position is synched data on {@link Yautja} — the server decides, the client watches the synced value change and plays
 * the clip.
 * <h2>Playback rate is part of the movement spec, not a flourish</h2> ⚠ {@code walk} and {@code run} carry a
 * {@code setSpeed}. Both clips are authored for far less ground speed than the yautja actually travels at — see
 * {@link YautjaMovement} for the measured figures — so without these the feet skate. The multipliers are derived from
 * the movement speeds and must be changed with them.
 * <h2>Two tracks</h2> Locomotion runs on {@code full_body}; the caster runs on its own {@code caster} track. They never
 * write the same bone, so a walking yautja can hold a caster aim and a firing yautja can keep running.
 * <h2>idempotent vs replay</h2> Looping and held poses use {@code idempotent}: re-issuing them while they are already
 * playing does nothing, which is what a loop wants. {@code caster.shoot} uses {@code replay}, because two shots in a
 * row must be two visible recoils rather than one continuous animation.
 */
public class YautjaAnimationDispatcher {

    private static final AzCommand<Yautja> IDLE = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.IDLE_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> WALK = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.WALK_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.WALK_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> RUN = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.RUN_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.RUN_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> SWIM = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.SWIM_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    /**
     * ⚠ {@code replay}, not idempotent — a roar is an event. It also HOLDS its last frame rather than looping, so the
     * body sits in the finished pose until the locomotion state takes it back.
     */
    /** ⚠ replay, not idempotent — a dodge is an event, and two rolls in a row must both play. */
    private static final AzCommand<Yautja> DODGE = AzCommand.<Yautja>replay()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.DODGE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    /** ⚠ idempotent: raising the bow is a STATE, and re-sending it must not restart the raise every tick. */
    private static final AzCommand<Yautja> BOW_READY = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaAnimationRefs.BOW_READY_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    /** ⚠ replay: every shot is its own event, and two in a row must both play. */
    private static final AzCommand<Yautja> BOW_SHOOT = AzCommand.<Yautja>replay()
        .play(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaAnimationRefs.BOW_SHOOT_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    private static final AzCommand<Yautja> BOW_PUTAWAY = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaAnimationRefs.BOW_PUTAWAY_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    private static final AzCommand<Yautja> ROAR = AzCommand.<Yautja>replay()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.ROAR_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.ROAR_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> CRAWL = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> CLIMB_SLOW = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.CLIMB_SLOW_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> CLIMB_FAST = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.CLIMB_FAST_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> CLIMB_IDLE_LEFT = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.CLIMB_IDLE_LEFT_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Yautja> CLIMB_IDLE_RIGHT = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.CLIMB_IDLE_RIGHT_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Yautja> JUMP = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.JUMP_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> LAND = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaAnimationRefs.LAND_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.FULL_BODY_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    /**
     * ⚠ {@code replay}, not idempotent. Two slashes in a row are two swings; an idempotent command would see the same
     * clip already playing and do nothing, so the second blow would land with no animation at all.
     */
    /**
     * Plays any one-shot attack clip on the attack track.
     * <p>
     * ⚠⚠ IT STATES ITS SPEED EXPLICITLY, AND THAT IS NOT DECORATION. Playback is a TRACK property, not a clip property
     * — whatever was last set stays set. The spear deploy runs at 0.8, and every swipe, stab, throw and wrist shot
     * afterwards goes through HERE, so without naming 1.0 they would all inherit 80% for the rest of that yautja's
     * life. Exactly the bug the roar caused on the body track.
     */
    public static void playAttack(Yautja yautja, String clip) {
        playAttack(yautja, clip, YautjaMovement.NORMAL_ANIMATION_SPEED);
    }

    /** As above, at a stated speed — used for the clips that are deliberately slowed so they can be read. */
    public static void playAttack(Yautja yautja, String clip, double speed) {
        if (!yautja.level().isClientSide) {
            return;
        }

        AzCommand.<Yautja>replay()
            .play(
                AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
                clip,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(
                AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
                speed
            )
            .build()
            .dispatchForEntity(yautja);
    }

    private static final AzCommand<Yautja> CASTER_IDLE = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.CASTER_CONTROLLER_NAME),
            YautjaAnimationRefs.CASTER_IDLE_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Yautja> CASTER_AIM = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.CASTER_CONTROLLER_NAME),
            YautjaAnimationRefs.CASTER_AIM_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    private static final AzCommand<Yautja> CASTER_READY = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.CASTER_CONTROLLER_NAME),
            YautjaAnimationRefs.CASTER_READY_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Yautja> CASTER_SHOOT = AzCommand.<Yautja>replay()
        .play(
            AzTarget.track(YautjaAnimationRefs.CASTER_CONTROLLER_NAME),
            YautjaAnimationRefs.CASTER_SHOOT_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    private static final AzCommand<Yautja> CASTER_DISARM = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.CASTER_CONTROLLER_NAME),
            YautjaAnimationRefs.CASTER_DISARM_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    private final Yautja yautja;

    public YautjaAnimationDispatcher(Yautja yautja) {
        this.yautja = yautja;
    }

    public void idle() {
        dispatch(IDLE);
    }

    public void walk() {
        dispatch(WALK);
    }

    public void run() {
        dispatch(RUN);
    }

    public void swim() {
        dispatch(SWIM);
    }

    public void dodge() {
        dispatch(DODGE);
    }

    /**
     * ⚠ The spear deploy and stow ride the ATTACK track, not the body track. The yautja keeps walking, climbing or
     * standing while it telescopes the weapon out — same reason the caster has its own track.
     */
    private static final AzCommand<Yautja> SPEAR_OPEN = AzCommand.<Yautja>replay()
        .play(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaAnimationRefs.SPEAR_OPEN_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaMovement.SPEAR_DEPLOY_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> SPEAR_CLOSE = AzCommand.<Yautja>replay()
        .play(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaAnimationRefs.SPEAR_CLOSE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaMovement.SPEAR_DEPLOY_ANIMATION_SPEED
        )
        .build();

    /** ⚠ Gauntlet aim HOLDS its last frame — it is a pose held while lining up, not a gesture. */
    private static final AzCommand<Yautja> WRIST_AIM = AzCommand.<Yautja>idempotent()
        .play(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaAnimationRefs.WRIST_AIM_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    private static final AzCommand<Yautja> WRIST_FIRE = AzCommand.<Yautja>replay()
        .play(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaAnimationRefs.WRIST_FIRE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .setSpeed(
            AzTarget.track(YautjaAnimationRefs.ATTACK_CONTROLLER_NAME),
            YautjaMovement.NORMAL_ANIMATION_SPEED
        )
        .build();

    public void spearOpen() {
        dispatch(SPEAR_OPEN);
    }

    public void spearClose() {
        dispatch(SPEAR_CLOSE);
    }

    public void wristAim() {
        dispatch(WRIST_AIM);
    }

    public void wristFire() {
        dispatch(WRIST_FIRE);
    }

    public void bowReady() {
        dispatch(BOW_READY);
    }

    public void bowShoot() {
        dispatch(BOW_SHOOT);
    }

    public void bowPutaway() {
        dispatch(BOW_PUTAWAY);
    }

    public void roar() {
        dispatch(ROAR);
    }

    public void crawl() {
        dispatch(CRAWL);
    }

    public void climbSlow() {
        dispatch(CLIMB_SLOW);
    }

    public void climbIdleLeft() {
        dispatch(CLIMB_IDLE_LEFT);
    }

    public void climbIdleRight() {
        dispatch(CLIMB_IDLE_RIGHT);
    }

    public void climbFast() {
        dispatch(CLIMB_FAST);
    }

    public void jump() {
        dispatch(JUMP);
    }

    public void land() {
        dispatch(LAND);
    }

    public void casterIdle() {
        dispatch(CASTER_IDLE);
    }

    public void casterAim() {
        dispatch(CASTER_AIM);
    }

    public void casterReady() {
        dispatch(CASTER_READY);
    }

    public void casterShoot() {
        dispatch(CASTER_SHOOT);
    }

    public void casterDisarm() {
        dispatch(CASTER_DISARM);
    }

    private void dispatch(AzCommand<Yautja> command) {
        if (!yautja.level().isClientSide) {
            return;
        }

        command.dispatchForEntity(yautja);
    }
}
