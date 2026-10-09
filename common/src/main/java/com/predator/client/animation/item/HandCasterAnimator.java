package com.predator.client.animation.item;

import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzItemAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.predator.PredatorResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** The hand caster's two clips — [stated] "idle which is its normal mode and fire when it fires the shot." */
public class HandCasterAnimator extends AzItemAnimator {

    public static final String NAME = "hand_caster";

    public static final String TRACK = "handcaster";

    private static final ResourceLocation ANIMATION = PredatorResources.itemAnimationLocation(NAME);

    public HandCasterAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(@NotNull AzAnimationTrackContainer<ItemStack> container) {
        container.add(AzAnimationTrack.builder(this, TRACK).setTransitionLength(0).build());
    }

    @Override
    public void setCustomAnimations(ItemStack stack, float partialTicks) {
        super.setCustomAnimations(stack, partialTicks);
        HandCasterAnimationDispatcher.update(net.minecraft.client.Minecraft.getInstance().player, stack);
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(@NotNull ItemStack stack) {
        return ANIMATION;
    }
}
