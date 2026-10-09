package com.predator.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oct 7 - A GIGERESQUE FACEHUGGER ON A YAUTJA SAT ABOVE ITS HEAD.
 * <p>
 * Since the Gigeresque host tags (avp_combined__12) its facehuggers can latch onto a yautja. Gigeresque's renderer only
 * moves a latched hugger onto the face for host types listed in its own head-data table
 * ({@code EntityHeadData.ENTITY_HEAD_DATA_BY_TYPE} - read from the 0.8.16 jar); for any other host it simply returns,
 * leaving the hugger where riding puts it - the top of the 2.48-block hitbox, a good third of a block above the
 * yautja's head.
 * <p>
 * For a yautja host this does the placement itself, with the same numbers avp_alien's own facehugger uses on the yautja
 * (avp_predator's PredatorEntityHeadData / PredatorParasiteAttachmentOffsetData) and the same math as Gigeresque's own
 * face placement, so it follows head turns and pitch exactly as it does on its native hosts. The numbers are copied
 * here, not referenced, so this works with Gigeresque installed and avp_alien absent.
 * <p>
 * {@code @Pseudo} and {@code require = 0}: without Gigeresque, or with a version that renamed the method, nothing
 * happens. Both the typed method and its bridge are matched by name; whichever runs first places the hugger and
 * cancels, so it is applied once.
 */
@Pseudo
@Mixin(targets = "mods.cybercat.gigeresque.client.entity.model.FacehuggerModelRenderer", remap = false)
public abstract class MixinGigFacehuggerModelRenderer_YautjaFace {

    /** The yautja head, in blocks: model gHead pivot (pixels / 16) - matches yautja_jungle.geo.json. */
    private static final double PIVOT_X = -0.3347 / 16.0;

    private static final double PIVOT_Y = 32.0 / 16.0;

    private static final double PIVOT_Z = 1.8756 / 16.0;

    /** Head size used by the offsets (pixels / 16). */
    private static final double SIZE_Y = 4.0 / 16.0;

    private static final double SIZE_Z = 3.0 / 16.0;

    /** Down from the top of the hitbox onto the face - the yautja's own facehugger offset. */
    private static final double VERTICAL_OFFSET = -SIZE_Y * 3.0;

    /** avp_alien's standard facehugger height; the offsets were authored against it. */
    private static final double REFERENCE_HUGGER_HEIGHT = 0.25;

    /** Out onto the front of the face. */
    private static final double FACE_OFFSET = SIZE_Z - SIZE_Z / 24.0;

    @Inject(method = "applyRotations", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void avp_predator$placeOnYautjaFace(
        @Coerce Entity facehugger,
        PoseStack poseStack,
        float ageInTicks,
        float rotationYaw,
        float partialTick,
        float nativeScale,
        CallbackInfo ci
    ) {
        if (!facehugger.isPassenger() || !(facehugger.getVehicle() instanceof Yautja host)) {
            return;
        }

        var bodyYaw = Mth.rotLerp(partialTick, host.yBodyRotO, host.yBodyRot);
        var headYaw = Mth.rotLerp(partialTick, host.yHeadRotO, host.yHeadRot) - bodyYaw;
        var headPitch = Mth.rotLerp(partialTick, host.getXRot(), host.xRotO);

        poseStack.mulPose(Axis.YN.rotationDegrees(bodyYaw));
        poseStack.translate(PIVOT_X, PIVOT_Y - host.getBbHeight(), -PIVOT_Z);
        poseStack.mulPose(Axis.YN.rotationDegrees(headYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));
        poseStack.translate(-PIVOT_X, -PIVOT_Y + host.getBbHeight(), PIVOT_Z);
        // A Gigeresque hugger is a little taller than avp_alien's (0.3 vs 0.25): out by the difference, exactly as
        // avp_alien's own renderer handles larger huggers.
        poseStack.translate(0.0, VERTICAL_OFFSET, FACE_OFFSET + facehugger.getBbHeight() - REFERENCE_HUGGER_HEIGHT);

        ci.cancel();
    }
}
