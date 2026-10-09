package com.predator.client.animation.item;

import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.AzTarget;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import com.predator.common.gameplay.item.combistick.CombiStickState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plays the combi stick's own open, loop and close on the ITEM model.
 * <h2>⚠⚠ THIS IS THE HALF THE YAUTJA DOES NOT DO</h2> The yautja plays {@code attack.spear.open} on its body; this
 * plays {@code combi.open} on the stick, telescoping the segments out. Neither knows about the other — they stay in
 * step because both read the same state on the stack.
 * <p>
 * That is also why it works for a PLAYER with no yautja involved at all: his spec is that equipping it opens it,
 * holding it loops, and putting it away closes it. Nothing here is predator-specific.
 * <h2>Why a per-stack memory</h2> ⚠ A dispatch has to fire ON THE TRANSITION, not every tick the state holds. Without
 * remembering what was last played, {@code combi.open} would restart every frame and the segments would never visibly
 * extend.
 * <p>
 * ⚠ Weak keys, so a stack that is dropped, destroyed or garbage collected does not pin an entry forever.
 */
public final class CombiStickAnimationDispatcher {

    private static final Map<ItemStack, CombiStickState> LAST_PLAYED = new WeakHashMap<>();

    /**
     * Holds the stick shut.
     * <p>
     * ⚠⚠ WITHOUT THIS THE SPEAR SHOWS FULLY EXTENDED THE MOMENT IT IS EQUIPPED. COLLAPSED used to play nothing, so the
     * model sat at its BIND POSE — and the geo is authored EXTENDED, because that is the shape everything else animates
     * away from. The open clip then started from closed, which is the flash he saw: extended, blink, closed, open.
     * <p>
     * ⚠ A held pose is the only fix. There is no "play nothing" that lands on closed, because the bind pose is not
     * closed.
     */
    private static final AzCommand<ItemStack> SHUT = AzCommand.<ItemStack>idempotent()
        .play(AzTarget.track(CombiStickAnimator.TRACK), "combi.shut", AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<ItemStack> OPEN = AzCommand.<ItemStack>replay()
        .play(AzTarget.track(CombiStickAnimator.TRACK), "combi.open", AzPlayBehaviors.PLAY_ONCE)
        .build();

    /** ⚠ Idempotent: the loop must NOT restart every tick or the stick would jitter in the hand. */
    private static final AzCommand<ItemStack> LOOP = AzCommand.<ItemStack>idempotent()
        .play(AzTarget.track(CombiStickAnimator.TRACK), "combi.loop", AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<ItemStack> CLOSE = AzCommand.<ItemStack>replay()
        .play(AzTarget.track(CombiStickAnimator.TRACK), "combi.close", AzPlayBehaviors.PLAY_ONCE)
        .build();

    private CombiStickAnimationDispatcher() {
        throw new UnsupportedOperationException();
    }

    /** Called from the renderer each frame; dispatches only when the state has actually changed. */
    /**
     * ⚠ Needs the HOLDER, not just the stack: BLib keys an item animator by the entity carrying it, so the same stack
     * in two different hands animates independently. Passing null would silently animate nothing.
     */
    public static void update(Entity holder, ItemStack stack) {
        if (holder == null || !(stack.getItem() instanceof CombiStickItem)) {
            return;
        }

        var state = CombiStickItem.stateOf(stack);
        var last = LAST_PLAYED.get(stack);

        if (state == last) {
            return;
        }

        LAST_PLAYED.put(stack, state);

        switch (state) {
            case OPENING -> OPEN.dispatchForItem(holder, stack);
            case EXTENDED -> LOOP.dispatchForItem(holder, stack);
            case CLOSING -> CLOSE.dispatchForItem(holder, stack);

            // ⚠ COLLAPSED holds combi.shut. It used to play nothing, on the assumption that the bind pose WAS
            // the closed shape — it is not, the geo is authored extended, so the spear appeared fully open until
            // the first clip fired.
            case COLLAPSED -> SHUT.dispatchForItem(holder, stack);
        }
    }
}
