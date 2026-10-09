package com.predator.common.gameplay.entity.projectile;

import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.AzTarget;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

/**
 * Plays the bolt's one clip.
 * <h2>Why a bolt needs an animation at all</h2> ⚠ {@code bolt.fire} is not decoration — it is the model's TRANSFORM,
 * and without it the bolt draws wrong twice over. It sets {@code root} to 0.3 scale and offsets {@code gBolt} by -27 on
 * Y. At full size the bolt is a six-block plank; at the authored scale it is about 0.6 blocks long, which is what the
 * hitbox is sized for. The Y offset is what puts it on the muzzle instead of nearly two blocks above it.
 * <p>
 * A looping single-pose clip rather than a static model tweak because that is how it was authored, so re-exporting the
 * model from Blockbench keeps working without anyone having to remember a matching number in Java.
 */
public class PlasmaBoltAnimationDispatcher {

    /** Its own track name; the bolt shares no rig with anything else. */
    public static final String BOLT_CONTROLLER_NAME = "bolt";

    public static final String FIRE_ANIMATION_NAME = "bolt.fire";

    private static final AzCommand<PlasmaBoltProjectile> FIRE = AzCommand.<PlasmaBoltProjectile>idempotent()
        .play(
            AzTarget.track(BOLT_CONTROLLER_NAME),
            FIRE_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private final PlasmaBoltProjectile bolt;

    public PlasmaBoltAnimationDispatcher(PlasmaBoltProjectile bolt) {
        this.bolt = bolt;
    }

    public void fire() {
        if (!bolt.level().isClientSide) {
            return;
        }

        FIRE.dispatchForEntity(bolt);
    }
}
