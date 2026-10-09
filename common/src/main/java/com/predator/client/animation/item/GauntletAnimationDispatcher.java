package com.predator.client.animation.item;

import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.AzTarget;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.predator.common.gameplay.item.GauntletItem;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plays the gauntlet's clips, picking the left or right set from the arm actually wearing it.
 * <h2>⚠⚠ THE SIDE COMES FROM {@code getMainArm()}, NOT A CONSTANT</h2> The gauntlet sits in the offhand, which is the
 * OPPOSITE of the main arm — and vanilla has a left-handed setting under Skin Customisation. A left-handed player wears
 * it on the right and needs the right-hand clips, in stock Minecraft with no other mods involved.
 * <h2>⚠ Ready loops, the rest fire once</h2> {@code ready} is idempotent so it does not restart every frame and jitter
 * the model. {@code open}, {@code close} and {@code fire} replay, because each is an event that must play again the
 * next time it happens.
 */
public final class GauntletAnimationDispatcher {

    private static final Map<ItemStack, String> LAST_PLAYED = new WeakHashMap<>();

    /**
     * ⚠⚠ WHEN A ONE-SHOT MUST BE LEFT ALONE. {@code ready} is dispatched from the animator EVERY FRAME, so without this
     * a fire, open or close clip is overwritten by the loop on the very next frame and never visibly plays. That is why
     * the fire animation never appeared.
     */
    private static final Map<ItemStack, Long> HOLD_UNTIL = new WeakHashMap<>();

    private GauntletAnimationDispatcher() {
        throw new UnsupportedOperationException();
    }

    /** The looping resting pose. ⚠ Called every frame from the animator, so it must stay idempotent. */
    public static void ready(Player player, ItemStack stack) {
        if (!GauntletItem.isEquipped(player, stack)) {
            LAST_PLAYED.remove(stack);
            HOLD_UNTIL.remove(stack);

            return;
        }

        // ⚠ Do not stomp a one-shot that is still running.
        var hold = HOLD_UNTIL.get(stack);

        if (hold != null && System.currentTimeMillis() < hold) {
            return;
        }

        play(player, stack, "ready", AzPlayBehaviors.LOOP, 0L);
    }

    /** ⚠ 250ms is the authored length of gauntlet.&lt;side&gt;.open. */
    public static void open(Player player, ItemStack stack) {
        // ⚠⚠ HOLD, NOT PLAY_ONCE. The clip is authored hold_on_last_frame — the wrist door stays open until close.
        // Played once it STOPPED at 0.25s, the next server tick's ready replayed over it (door slams shut while the
        // GUI is still up) and close then started from a pose it was not in. A held clip pauses instead of stopping,
        // so the client sync handler's "loop yields to a running one-shot" keeps ready off until close runs.
        play(player, stack, "open", AzPlayBehaviors.HOLD_ON_LAST_FRAME, Long.MAX_VALUE);
    }

    public static void close(Player player, ItemStack stack) {
        play(player, stack, "close", AzPlayBehaviors.PLAY_ONCE, 250L);
    }

    /** ⚠ 333ms, the authored length of gauntlet.&lt;side&gt;.fire. */
    public static void fire(Player player, ItemStack stack) {
        play(player, stack, "fire", AzPlayBehaviors.PLAY_ONCE, 333L);
    }

    /**
     * ⚠ The side comes from {@code getMainArm()}, not a constant. The gauntlet sits in the offhand, which is the
     * OPPOSITE of the main arm — and vanilla has a left-handed setting, so a left-handed player wears it on the right
     * and needs the right-hand clips in stock Minecraft.
     */
    private static void play(
        Player player,
        ItemStack stack,
        String clip,
        com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehavior behavior,
        long holdMillis
    ) {
        var looping = behavior == AzPlayBehaviors.LOOP;
        var side = GauntletItem.arm(player) == HumanoidArm.LEFT ? "left" : "right";
        var name = "gauntlet." + side + "." + clip;

        if (looping) {
            if (name.equals(LAST_PLAYED.get(stack))) {
                return;
            }

            LAST_PLAYED.put(stack, name);
        } else {
            // ⚠ Clearing this is what lets ready re-dispatch once the hold expires, instead of the model staying
            // frozen on the last frame of the one-shot.
            LAST_PLAYED.remove(stack);
            // ⚠ Guard the overflow: Long.MAX_VALUE means "hold until something else plays", and adding
            // it to the clock would wrap negative and expire instantly.
            HOLD_UNTIL.put(
                stack,
                holdMillis == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + holdMillis
            );
        }

        var command = looping
            ? AzCommand.<ItemStack>idempotent()
                .play(AzTarget.track(GauntletAnimator.TRACK), name, AzPlayBehaviors.LOOP)
                .build()
            : AzCommand.<ItemStack>replay()
                .play(AzTarget.track(GauntletAnimator.TRACK), name, behavior)
                .build();

        command.dispatchForItem(player, stack);
    }
}
