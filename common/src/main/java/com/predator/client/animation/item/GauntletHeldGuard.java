package com.predator.client.animation.item;

import com.blib.internal.client.animation.AzAnimatorAccessor;
import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Stops the gauntlet's clips the moment it is rendered in a hand it is not WORN in.
 * <h2>⚠⚠ NOTHING ELSE EVER STOPS THE READY LOOP</h2> The server sends {@code ready} every tick while the gauntlet is in
 * the offhand and simply goes quiet when it leaves. A loop does not end on its own, and the animator rides the stack
 * (keyed by {@code AZ_ID}), so a gauntlet moved to the main hand kept looping: skin arm drawn, {@code root} still
 * shoved 5.8 units onto an arm that is not there. That is "centred on my screen and holding the entire arm" in first
 * person and "on my crotch" in third — the display transforms were fine, the pose under them was not.
 * <p>
 * The guard lives on the client, at the two places that render a held item and know both the holder and the stack
 * ({@code ItemInHandRenderer} for the local player, {@code ItemInHandLayer} for everyone). Once stopped and cleared,
 * BLib's bone cache eases every bone back to its rest snapshot, and the arm gate (no current animation, STOP state)
 * stops drawing the skin arm — so the held gauntlet is drawn purely by its {@code thirdperson_*} /
 * {@code firstperson_righthand} display transforms, as designed.
 * <p>
 * ⚠ Cheap on the common path: an item check, then two null checks and a state read.
 */
public final class GauntletHeldGuard {

    private GauntletHeldGuard() {
        throw new UnsupportedOperationException();
    }

    public static void stopIfNotWorn(LivingEntity holder, ItemStack stack) {
        if (!stack.is(PredatorItems.GAUNTLET.get())) {
            return;
        }

        if (holder instanceof Player player && GauntletItem.isEquipped(player, stack)) {
            return;
        }

        var animator = AzAnimatorAccessor.<UUID, ItemStack>getOrNull(stack);

        if (animator == null) {
            return;
        }

        var track = animator.getAnimationTrackContainer().getOrNull(GauntletAnimator.TRACK);

        if (track == null || (track.currentAnimation() == null && track.stateMachine().isStopped())) {
            return;
        }

        // ⚠ STOP alone is not enough: the stop state keeps currentAnimation, and the arm gate treats "has an
        // animation" as "draw the arm". Clear it and the queue so the track is genuinely idle.
        track.stateMachine().stop();
        track.setCurrentAnimation(null);
        track.animationQueue().clear();
    }
}
