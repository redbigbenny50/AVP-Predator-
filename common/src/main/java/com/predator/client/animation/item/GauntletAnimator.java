package com.predator.client.animation.item;

import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzItemAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.blib.api.client.model.v1.AzBone;
import com.predator.PredatorResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Drives the gauntlet's clips.
 * <p>
 * <strong>⚠⚠ AzItemAnimator, NOT AzAnimator.</strong> This extended the raw {@code AzAnimator} before. The item
 * subclass adds the Molang query setup an item needs, and it is what avp_human's guns extend — the item animators in
 * this ecosystem that demonstrably work in game.
 * <p>
 * <strong>⚠⚠ THE ARM DEPENDS ON A TRACK ACTUALLY PLAYING.</strong> Read from {@code AzItemModelRenderer}: an arm bone
 * is replaced by the real player arm only when {@code isArmBone(bone) && isAnimationPlaying}, and NOTHING hides it
 * otherwise — with no track playing, the {@code leftArm} and {@code rightArm} cubes render as ordinary geo wearing the
 * gauntlet's own texture. That is exactly the giant coloured slabs: not a scale bug, an animation that was never
 * running.
 * <p>
 * So {@code ready} is (re)dispatched from {@link #setCustomAnimations}, which the animator itself calls every frame.
 * That is where avp_human's {@code GunItemAnimator} drives its barrel spin, and it is the reliable hook — driving it
 * from {@code renderByItem} meant reaching into the render pipeline for the holder mid-draw.
 */
public class GauntletAnimator extends AzItemAnimator {

    public static final String NAME = "gauntlet";

    public static final String TRACK = "gauntlet";

    private static final ResourceLocation ANIMATION = PredatorResources.itemAnimationLocation(NAME);

    public GauntletAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(@NotNull AzAnimationTrackContainer<ItemStack> container) {
        container.add(
            AzAnimationTrack.builder(this, TRACK)
                // ⚠ No blend. open and close are 0.25s and fire is 0.33s; a transition would eat most of the motion.
                .setTransitionLength(0)
                .build()
        );
    }

    /**
     * ⚠ Called every frame by the animator, BEFORE the model is drawn. Both jobs live here for that reason: the looping
     * clip, and the bone scaling that decides which arm exists.
     */
    /**
     * ⚠⚠ NO ANIMATION IS STARTED HERE ANY MORE, AND THAT IS THE POINT.
     * <p>
     * Every client-side attempt failed for one measured reason: BLib hands out a NEW ANIMATOR INSTANCE EVERY FRAME for
     * a held item, because {@code AzProvider.provideAnimator} caches on the ItemStack and the renderer receives a
     * different stack object each frame. The diagnostic showed a different animator id on every line with
     * {@code playing=false} throughout.
     * <p>
     * The loop is now started SERVER-SIDE from {@code GauntletItem.inventoryTick} and sent over the wire, which is the
     * pattern AzureLib's own gun-with-arm example uses. The networking for it is added in
     * {@code com.blib.mod.common.animation_sync}.
     * <p>
     * ⚠ Also gone: the per-frame diagnostic and the bone scaling. {@code AzItemRendererPipeline.preRender} already
     * hides both arm bones every frame, so the scaling was machinery for a problem BLib had solved.
     */

    @Override
    public @NotNull ResourceLocation getAnimationLocation(@NotNull ItemStack stack) {
        return ANIMATION;
    }

    /**
     * Hides the arm that is NOT wearing the gauntlet.
     * <p>
     * ⚠⚠ BLib draws a skin arm for EVERY {@code leftArm}/{@code rightArm} bone in the rig while anything plays, so both
     * arms showed. AzureLib's own convention for "no arm here" is a zero scale on the bone (its gunwitharm clips
     * keyframe it), and {@code AzItemArmRenderUtil} now skips a zero-scaled arm outright. Done in code rather than in
     * every clip so it cannot be forgotten in the redo, and so it follows the clip actually playing — a left-handed
     * player plays the {@code right.*} clips and hides the LEFT arm.
     * <p>
     * ⚠ {@code setCustomAnimations} runs at the END of {@code animate()}, after the bone cache has applied the
     * keyframes for this frame, so nothing overwrites the scale before the renderer reads it.
     */
    @Override
    public void setCustomAnimations(ItemStack stack, float partialTicks) {
        var model = context().boneCache().getBakedModel();

        if (model == null) {
            return;
        }

        var side = currentSide();

        if (side == null) {
            return;
        }

        hide(model.getBoneOrNull(side.equals("left") ? "rightArm" : "leftArm"));
    }

    /** {@return "left" or "right" from the clip on the track, or null if nothing has played} */
    private String currentSide() {
        var track = getAnimationTrackContainer().getOrNull(TRACK);

        if (track == null || track.currentAnimation() == null) {
            return null;
        }

        var name = track.currentAnimation().animation().name();

        if (name.contains(".left.")) {
            return "left";
        }

        return name.contains(".right.") ? "right" : null;
    }

    private static void hide(AzBone bone) {
        if (bone != null) {
            bone.setScaleX(0.0F);
            bone.setScaleY(0.0F);
            bone.setScaleZ(0.0F);
        }
    }
}
