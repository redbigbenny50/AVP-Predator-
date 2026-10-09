package com.predator.client.animation.item;

import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.AzTarget;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.predator.common.gameplay.item.HandCasterItem;
import com.predator.common.registry.init.PredatorDataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plays "handcaster.fire" once per shot, "handcaster.idle" otherwise.
 * <p>
 * ⚠ Keyed on the FIRE TIME the item carries, not on the ItemStack object: when the server changes a component the
 * client receives a NEW object, so "has this object fired" would never trigger. Any object whose fire time is recent,
 * and not yet played, plays the clip.
 */
public final class HandCasterAnimationDispatcher {

    /** The fire clip is 0.5 s. */
    private static final long FIRE_CLIP_TICKS = 10L;

    private static final Map<ItemStack, Long> LAST_PLAYED = new WeakHashMap<>();

    private static final AzCommand<ItemStack> IDLE = AzCommand.<ItemStack>idempotent()
        .play(AzTarget.track(HandCasterAnimator.TRACK), "handcaster.idle", AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<ItemStack> FIRE = AzCommand.<ItemStack>replay()
        .play(AzTarget.track(HandCasterAnimator.TRACK), "handcaster.fire", AzPlayBehaviors.PLAY_ONCE)
        .build();

    private HandCasterAnimationDispatcher() {
        throw new UnsupportedOperationException();
    }

    public static void update(Entity holder, ItemStack stack) {
        if (holder == null || !(stack.getItem() instanceof HandCasterItem)) {
            return;
        }

        var firedAt = stack.getOrDefault(PredatorDataComponents.HAND_CASTER_FIRED_AT.get(), -1L);
        var sinceFire = holder.level().getGameTime() - firedAt;

        if (firedAt >= 0L && sinceFire < FIRE_CLIP_TICKS) {
            var played = LAST_PLAYED.get(stack);

            if (played == null || played != firedAt) {
                LAST_PLAYED.put(stack, firedAt);
                FIRE.dispatchForItem(holder, stack);
            }

            return;
        }

        IDLE.dispatchForItem(holder, stack);
    }
}
