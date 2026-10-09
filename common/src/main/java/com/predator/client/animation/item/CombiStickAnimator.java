package com.predator.client.animation.item;

import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzItemAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.predator.PredatorResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Drives the combi stick's telescoping open, loop and close.
 * <h2>The clips</h2> {@code combi.open} 0.25s holding its last frame, {@code combi.loop} looping, {@code combi.close}
 * 0.17s holding. They animate eleven bones — the segments slide out of one another and the blade pairs scale up from
 * nothing, which is why open and close are not simply each other reversed.
 * <p>
 * ⚠ ONE TRACK ONLY. The whole item is a single mechanism; there is nothing to layer, unlike the yautja where the body
 * and the caster move independently.
 */
/*
 * ⚠⚠ MUST EXTEND AzItemAnimator, NOT AzAnimator. BLib's ItemStack.setAnimator casts to AzItemAnimator, so a raw
 * AzAnimator throws ClassCastException the moment the item is drawn — which crashed the client the first time a combi
 * stick appeared in the hotbar. AzItemAnimator extends AzAnimator<UUID, ItemStack> and adds the Molang setup an item
 * needs; there is no reason to use the raw class for an item.
 */
public class CombiStickAnimator extends AzItemAnimator {

    public static final String NAME = "combi_stick";

    public static final String TRACK = "combi";

    private static final ResourceLocation ANIMATION = PredatorResources.itemAnimationLocation(NAME);

    public CombiStickAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(@NotNull AzAnimationTrackContainer<ItemStack> container) {
        container.add(
            AzAnimationTrack.builder(this, TRACK)
                // ⚠ No transition. The open and close clips are 5 and 4 ticks of telescoping motion; a blend into
                // them would eat most of that and the segments would appear to fade rather than slide.
                .setTransitionLength(0)
                .build()
        );
    }

    /**
     * ⚠⚠ NOTHING WAS DRIVING THE OPEN CLIP. CombiStickAnimationDispatcher.update existed and was never called from
     * anywhere — the dispatch had been removed from renderByItem and never re-homed, so equipping the spear silently
     * played no animation at all.
     * <p>
     * ⚠ BLib calls this every frame, and update() is a no-op unless the STATE changed, so it costs a map lookup and
     * fires exactly once per transition.
     * <p>
     * ⚠ This only works because AzIdentityRegistry.register now includes COMBI_STICK. Without an AZ_ID the animator is
     * rebuilt every frame and no clip can establish itself — the same root cause as the gauntlet arm.
     */
    @Override
    public void setCustomAnimations(ItemStack stack, float partialTicks) {
        super.setCustomAnimations(stack, partialTicks);

        CombiStickAnimationDispatcher.update(net.minecraft.client.Minecraft.getInstance().player, stack);
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(@NotNull ItemStack stack) {
        return ANIMATION;
    }
}
